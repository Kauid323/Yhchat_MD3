package com.yhchat.canary.utils.splitfile

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yhchat.canary.data.model.SplitFileManifest
import com.yhchat.canary.data.model.SplitPartEntry
import com.yhchat.canary.data.model.SplitUploadProgress
import com.yhchat.canary.utils.FileUploadUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 非 VIP 分块上传：把大文件切成若干块，逐块上传七牛，生成 `.splitfiles` 清单。
 *
 * ## 流程
 * ```
 * 1. 边读边切：把源文件按 chunkSize 切成临时块文件（流式，不整块进内存）
 * 2. 逐块上传：每块走已有的 FileUploadUtil.uploadFile（七牛）
 * 3. 收集响应：把每块的 key / hash / size 按序记下来
 * 4. 生成清单：把 parts 按 index 排序、包进协议头，写成 .splitfiles 文件
 * 5. 清理临时块文件
 * ```
 *
 * ## 为什么块文件要落到磁盘而不是内存
 *
 * 一个 32MB 的块在内存里是 ByteArray，切 10 块就是 320MB —— 手机上直接 OOM。
 * 落盘后每块单独上传、上传完立即删除，内存峰值只有上传缓冲区的大小。
 */
object SplitFileUploader {

    private const val TAG = "SplitFileUploader"

    /**
     * 上传文件并生成清单。
     *
     * @param chunkSize 分块大小，默认 32MB
     * @param onProgress 进度回调，在 IO 线程调用
     * @return 生成的 `.splitfiles` 文件；失败返回异常
     */
    suspend fun splitAndUpload(
        context: Context,
        fileUri: Uri,
        uploadToken: String,
        chunkSize: Long = SplitFileManifest.DEFAULT_CHUNK_SIZE,
        originalName: String? = null,
        mime: String? = null,
        onProgress: (SplitUploadProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "split_upload_${System.currentTimeMillis()}")
        workDir.mkdirs()

        val chunkFiles = mutableListOf<File>()

        try {
            onProgress(SplitUploadProgress(stage = SplitUploadProgress.Stage.Preparing))

            // ---- 1. 切块 ----
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: return@withContext Result.failure(Exception("无法读取文件"))

            val fileName = originalName
                ?: queryDisplayName(context, fileUri)
                ?: "unnamed_file"

            val totalSize = inputStream.use { input ->
                var index = 0
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                var chunkOut: FileOutputStream? = null
                var chunkFile: File? = null
                var chunkWritten = 0L

                fun openNewChunk() {
                    chunkOut?.flush()
                    chunkOut?.close()
                    chunkFile?.let { chunkFiles.add(it) }
                    val f = File(workDir, "part_${index.toString().padStart(5, '0')}")
                    chunkFile = f
                    chunkOut = FileOutputStream(f)
                    chunkWritten = 0L
                    index++
                }

                openNewChunk()

                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    written += read
                    chunkOut!!.write(buffer, 0, read)
                    chunkWritten += read

                    // 当前块写满就换下一块
                    if (chunkWritten >= chunkSize) {
                        openNewChunk()
                    }
                }

                chunkOut?.flush()
                chunkOut?.close()
                // 最后一块：文件不为空才收进列表（避免多出一个 0 字节块）
                chunkFile?.let { last ->
                    if (last.length() > 0) chunkFiles.add(last) else last.delete()
                }

                written
            }

            if (chunkFiles.isEmpty() || totalSize <= 0L) {
                return@withContext Result.failure(Exception("文件为空"))
            }

            val totalChunks = chunkFiles.size
            Log.d(TAG, "切块完成: $totalChunks 块, 总计 $totalSize 字节")

            // ---- 2. 逐块上传 ----
            val parts = mutableListOf<SplitPartEntry>()
            chunkFiles.forEachIndexed { index, chunkFile ->
                onProgress(
                    SplitUploadProgress(
                        currentChunk = index,
                        totalChunks = totalChunks,
                        stage = SplitUploadProgress.Stage.Uploading
                    )
                )

                val uploadResult = FileUploadUtil.uploadFile(
                    context = context,
                    fileUri = Uri.fromFile(chunkFile),
                    uploadToken = uploadToken,
                    onProgress = { fraction ->
                        onProgress(
                            SplitUploadProgress(
                                currentChunk = index,
                                totalChunks = totalChunks,
                                chunkFraction = fraction,
                                stage = SplitUploadProgress.Stage.Uploading
                            )
                        )
                    }
                )

                val uploaded = uploadResult.getOrElse { e ->
                    Log.e(TAG, "第 ${index} 块上传失败", e)
                    return@withContext Result.failure(
                        Exception("第 ${index + 1}/$totalChunks 块上传失败: ${e.message}")
                    )
                }

                parts += SplitPartEntry(
                    index = index,
                    key = uploaded.key,
                    hash = uploaded.hash,
                    size = uploaded.fsize
                )

                // 传完就删，别占着缓存
                chunkFile.delete()
            }

            // ---- 3. 生成清单 ----
            onProgress(
                SplitUploadProgress(
                    currentChunk = totalChunks,
                    totalChunks = totalChunks,
                    stage = SplitUploadProgress.Stage.WritingManifest
                )
            )

            val manifest = SplitFileManifest(
                fileName = fileName,
                totalSize = totalSize,
                chunkSize = chunkSize,
                count = parts.size,
                mime = mime ?: context.contentResolver.getType(fileUri),
                // 按 index 显式排序：虽然上传是按序的，但显式保证一次不亏
                parts = parts.sortedBy { it.index }
            )

            val manifestFile = writeManifest(context, manifest)

            onProgress(
                SplitUploadProgress(
                    currentChunk = totalChunks,
                    totalChunks = totalChunks,
                    stage = SplitUploadProgress.Stage.Done
                )
            )

            Result.success(manifestFile)
        } catch (e: Exception) {
            Log.e(TAG, "分块上传失败", e)
            Result.failure(e)
        } finally {
            // 清理：无论成功失败，临时目录都要删掉
            chunkFiles.forEach { runCatching { it.delete() } }
            runCatching { workDir.deleteRecursively() }
        }
    }

    /**
     * 把清单写成 `.splitfiles` 文件。
     *
     * 内容是可读的 JSON（带缩进），这样用户用文本编辑器打开也能看懂，
     * 排查问题时不必再写解析器。
     */
    private fun writeManifest(context: Context, manifest: SplitFileManifest): File {
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(manifest)

        val baseName = manifest.fileName.substringBeforeLast('.', manifest.fileName)
        val outFile = File(context.cacheDir, "$baseName${SplitFileManifest.FILE_EXTENSION}")
        outFile.writeText(json, Charsets.UTF_8)
        Log.d(TAG, "清单已生成: ${outFile.absolutePath} (${outFile.length()} 字节)")
        return outFile
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从文件名判断是不是分块清单。
     *
     * 只认后缀，不读内容 —— 这个消息列表要频繁调用，不能每条消息都开一次文件。
     */
    fun isSplitFile(fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) return false
        return fileName.endsWith(SplitFileManifest.FILE_EXTENSION, ignoreCase = true)
    }
}
