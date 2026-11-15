package com.yhchat.canary.utils.splitfile

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * 分块下载与合并。
 *
 * 流程按协议走：
 *   1. 逐块下载，**每块下完立刻校验它自己的 SHA-256**（能定位到坏块，不用整包重下）；
 *   2. 按块号顺序拼接；
 *   3. 对合并结果做完整 SHA-256（以及可选的 MD5）校验；
 *   4. 校验通过才发布成最终文件。
 *
 * 缓存目录：分块和临时合并文件都放 `filesDir/split/<transferId>/`。
 * 失败时保留分块，支持重试时只补缺的那几块 —— 协议要求"缺块允许重试"。
 */
class SplitFileDownloader(private val context: Context) {

    companion object {
        private const val TAG = "SplitFileDownloader"
        private const val BUFFER_SIZE = 8192
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 下载进度回调 */
    interface ProgressListener {
        fun onChunkStart(chunkIndex: Int, totalChunks: Int)
        fun onChunkProgress(chunkIndex: Int, bytesRead: Long, totalBytes: Long)
        fun onChunkDone(chunkIndex: Int)
        fun onMerging()
        fun onVerifying()
    }

    /** 下载结果 */
    sealed interface Result {
        data class Success(val file: File) : Result
        /** 有块下载失败或校验不通过 */
        data class Failed(val message: String, val failedChunks: List<Int> = emptyList()) : Result
        data object Cancelled : Result
    }

    private fun taskDir(transferId: String): File =
        File(File(context.filesDir, "split"), transferId).apply { mkdirs() }

    private fun chunkFile(taskDir: File, index: Int): File =
        File(taskDir, "chunk_${index.toString().padStart(5, '0')}")

    /** 已下载并校验通过的分块数量，用于显示"缓存了多少块" */
    fun cachedChunkCount(transferId: String, task: SplitTransferTask): Int {
        val dir = taskDir(transferId)
        return task.chunks.count { entry ->
            val file = chunkFile(dir, entry.chunkIndex)
            file.exists() && file.length() == entry.size &&
                SplitFileVerifier.verifyChunk(file, entry.chunkSha256)
        }
    }

    /** 清掉某个任务的全部缓存（下载完成且用户勾选了自动清理时用） */
    fun clearCache(transferId: String) {
        runCatching { taskDir(transferId).deleteRecursively() }
            .onFailure { Log.w(TAG, "清理分块缓存失败: ${it.message}") }
    }

    /**
     * 只清分块，保留合并后的成品文件。
     *
     * 「传完自动清缓存」要保的东西比要删的多：协议明确要求保留任务清单、检查点、
     * 已合并文件，以及用户最初选择的原文件。所以这里只删各块，成品留着 ——
     * 用户之后仍然能打开它，或从云端重新下载。
     */
    fun clearChunksOnly(transferId: String) {
        val dir = taskDir(transferId)
        runCatching {
            dir.listFiles()
                ?.filter { it.isFile && it.name.startsWith("chunk_") }
                ?.forEach { it.delete() }
        }.onFailure { Log.w(TAG, "清理分块失败: ${it.message}") }
    }

    /**
     * 下载并合并。
     *
     * 已存在且校验通过的分块会跳过，所以中断后重试只需补缺块。
     * 用 [isCancelled] 轮询取消，而不是用协程 cancel —— 文件写入和网络读取
     * 都不是可中断点，光 cancel 协程会让写了一半的文件留在磁盘上。
     */
    suspend fun download(
        task: SplitTransferTask,
        listener: ProgressListener? = null,
        isCancelled: () -> Boolean = { false }
    ): Result = withContext(Dispatchers.IO) {
        val transferId = task.key.transferId
        val dir = taskDir(transferId)
        val manifest = task.manifest

        val failedChunks = mutableListOf<Int>()

        // ---- 1. 逐块下载 ----
        for (entry in task.chunks.sortedBy { it.chunkIndex }) {
            if (isCancelled()) return@withContext Result.Cancelled

            val target = chunkFile(dir, entry.chunkIndex)

            // 已缓存且校验通过 → 跳过（断点续传）
            if (target.exists() && target.length() == entry.size &&
                SplitFileVerifier.verifyChunk(target, entry.chunkSha256)
            ) {
                listener?.onChunkDone(entry.chunkIndex)
                continue
            }

            listener?.onChunkStart(entry.chunkIndex, task.manifest.chunkCount)

            val ok = downloadSingleChunk(
                entry = entry,
                target = target,
                listener = listener,
                isCancelled = isCancelled
            )

            when {
                isCancelled() -> return@withContext Result.Cancelled
                !ok -> {
                    failedChunks += entry.chunkIndex
                    runCatching { target.delete() }
                }
                // 下完立刻校验本块
                !SplitFileVerifier.verifyChunk(target, entry.chunkSha256) -> {
                    Log.w(TAG, "第 ${entry.chunkIndex} 块哈希校验失败")
                    failedChunks += entry.chunkIndex
                    runCatching { target.delete() }
                }
                else -> listener?.onChunkDone(entry.chunkIndex)
            }
        }

        if (failedChunks.isNotEmpty()) {
            return@withContext Result.Failed(
                message = "${failedChunks.size} 个分块下载失败",
                failedChunks = failedChunks
            )
        }

        if (isCancelled()) return@withContext Result.Cancelled

        // ---- 2. 合并 ----
        listener?.onMerging()
        val mergedTmp = File(dir, "merged.tmp")
        val mergedOk = runCatching {
            mergedTmp.delete()
            RandomAccessFile(mergedTmp, "rw").use { out ->
                task.chunks.sortedBy { it.chunkIndex }.forEach { entry ->
                    val src = chunkFile(dir, entry.chunkIndex)
                    if (!src.exists()) throw IllegalStateException("缺少第 ${entry.chunkIndex} 块")
                    src.inputStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                        }
                    }
                }
            }
            true
        }.getOrElse {
            Log.e(TAG, "合并分块失败", it)
            false
        }

        if (!mergedOk) {
            runCatching { mergedTmp.delete() }
            return@withContext Result.Failed("合并分块失败")
        }

        // ---- 3. 完整性校验 ----
        listener?.onVerifying()
        val verifyError = SplitFileVerifier.verifyMerged(mergedTmp, manifest)
        if (verifyError != null) {
            Log.w(TAG, "合并结果校验失败: $verifyError")
            runCatching { mergedTmp.delete() }
            return@withContext Result.Failed(verifyError)
        }

        // ---- 4. 发布最终文件 ----
        val finalFile = File(dir, manifest.fileName.sanitizeFileName())
        runCatching {
            if (finalFile.exists()) finalFile.delete()
            if (!mergedTmp.renameTo(finalFile)) {
                // renameTo 在同分区应该成功，失败就退回复制
                mergedTmp.inputStream().use { input ->
                    finalFile.outputStream().use { output -> input.copyTo(output) }
                }
                mergedTmp.delete()
            }
        }.onFailure {
            Log.e(TAG, "发布最终文件失败", it)
            runCatching { mergedTmp.delete() }
            return@withContext Result.Failed("保存文件失败: ${it.message}")
        }

        Result.Success(finalFile)
    }

    /**
     * 下载单个分块。
     *
     * @return 是否完整下载到预期大小
     */
    private fun downloadSingleChunk(
        entry: SplitChunkEntry,
        target: File,
        listener: ProgressListener?,
        isCancelled: () -> Boolean
    ): Boolean {
        return try {
            val request = Request.Builder()
                .url(entry.fileUrl)
                // 云湖图床/文件服务需要 Referer，否则 403 —— 和下载器里的处理一致
                .header("Referer", "https://myapp.jwznb.com")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "块 ${entry.chunkIndex} HTTP ${response.code}")
                    return false
                }
                val body = response.body ?: return false
                val total = body.contentLength()

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var written = 0L
                        while (true) {
                            if (isCancelled()) return false
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            listener?.onChunkProgress(entry.chunkIndex, written, total)
                        }
                    }
                }

                // 大小对不上说明连接被截断，按失败处理（哈希校验虽然也能发现，
                // 但先比大小能省一次全文件读取）
                target.length() == entry.size
            }
        } catch (e: Exception) {
            Log.w(TAG, "块 ${entry.chunkIndex} 下载异常: ${e.message}")
            false
        }
    }

    /**
     * 文件名消毒。
     *
     * 清单里的 name 来自对方，直接拿来当本地文件名有路径穿越风险
     * （`../../xxx`），必须剥掉路径分隔符和控制字符。
     */
    private fun String.sanitizeFileName(): String {
        val cleaned = this
            .replace('/', '_')
            .replace('\\', '_')
            .filterNot { it.isISOControl() }
            .trim()
        return cleaned.ifBlank { "merged_file" }.take(120)
    }
}
