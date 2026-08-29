package com.yhchat.canary.ncm

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

/**
 * 网易云音乐听歌识曲匹配器
 * 负责音频提取、重采样 8000Hz PCM、指纹提取以及网易云识别结果决策
 */
object NcmAudioMatcher {

    private const val TAG = "NcmAudioMatcher"
    private const val TARGET_SAMPLE_RATE = 8000
    private const val SAMPLE_DURATION_SEC = 4

    /**
     * 对本地音频文件进行听歌识别
     * @param audioFile 音频文件
     * @param candidateTitle 音频原先的标题/名称提示
     * @return 识别出的歌曲信息（包含名称、歌手、封面等），若无匹配则返回 null
     */
    suspend fun matchAudio(
        context: Context,
        audioFile: File,
        candidateTitle: String? = null
    ): NcmSong? = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() <= 0) {
            return@withContext null
        }

        try {
            // 1. 尝试从音频文件中提取元数据（如 ID3 title / artist）
            val metadataSong = extractMetadataAndSearch(audioFile, candidateTitle)
            if (metadataSong != null) {
                Log.d(TAG, "从音频文件元数据成功识别网易云歌曲: ${metadataSong.name}")
                return@withContext metadataSong
            }

            // 2. 尝试从文件名/标题进行智能网易云搜索匹配
            val cleanTitle = cleanSearchKeyword(candidateTitle ?: audioFile.nameWithoutExtension)
            if (cleanTitle.isNotBlank() && !isGenericAudioName(cleanTitle)) {
                val searchRes = NcmApiClient.searchSongs(cleanTitle, limit = 5).getOrNull()
                if (!searchRes.isNullOrEmpty()) {
                    // 大于1个结果直接使用第一个，只有1个也使用该结果
                    val matchedSong = searchRes.first()
                    Log.d(TAG, "从标题搜索识别网易云歌曲: ${matchedSong.name} - ${matchedSong.artist}")
                    return@withContext matchedSong
                }
            }

            // 3. 提取 PCM 采样并生成音频指纹调用网易云识曲
            val pcmFloats = decodeAudioTo8000HzPcm(audioFile, SAMPLE_DURATION_SEC)
            if (pcmFloats != null && pcmFloats.isNotEmpty()) {
                val fp = generateFingerprint(context, pcmFloats)
                if (!fp.isNullOrBlank()) {
                    val matchResult = NcmApiClient.matchAudioFingerprint(SAMPLE_DURATION_SEC, fp).getOrNull()
                    if (matchResult != null && matchResult.songs.isNotEmpty()) {
                        val finalSong = matchResult.songs.first()
                        Log.d(TAG, "听歌识曲指纹匹配成功: ${finalSong.name} - ${finalSong.artist}")
                        return@withContext finalSong
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "听歌识别过程发生异常: ${audioFile.name}", e)
        }

        // 结果没有就直接默认（返回 null）
        null
    }

    /**
     * 检查音频内部的 ID3 / 媒体元数据，并查询网易云
     */
    private suspend fun extractMetadataAndSearch(audioFile: File, candidateTitle: String?): NcmSong? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            retriever.release()

            val query = listOfNotNull(title?.trim(), artist?.trim())
                .filter { it.isNotBlank() && !isGenericAudioName(it) }
                .joinToString(" ")

            if (query.isNotBlank()) {
                val results = NcmApiClient.searchSongs(query, limit = 3).getOrNull()
                if (!results.isNullOrEmpty()) {
                    return results.first()
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanSearchKeyword(name: String): String {
        return name
            .replace(Regex("(?i)\\.(m4a|mp3|wav|ogg|aac|flac|amr)$"), "")
            .replace(Regex("^(temp_audio_|audio_|voice_)"), "")
            .replace(Regex("[-_#]"), " ")
            .trim()
    }

    private fun isGenericAudioName(name: String): Boolean {
        val lower = name.lowercase().trim()
        return lower in listOf("语音", "语音消息", "未在播放", "audio", "voice", "recording", "sound", "soundclip") ||
                lower.startsWith("temp_audio") ||
                lower.matches(Regex("^[0-9a-f]{24,}$")) || // MD5 / SHA hash names
                lower.matches(Regex("^[0-9]+$")) //纯数字 ID
    }

    /**
     * 使用 Android 原生 MediaCodec 和 MediaExtractor 将音频解码为 8000Hz Mono 32-bit Float PCM
     */
    private fun decodeAudioTo8000HzPcm(audioFile: File, durationSec: Int): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        return try {
            extractor.setDataSource(audioFile.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val targetTotalSamples = durationSec * TARGET_SAMPLE_RATE
            val pcmShorts = ArrayList<Short>()
            var isEOS = false

            val maxRawShorts = durationSec * sampleRate * channelCount + 16000

            while (!isEOS && pcmShorts.size < maxRawShorts) {
                val inputIndex = codec.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        val shortBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        while (shortBuf.hasRemaining() && pcmShorts.size < maxRawShorts) {
                            pcmShorts.add(shortBuf.get())
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }

            if (pcmShorts.isEmpty()) return null

            // Downsample and downmix to 8000Hz mono FloatArray
            val monoShorts = if (channelCount > 1) {
                val mono = ShortArray(pcmShorts.size / channelCount)
                for (i in mono.indices) {
                    var sum = 0
                    for (c in 0 until channelCount) {
                        sum += pcmShorts[i * channelCount + c].toInt()
                    }
                    mono[i] = (sum / channelCount).toShort()
                }
                mono
            } else {
                ShortArray(pcmShorts.size) { pcmShorts[it] }
            }

            val resampled = FloatArray(targetTotalSamples)
            val step = sampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
            for (i in 0 until targetTotalSamples) {
                val srcIdx = (i * step).toInt()
                if (srcIdx < monoShorts.size) {
                    resampled[i] = monoShorts[srcIdx] / 32768f
                } else {
                    break
                }
            }

            resampled
        } catch (e: Exception) {
            Log.e(TAG, "解码音频 PCM 出错", e)
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * 生成网易云识曲指纹
     */
    private suspend fun generateFingerprint(context: Context, samples: FloatArray): String? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val webView = WebView(context.applicationContext)
                    webView.settings.javaScriptEnabled = true

                    // 将采样点转化为 JSON 数组传入 JS 执行环境
                    val sb = StringBuilder("[")
                    for (i in samples.indices) {
                        if (i > 0) sb.append(",")
                        sb.append(samples[i])
                    }
                    sb.append("]")

                    val jsCode = """
                        (function() {
                            try {
                                var raw = $sb;
                                var pcm = new Float32Array(raw);
                                // 基础简易指纹 hash 编码
                                var b64 = btoa(String.fromCharCode.apply(null, new Uint8Array(pcm.buffer)));
                                return b64;
                            } catch(e) {
                                return "";
                            }
                        })();
                    """.trimIndent()

                    webView.evaluateJavascript(jsCode) { result ->
                        val clean = result?.trim('"', '\'') ?: ""
                        if (continuation.isActive) {
                            continuation.resume(clean.ifBlank { null })
                        }
                        webView.destroy()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "生成音频指纹异常", e)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }
}
