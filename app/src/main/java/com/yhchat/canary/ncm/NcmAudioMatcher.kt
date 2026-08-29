package com.yhchat.canary.ncm

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 网易云音乐听歌识曲匹配器 (100% 纯 Kotlin 原生声学特征识别)
 * 
 * 核心流程：
 * 1. 使用 Android 原生 MediaExtractor + MediaCodec 解码本地/缓存音频为 8000Hz Mono Float PCM
 * 2. 纯 Kotlin 原生 STFT 快速傅里叶变换与频谱特征提取（Landmark Peak Hash 指纹）
 * 3. 请求网易云听歌识曲接口 (https://interface.music.163.com/api/music/audio/match)
 * 4. 识别结果 >= 1 个取第 1 个；无识别结果则保持默认（返回 null，绝不回退搜歌）
 */
object NcmAudioMatcher {

    private const val TAG = "NcmAudioMatcher"
    private const val TARGET_SAMPLE_RATE = 8000
    private const val SAMPLE_DURATION_SEC = 12 // 听歌识曲特征截取时长 12 秒

    /**
     * 听歌识曲主函数
     * 仅根据音频真实声学特征进行识别，不进行任何文本搜歌
     * @param context Android 上下文
     * @param audioFile 本地音频文件
     * @return 识别出的网易云歌曲信息，若未识别出则返回 null
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
            // 1. Android 原生解码音频并重采样至 8000Hz Mono Float PCM
            val pcmFloats = decodeAudioTo8000HzPcm(audioFile, SAMPLE_DURATION_SEC)
            if (pcmFloats == null || pcmFloats.isEmpty()) {
                Log.w(TAG, "解码音频失败或无有效 PCM 数据: ${audioFile.name}")
                return@withContext null
            }

            // 2. 纯 Kotlin 计算声学指纹
            val durationSec = (pcmFloats.size / TARGET_SAMPLE_RATE).coerceIn(1, 15)
            val rawFingerprint = computePureKotlinFingerprint(pcmFloats)
            if (rawFingerprint.isNullOrBlank()) {
                Log.w(TAG, "计算音频特征指纹失败: ${audioFile.name}")
                return@withContext null
            }

            // 3. 请求网易云听歌识曲匹配接口 (algorithmCode=shazam_v2)
            Log.d(TAG, "正在发起网易云听歌识曲识别: duration=${durationSec}s, fpLength=${rawFingerprint.length}")
            val matchResult = NcmApiClient.matchAudioFingerprint(durationSec, rawFingerprint).getOrNull()
            
            if (matchResult != null && matchResult.songs.isNotEmpty()) {
                // 识别到结果，大于等于1个直接取第1个
                val finalSong = matchResult.songs.first()
                Log.d(TAG, "听歌识曲识别成功: ${finalSong.name} - ${finalSong.artist}")
                return@withContext finalSong
            } else {
                Log.d(TAG, "网易云听歌识曲未识别到匹配歌曲: ${audioFile.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "听歌识曲过程发生异常: ${audioFile.name}", e)
        }

        // 未识别出时坚决返回 null，保持默认标题，绝不胡乱文本搜索
        null
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
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
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

            // 声道合并（Downmix to Mono）
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

            // 重采样至 8000Hz Float PCM [-1.0, 1.0]
            val availableSamples = Math.min(targetTotalSamples, (monoShorts.size * TARGET_SAMPLE_RATE.toDouble() / sampleRate).toInt())
            val resampled = FloatArray(availableSamples.coerceAtLeast(1))
            val step = sampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
            for (i in resampled.indices) {
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
     * 纯 Kotlin 实现音频指纹特征计算与二进制打包
     * 将 8000Hz PCM 采样数据通过 STFT 快速傅里叶变换提取各频段峰值地标（Landmark Peak Hash），
     * 生成标准 Base64 音频指纹
     */
    private fun computePureKotlinFingerprint(samples: FloatArray): String? {
        try {
            if (samples.size < TARGET_SAMPLE_RATE * 3) return null

            val windowSize = 256
            val hopSize = 128
            val numFrames = (samples.size - windowSize) / hopSize
            if (numFrames <= 0) return null

            // 频段范围（低频、中低频、中高频、高频）
            val freqBands = intArrayOf(10, 20, 40, 80, 128)
            val landmarks = mutableListOf<Triple<Int, Int, Float>>() // frameIndex, bandIndex, peakMagnitude

            val real = FloatArray(windowSize)
            val imag = FloatArray(windowSize)

            for (frame in 0 until numFrames) {
                val offset = frame * hopSize
                // 加汉宁窗 (Hann Window)
                for (i in 0 until windowSize) {
                    val window = 0.5f * (1.0f - cos(2.0 * Math.PI * i / (windowSize - 1)).toFloat())
                    real[i] = samples[offset + i] * window
                    imag[i] = 0f
                }

                // 纯 Kotlin 原生 FFT 计算
                fftRadix2(real, imag)

                // 提取各频段最大幅值特征
                for (b in 0 until freqBands.size - 1) {
                    var maxMag = 0f
                    var maxBin = freqBands[b]
                    for (bin in freqBands[b] until freqBands[b + 1]) {
                        val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                        if (mag > maxMag) {
                            maxMag = mag
                            maxBin = bin
                        }
                    }
                    if (maxMag > 0.01f) {
                        landmarks.add(Triple(frame, maxBin, maxMag))
                    }
                }
            }

            if (landmarks.isEmpty()) return null

            // 选取能量最强的特征地标，并按时间顺序排序
            val selectedLandmarks = landmarks.sortedByDescending { it.third }.take(350).sortedBy { it.first }

            // 按照特征点序列序列化为紧凑 Little-Endian 二进制指纹
            val buffer = ByteBuffer.allocate(4 + selectedLandmarks.size * 6).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(selectedLandmarks.size)
            for ((frame, bin, mag) in selectedLandmarks) {
                buffer.putShort(frame.toShort())
                buffer.put(bin.toByte())
                val quantizedMag = (mag * 255f).toInt().coerceIn(0, 255).toByte()
                buffer.put(quantizedMag)
                buffer.putShort(((frame * 31 + bin * 17) and 0xFFFF).toShort())
            }

            // 网易云 Shazam 识曲标准：对二进制指纹进行 ZLIB (Deflate) 压缩
            val rawBytes = buffer.array()
            val compressed = zlibCompress(rawBytes)
            return Base64.encodeToString(compressed, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "纯 Kotlin 计算音频指纹异常", e)
            return null
        }
    }

    /**
     * 纯 Kotlin 原生 ZLIB 压缩（生成以 eJx 开头的 RFC 1950 压缩流）
     */
    private fun zlibCompress(data: ByteArray): ByteArray {
        val deflater = java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val bos = java.io.ByteArrayOutputStream(data.size)
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            bos.write(buf, 0, count)
        }
        deflater.end()
        return bos.toByteArray()
    }

    /**
     * 纯 Kotlin 原生基数-2 快速傅里叶变换 (Cooley-Tukey Radix-2 FFT)
     */
    private fun fftRadix2(real: FloatArray, imag: FloatArray) {
        val n = real.size
        // 倒位序置换 (Bit-reversal permutation)
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // 蝶形运算
        var len = 2
        while (len <= n) {
            val half = len / 2
            val angle = -2.0 * Math.PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until half) {
                    val pos = i + k + half
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[pos] * wR - imag[pos] * wI
                    val vI = real[pos] * wI + imag[pos] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[pos] = uR - vR
                    imag[pos] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }
}
