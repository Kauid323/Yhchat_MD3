package com.yhchat.canary.ui.share.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.api.QiniuTokenResponse
import com.yhchat.canary.data.api.QiniuUploadResponse
import com.yhchat.canary.data.model.MsgForwardReceive
import com.yhchat.canary.data.repository.MessageRepository
import com.yhchat.canary.data.repository.SendMessageMedia
import com.yhchat.canary.data.repository.SendMessagePayload
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.ui.share.SendToChatContent
import com.yhchat.canary.ui.share.SendToChatMediaItem
import com.yhchat.canary.ui.share.SendToChatMediaKind
import com.yhchat.canary.ui.share.SendToChatTarget
import com.yhchat.canary.utils.FileUploadUtil
import com.yhchat.canary.utils.ImageUploadUtil
import com.yhchat.canary.utils.VideoUploadUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.File
import javax.inject.Inject

/** 发送进度（上传图片/附件时 progress 为 0f~1f） */
data class SendToChatProgress(
    val isSending: Boolean = false,
    val status: String? = null,
    val progress: Float? = null
)

/**
 * 「发送到会话」的发送逻辑：文本 / 图片 / 视频 / 附件 / 文章 / 转发消息。
 *
 * 图片、视频、附件会先上传到七牛（和聊天页发送时用的是同一套工具），
 * 上传完成后才发消息，所以进度可以在界面上显示。
 */
@HiltViewModel
class SendToChatViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository,
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val tag = "SendToChatViewModel"

    private val _progress = MutableStateFlow(SendToChatProgress())
    val progress: StateFlow<SendToChatProgress> = _progress.asStateFlow()

    /**
     * 发送内容到选中的会话。
     * 全部成功 → onFinished(Result.success)；任意一个失败 → onFinished(Result.failure)。
     */
    fun send(
        content: SendToChatContent,
        targets: List<SendToChatTarget>,
        onFinished: (Result<Unit>) -> Unit
    ) {
        if (targets.isEmpty()) {
            onFinished(Result.failure(IllegalStateException("请先选择会话")))
            return
        }

        viewModelScope.launch {
            _progress.value = SendToChatProgress(isSending = true, status = "正在发送…")
            val result = try {
                performSend(content, targets)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "发送失败", e)
                Result.failure(e)
            }
            _progress.value = SendToChatProgress()
            onFinished(result)
        }
    }

    private suspend fun performSend(content: SendToChatContent, targets: List<SendToChatTarget>) {
        when (content) {
            is SendToChatContent.Text -> sendText(content.text, targets)
            is SendToChatContent.Post -> sendPost(content, targets)
            is SendToChatContent.Media -> sendMedia(content.items, targets)
            is SendToChatContent.ForwardMessage -> forwardMessages(content, targets)
        }
    }

    // ==================== 文本 ====================

    private suspend fun sendText(text: String, targets: List<SendToChatTarget>) {
        if (text.isBlank()) throw IllegalStateException("没有可发送的文本")
        setStatus("正在发送…")
        sendToAll("文本", targets) { target ->
            messageRepository.sendMessage(
                chatId = target.chatId,
                chatType = target.chatType,
                text = text
            )
        }
    }

    // ==================== 文章 ====================

    private suspend fun sendPost(post: SendToChatContent.Post, targets: List<SendToChatTarget>) {
        val preview = if (post.content.length > 50) {
            post.content.substring(0, 50) + "..."
        } else {
            post.content
        }

        setStatus("正在发送…")
        sendToAll("文章", targets) { target ->
            messageRepository.sendMessage(
                chatId = target.chatId,
                chatType = target.chatType,
                payload = SendMessagePayload(
                    contentType = CONTENT_TYPE_POST,
                    postId = post.postId,
                    postTitle = post.title,
                    postContent = preview,
                    postType = post.postType.ifBlank { "1" }
                )
            )
        }
    }

    // ==================== 图片 / 视频 / 附件 ====================

    /**
     * 逐条上传并发送。每条按自己的类型发：图片→图片消息，视频→视频消息，其余→附件消息。
     */
    private suspend fun sendMedia(items: List<SendToChatMediaItem>, targets: List<SendToChatTarget>) {
        if (items.isEmpty()) throw IllegalStateException("没有可发送的内容")

        items.forEachIndexed { index, item ->
            val uri = item.uri.toShareUri()
            val label = item.kind.label()
            setStatus(
                if (items.size > 1) "正在上传${label} ${index + 1}/${items.size}…" else "正在上传${label}…"
            )

            when (item.kind) {
                SendToChatMediaKind.IMAGE -> {
                    val uploadToken = getUploadToken("图片") { apiService.getQiniuImageToken(it) }
                    val uploaded = ImageUploadUtil.uploadImage(
                        context = context,
                        imageUri = uri,
                        uploadToken = uploadToken,
                        onProgress = { updateProgress(it) }
                    ).getOrElse { throw IllegalStateException("上传图片失败: ${it.message}") }

                    sendToAll("图片", targets) { target ->
                        messageRepository.sendMessage(
                            chatId = target.chatId,
                            chatType = target.chatType,
                            payload = imagePayload(uploaded)
                        )
                    }
                }

                SendToChatMediaKind.VIDEO -> {
                    val uploadToken = getUploadToken("视频") { apiService.getQiniuVideoToken(it) }
                    val uploaded = VideoUploadUtil.uploadVideo(
                        context = context,
                        videoUri = uri,
                        uploadToken = uploadToken,
                        onProgress = { updateProgress(it) }
                    ).getOrElse { throw IllegalStateException("上传视频失败: ${it.message}") }

                    sendToAll("视频", targets) { target ->
                        messageRepository.sendMessage(
                            chatId = target.chatId,
                            chatType = target.chatType,
                            payload = videoPayload(uploaded, item.mimeType)
                        )
                    }
                }

                SendToChatMediaKind.FILE -> {
                    val uploadToken = getUploadToken("文件") { apiService.getQiniuFileToken(it) }
                    val uploaded = FileUploadUtil.uploadFile(
                        context = context,
                        fileUri = uri,
                        uploadToken = uploadToken,
                        onProgress = { updateProgress(it) }
                    ).getOrElse { throw IllegalStateException("上传附件失败: ${it.message}") }

                    val fileName = queryDisplayName(uri)

                    sendToAll("附件", targets) { target ->
                        messageRepository.sendMessage(
                            chatId = target.chatId,
                            chatType = target.chatType,
                            payload = SendMessagePayload(
                                contentType = CONTENT_TYPE_FILE,
                                fileName = fileName,
                                fileKey = uploaded.key,
                                fileSize = uploaded.fsize
                            )
                        )
                    }
                }
            }
        }
    }

    private fun SendToChatMediaKind.label(): String = when (this) {
        SendToChatMediaKind.IMAGE -> "图片"
        SendToChatMediaKind.VIDEO -> "视频"
        SendToChatMediaKind.FILE -> "附件"
    }

    private fun imagePayload(uploaded: QiniuUploadResponse): SendMessagePayload {
        val suffix = uploaded.key.substringAfterLast('.', "jpg").lowercase()
        val mimeType = when (suffix) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val width = uploaded.avinfo?.video?.width ?: 1080
        val height = uploaded.avinfo?.video?.height ?: 1920

        return SendMessagePayload(
            contentType = CONTENT_TYPE_IMAGE,
            imageKey = uploaded.key,
            fileSize = uploaded.fsize,
            media = SendMessageMedia(
                fileKey = uploaded.key,
                fileType = mimeType,
                fileSize = uploaded.fsize,
                fileSuffix = suffix,
                imageWidth = width.toLong(),
                imageHeight = height.toLong()
            )
        )
    }

    private fun videoPayload(
        uploaded: QiniuUploadResponse,
        mimeType: String?
    ): SendMessagePayload {
        val suffix = uploaded.key.substringAfterLast('.', "mp4").lowercase()
        val fileType = mimeType?.takeIf { it.startsWith("video/") } ?: "video/mp4"

        return SendMessagePayload(
            contentType = CONTENT_TYPE_VIDEO,
            videoKey = uploaded.key,
            media = SendMessageMedia(
                fileKey = uploaded.key,
                fileType = fileType,
                fileHash = uploaded.hash,
                fileSize = uploaded.fsize,
                fileSuffix = suffix
            )
        )
    }

    // ==================== 转发消息 ====================

    private suspend fun forwardMessages(
        content: SendToChatContent.ForwardMessage,
        targets: List<SendToChatTarget>
    ) {
        if (content.msgIds.isEmpty()) throw IllegalStateException("没有可转发的消息")

        val receive = targets.map { MsgForwardReceive(chatId = it.chatId, chatType = it.chatType) }

        setStatus("正在转发…")
        content.msgIds.forEach { msgId ->
            val result = messageRepository.forwardMessage(
                msgId = msgId,
                sourceChatType = content.sourceChatType,
                receive = receive
            )
            if (result.isFailure || result.getOrNull() != true) {
                throw IllegalStateException(result.exceptionOrNull()?.message ?: "转发失败")
            }
        }
    }

    // ==================== 工具 ====================

    private suspend fun sendToAll(
        label: String,
        targets: List<SendToChatTarget>,
        block: suspend (SendToChatTarget) -> Result<Boolean>
    ) {
        var error: String? = null
        targets.forEach { target ->
            val result = block(target)
            if (result.isFailure || result.getOrNull() != true) {
                error = error ?: result.exceptionOrNull()?.message ?: "${label}发送失败"
            }
        }
        error?.let { throw IllegalStateException(it) }
    }

    private fun setStatus(status: String) {
        _progress.value = _progress.value.copy(isSending = true, status = status, progress = null)
    }

    private fun updateProgress(value: Float) {
        _progress.value = _progress.value.copy(progress = value)
    }

    private suspend fun getUploadToken(
        label: String,
        request: suspend (String) -> Response<QiniuTokenResponse>
    ): String {
        val userToken = tokenRepository.getTokenSync()
        if (userToken.isNullOrBlank()) throw IllegalStateException("未登录")

        val response = request(userToken)
        val body = response.body()
        val token = body?.data?.token
        if (!response.isSuccessful || body?.code != 1 || token.isNullOrBlank()) {
            throw IllegalStateException("获取${label}上传凭证失败")
        }
        return token
    }

    private fun queryDisplayName(uri: Uri): String {
        val fromCursor = try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "无法获取文件名", e)
            null
        }

        return fromCursor?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "file_${System.currentTimeMillis()}"
    }

    /** content:// / file:// / 绝对路径 都能处理 */
    private fun String.toShareUri(): Uri = when {
        startsWith("content://") -> Uri.parse(this)
        startsWith("file://") -> Uri.parse(this)
        else -> Uri.fromFile(File(this))
    }

    private companion object {
        const val CONTENT_TYPE_IMAGE = 2
        const val CONTENT_TYPE_FILE = 4
        const val CONTENT_TYPE_POST = 6
        const val CONTENT_TYPE_VIDEO = 10
    }
}
