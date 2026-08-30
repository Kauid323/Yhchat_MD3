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
            // Log.d(TAG, "正在发起网易云听歌识曲识别: duration=${durationSec}s, fpLength=${rawFingerprint.length}")
            val matchResult = NcmApiClient.matchAudioFingerprint(durationSec, rawFingerprint).getOrNull()
            
            if (matchResult != null && matchResult.songs.isNotEmpty()) {
                // 识别到结果，大于等于1个直接取第1个
                val finalSong = matchResult.songs.first()
                // Log.d(TAG, "听歌识曲识别成功: ${finalSong.name} - ${finalSong.artist}")
                return@withContext finalSong
            } else {
                // Log.d(TAG, "网易云听歌识曲未识别到匹配歌曲: ${audioFile.name}")
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

            // 高保真线性插值重采样至 8000Hz Float PCM [-1.0, 1.0]
            val targetCount = Math.min(targetTotalSamples, kotlin.math.floor(monoShorts.size.toDouble() * TARGET_SAMPLE_RATE / sampleRate).toInt())
            val resampled = FloatArray(targetCount.coerceAtLeast(1)) { index ->
                val sourcePosition = index.toDouble() * sampleRate / TARGET_SAMPLE_RATE
                val left = kotlin.math.floor(sourcePosition).toInt().coerceIn(0, monoShorts.size - 1)
                val right = (left + 1).coerceAtMost(monoShorts.size - 1)
                val fraction = (sourcePosition - left).toFloat()
                ((monoShorts[left] * (1f - fraction) + monoShorts[right] * fraction) / 32767f).coerceIn(-1f, 1f)
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
     * 纯 Kotlin 原生实现 Shazam_v2 声学特征指纹生成
     * 流程：STFT 频谱变换 -> 对数频段峰值提取 (Peak Picking) -> 地标星座哈希配对 (Landmark Pairing) -> Little-Endian 序列化 -> ZLIB 压缩 -> Base64
     */
    private fun computePureKotlinFingerprint(samples: FloatArray): String? {
        try {
            if (samples.size < TARGET_SAMPLE_RATE * 2) return null

            val windowSize = 256
            val hopSize = 128
            val numFrames = (samples.size - windowSize) / hopSize
            if (numFrames <= 0) return null

            // 频段划分 (8000Hz 采样率下: 0~4000Hz 对应 0~128 bin，每个 bin 约 31.25Hz)
            // 频段覆盖：低频 250~500Hz (8~16), 中低频 500~1000Hz (16~32), 中高频 1000~2000Hz (32~64), 高频 2000~3500Hz (64~112)
            val freqBands = intArrayOf(8, 16, 32, 64, 112)
            val framePeaks = mutableListOf<Pair<Int, Int>>() // List of (frameIndex, peakBin)

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

                // 在各频段提取最大能量峰值点
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
                    if (maxMag > 0.02f) {
                        framePeaks.add(Pair(frame, maxBin))
                    }
                }
            }

            if (framePeaks.isEmpty()) return null

            // Shazam 地标星座哈希配对 (Combinatorial Hashing)
            // 对每个锚点 (t1, f1)，向后查找目标区内的峰值点 (t2, f2)，生成组合哈希值
            val landmarks = mutableListOf<Pair<Int, Int>>() // Pair<t1, hash32>
            val maxTargetOffset = 8 // 目标区向后 8 个 frame (约 128ms)
            
            for (i in framePeaks.indices) {
                val (t1, f1) = framePeaks[i]
                var count = 0
                for (j in i + 1 until framePeaks.size) {
                    val (t2, f2) = framePeaks[j]
                    val dt = t2 - t1
                    if (dt <= 0) continue
                    if (dt > maxTargetOffset) break

                    // 构造标准 32-bit 地标特征哈希: (f1: 8bit, f2: 8bit, dt: 8bit)
                    val hash = ((f1 and 0xFF) shl 16) or ((f2 and 0xFF) shl 8) or (dt and 0xFF)
                    landmarks.add(Pair(t1, hash))
                    count++
                    if (count >= 3) break // 每个锚点最多配对 3 个目标点，保持高判别度与紧凑性
                }
            }

            if (landmarks.isEmpty()) return null

            // 限制最多 400 个显著特征地标
            val selectedLandmarks = if (landmarks.size > 400) landmarks.take(400) else landmarks

            // 按照 Shazam 标准格式序列化为 Little-Endian 二进制指纹
            val buffer = ByteBuffer.allocate(4 + selectedLandmarks.size * 6).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(selectedLandmarks.size)
            for ((t1, hash) in selectedLandmarks) {
                buffer.putShort(t1.toShort())
                buffer.putInt(hash)
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
