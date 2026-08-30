package com.yhchat.canary.data.repository

import android.util.Log
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.*
import com.yhchat.canary.proto.*
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class SendMessageMedia(
    val fileKey: String,
    val fileType: String,
    val fileSize: Long? = null,
    val fileHash: String? = null,
    val fileSuffix: String? = null,
    val imageWidth: Long? = null,
    val imageHeight: Long? = null
)

data class SendMessagePayload(
    val contentType: Int,
    val text: String? = null,
    val buttons: String? = null,
    val imageKey: String? = null,
    val fileName: String? = null,
    val fileKey: String? = null,
    val fileSize: Long? = null,
    val form: String? = null,
    val postId: String? = null,
    val postTitle: String? = null,
    val postContent: String? = null,
    val postType: String? = null,
    val expressionId: String? = null,
    val videoKey: String? = null,
    val audioKey: String? = null,
    val audioTime: Long? = null,
    val stickerItemId: Long? = null,
    val stickerPackId: Long? = null,
    val roomName: String? = null,
    val quoteMsgId: String? = null,
    val quoteMsgText: String? = null,
    val quoteImageUrl: String? = null,
    val quoteImageName: String? = null,
    val quoteVideoUrl: String? = null,
    val quoteVideoTime: Long? = null,
    val commandId: Long? = null,
    val mentionedIds: List<String>? = null,
    val botLlmParams: String? = null,
    val media: SendMessageMedia? = null
)

@Singleton
class MessageRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenRepository: TokenRepository,
    private val cacheRepository: CacheRepository
) {
    private val tag = "MessageRepository"
    private var botLlmParamsProvider: ((chatId: String, chatType: Int) -> String?)? = null

    private val ulidRandom = SecureRandom()

    fun setBotLlmParamsProvider(provider: ((chatId: String, chatType: Int) -> String?)?) {
        botLlmParamsProvider = provider
    }
    
    // 常量
    private companion object {
        const val DEFAULT_MSG_COUNT = 20
        const val CONTENT_TYPE_TEXT = 1
        const val CONTENT_TYPE_IMAGE = 2
        const val CONTENT_TYPE_FILE = 4
        const val CONTENT_TYPE_POST = 6
        const val CONTENT_TYPE_EXPRESSION = 7
        const val CONTENT_TYPE_VIDEO = 10
        const val CONTENT_TYPE_AUDIO = 11
        const val CONTENT_TYPE_A2UI = 14
        val HEX_CHARS = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        )
    }

    suspend fun forwardMessage(
        msgId: String,
        sourceChatType: Int,
        receive: List<MsgForwardReceive>
    ): Result<Boolean> {
        return try {
            val token = getToken()
            if (token.isNullOrEmpty()) {
                return Result.failure(Exception("用户未登录"))
            }

            val request = MsgForwardRequest(
                msgId = msgId,
                chatType = sourceChatType,
                receive = receive
            )

            val response = apiService.forwardMessage(token, request)
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("响应体为空"))
                if (body.code == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(body.message))
                }
            } else {
                Result.failure(Exception("转发失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ 转发消息异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取Token的辅助方法
     */
    private suspend fun getToken(): String? {
        val tokenFlow = tokenRepository.getToken()
        val token = tokenFlow.first()?.token
        if (token.isNullOrEmpty()) {
            Log.e(tag, "Token is null or empty")
        }
        return token
    }
    
    /**
     * 处理Protobuf API响应的通用方法
     */
    private inline fun <reified T> handleProtobufResponse(
        response: retrofit2.Response<okhttp3.ResponseBody>,
        parseResponse: (ByteArray) -> T,
        getStatus: (T) -> Any,
        getStatusCode: (Any) -> Int,
        getStatusMsg: (Any) -> String,
        onSuccess: (T) -> Result<Any>
    ): Result<Any> {
        return if (response.isSuccessful) {
            response.body()?.let { responseBody ->
                val bytes = responseBody.bytes()
                val parsedResponse = parseResponse(bytes)
                val status = getStatus(parsedResponse)
                val code = getStatusCode(status)
                val msg = getStatusMsg(status)
                
                if (code == 1) {
                    onSuccess(parsedResponse)
                } else {
                    Log.e(tag, "API error: $msg")
                    Result.failure(Exception(msg))
                }
            } ?: Result.failure(Exception("响应体为空"))
        } else {
            Log.e(tag, "HTTP error: ${response.code()}")
            Result.failure(Exception("网络请求失败: ${response.code()}"))
        }
    }
    
    /**
     * 获取消息列表
     * 
     * 使用 list-message 接口：
     * - 列出的是指定消息ID **之前** 的消息
     * - 不包含请求的msgId指定的消息
     * - proto结构与 list-message-by-mid-seq 共用
     */
    suspend fun getMessages(
        chatId: String,
        chatType: Int,
        msgCount: Int = DEFAULT_MSG_COUNT,
        msgId: String? = null
    ): Result<List<ChatMessage>> {
        return try {
            val tokenFlow = tokenRepository.getToken()
            val token = tokenFlow.first()?.token
            if (token.isNullOrEmpty()) {
                Log.e(tag, "Token is null or empty")
                return Result.failure(Exception("用户未登录"))
            }

            // 构建protobuf请求
            val requestBuilder = list_message_send.newBuilder()
                .setChatId(chatId)
                .setChatType(chatType.toLong())
                .setMsgCount(msgCount.toLong())
            
            if (!msgId.isNullOrEmpty()) {
                requestBuilder.setMsgId(msgId)
            }
            
            val request = requestBuilder.build()
            val requestBody = request.toByteArray().toRequestBody("application/x-protobuf".toMediaType())

            if (msgId.isNullOrEmpty()) {
                // Log.d(tag, "📡 获取最新消息: chatId=$chatId, chatType=$chatType, count=$msgCount")
            } else {
                // Log.d(tag, "📡 通过msgId获取消息: chatId=$chatId, chatType=$chatType, msgId=$msgId, count=$msgCount")
            }
            
            val response = apiService.listMessage(token, requestBody)
            
            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val bytes = responseBody.bytes()
                    val messageResponse = list_message.parseFrom(bytes)
                    
                    if (messageResponse.status.code == 1) {
                        val messages = messageResponse.msgList.map { protoMsg ->
                            convertProtoToMessage(protoMsg, chatId, chatType)
                        }
                        
                        if (msgId.isNullOrEmpty()) {
                            // Log.d(tag, "✅ 成功获取 ${messages.size} 条最新消息")
                        } else {
                            // Log.d(tag, "✅ 成功通过msgId获取 ${messages.size} 条消息")
                            // 验证目标消息是否在结果中
                            val foundTarget = messages.find { it.msgId == msgId }
                            if (foundTarget != null) {
                                // Log.d(tag, "🎯 确认找到目标消息 msgId: $msgId")
                            } else {
                                Log.w(tag, "⚠️ 未找到目标消息 msgId: $msgId（可能被过滤或不存在）")
                            }
                        }
                        Result.success(messages)
                } else {
                        Log.e(tag, "API error: ${messageResponse.status.msg}")
                        Result.failure(Exception(messageResponse.status.msg))
                }
                } ?: Result.failure(Exception("响应体为空"))
            } else {
                Log.e(tag, "HTTP error: ${response.code()}")
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting messages", e)
            Result.failure(e)
        }
    }
    
    /**
     * 通过序列获取消息列表
     */
    suspend fun getMessagesBySeq(
        chatId: String,
        chatType: Int,
        msgSeq: Long = 0
    ): Result<List<ChatMessage>> {
        return try {
            val tokenFlow = tokenRepository.getToken()
            val token = tokenFlow.first()?.token
            if (token.isNullOrEmpty()) {
                Log.e(tag, "Token is null or empty")
                return Result.failure(Exception("用户未登录"))
            }

            // 构建protobuf请求
            val request = list_message_by_seq_send.newBuilder()
                .setChatId(chatId)
                .setChatType(chatType.toLong())
                .setMsgSeq(msgSeq)
                .build()
            
            val requestBody = request.toByteArray().toRequestBody("application/x-protobuf".toMediaType())

            // Log.d(tag, "Getting messages by seq for chat: $chatId, type: $chatType, seq: $msgSeq")
            
            val response = apiService.listMessageBySeq(token, requestBody)
            
            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val bytes = responseBody.bytes()
                    val messageResponse = list_message_by_seq.parseFrom(bytes)

                    if (messageResponse.status.code == 1) {
                        val messages = messageResponse.msgList.map { protoMsg ->
                            convertProtoToMessage(protoMsg, chatId, chatType)
                        }
                        // Log.d(tag, "Successfully got ${messages.size} messages by seq")
                        Result.success(messages)
                } else {
                        Log.e(tag, "API error: ${messageResponse.status.msg}")
                        Result.failure(Exception(messageResponse.status.msg))
                }
                } ?: Result.failure(Exception("响应体为空"))
            } else {
                Log.e(tag, "HTTP error: ${response.code()}")
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting messages by seq", e)
            Result.failure(e)
        }
    }
    
    /**
     * 发送文本/指令消息（便捷重载）
     */
    suspend fun sendMessage(
        chatId: String,
        chatType: Int,
        text: String,
        contentType: Int = CONTENT_TYPE_TEXT,
        quoteMsgId: String? = null,
        quoteMsgText: String? = null,
        quoteImageUrl: String? = null,
        quoteImageName: String? = null,
        quoteVideoUrl: String? = null,
        quoteVideoTime: Long? = null,
        commandId: Long? = null,
        mentionedIds: List<String>? = null
    ): Result<Boolean> {
        val buttons = if (contentType == CONTENT_TYPE_A2UI) text else null
        val payload = SendMessagePayload(
            contentType = contentType,
            text = text,
            buttons = buttons,
            quoteMsgId = quoteMsgId,
            quoteMsgText = quoteMsgText,
            quoteImageUrl = quoteImageUrl,
            quoteImageName = quoteImageName,
            quoteVideoUrl = quoteVideoUrl,
            quoteVideoTime = quoteVideoTime,
            commandId = commandId,
            mentionedIds = mentionedIds
        )
        return sendMessage(chatId, chatType, payload)
    }

    /**
     * 发送通用载荷（图片/文件/语音/表单/A2UI等）
     */
    suspend fun sendMessage(
        chatId: String,
        chatType: Int,
        payload: SendMessagePayload
    ): Result<Boolean> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("用户未登录"))

            val msgId = generateUlid()
            val contentBuilder = send_message_send.Content.newBuilder()
            var hasPrimaryContent = false

            fun markContent(block: () -> Unit) {
                hasPrimaryContent = true
                block()
            }

            val isSendTextAllowEmptySetting = true//TODO 不知道这里怎么读取设置
            payload.text?.takeIf { it.isNotBlank() || isSendTextAllowEmptySetting }?.let { markContent { contentBuilder.text = it } }

            val resolvedButtons = when {
                payload.contentType == CONTENT_TYPE_A2UI && payload.buttons.isNullOrBlank() -> payload.text
                else -> payload.buttons
            }
            resolvedButtons?.takeIf { it.isNotBlank() }?.let { markContent { contentBuilder.buttons = it } }
            payload.form?.takeIf { it.isNotBlank() }?.let { markContent { contentBuilder.form = it } }
            payload.roomName?.takeIf { it.isNotBlank() }?.let(contentBuilder::setRoomName)

            payload.imageKey?.let { key ->
                markContent { contentBuilder.image = key }
                payload.fileSize?.let(contentBuilder::setFileSize)
            }

            payload.fileKey?.let { key ->
                markContent {
                    contentBuilder.file = key
                    payload.fileName?.let(contentBuilder::setFileName)
                    payload.fileSize?.let(contentBuilder::setFileSize)
                }
            }

            payload.postId?.let { id ->
                markContent {
                    contentBuilder.postId = id
                    contentBuilder.postTitle = payload.postTitle.orEmpty()
                    contentBuilder.postContent = payload.postContent.orEmpty()
                    contentBuilder.postType = payload.postType ?: "1"
                }
            }

            payload.expressionId?.let { id ->
                markContent { contentBuilder.expressionId = id }
            }

            payload.stickerItemId?.let(contentBuilder::setStickerItemId)
            payload.stickerPackId?.let(contentBuilder::setStickerPackId)

            payload.videoKey?.let { key ->
                markContent { contentBuilder.video = key }
            }

            payload.audioKey?.let { key ->
                markContent {
                    contentBuilder.audio = key
                    payload.audioTime?.let(contentBuilder::setAudioTime)
                }
            }

            payload.quoteMsgText?.let(contentBuilder::setQuoteMsgText)
            payload.quoteImageUrl?.let(contentBuilder::setQuoteImageUrl)
            payload.quoteImageName?.let(contentBuilder::setQuoteImageName)
            payload.quoteVideoUrl?.let(contentBuilder::setQuoteVideoUrl)
            payload.quoteVideoTime?.takeIf { it > 0 }?.let(contentBuilder::setQuoteVideoTime)

            payload.mentionedIds?.takeIf { it.isNotEmpty() }?.let(contentBuilder::addAllMentionedId)

            val resolvedBotLlmParams = payload.botLlmParams
                ?.takeIf { it.isNotBlank() }
                ?: botLlmParamsProvider?.invoke(chatId, chatType)?.takeIf { it.isNotBlank() }
            resolvedBotLlmParams?.let(contentBuilder::setBotLlmParams)

            if (!hasPrimaryContent) {
                return Result.failure(Exception("发送内容为空"))
            }

            val requestBuilder = send_message_send.newBuilder()
                .setMsgId(msgId)
                .setChatId(chatId)
                .setChatType(chatType.toLong())
                .setContent(contentBuilder.build())
                .setContentType(payload.contentType.toLong())

            payload.quoteMsgId?.let(requestBuilder::setQuoteMsgId)
            payload.commandId?.let(requestBuilder::setCommandId)

            payload.media?.let { media ->
                val mediaBuilder = send_message_send.Media.newBuilder()
                    .setFileKey(media.fileKey)
                    .setFileKey2(media.fileKey)
                    .setFileType(media.fileType)
                media.fileHash?.let(mediaBuilder::setFileHash)
                media.fileSize?.let(mediaBuilder::setFileSize)
                media.imageWidth?.let(mediaBuilder::setImageWidth)
                media.imageHeight?.let(mediaBuilder::setImageHeight)
                media.fileSuffix?.let(mediaBuilder::setFileSuffix)
                requestBuilder.setMedia(mediaBuilder.build())
            }

            val request = requestBuilder.build()
            val requestBody = request.toByteArray().toRequestBody("application/x-protobuf".toMediaType())

            val response = apiService.sendMessage(token, requestBody)
            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val sendResponse = send_message.parseFrom(responseBody.bytes())
                    if (sendResponse.status.code == 1) {
                        // Log.d(tag, "Message sent successfully")
                        Result.success(true)
                    } else {
                        Log.e(tag, "Send message error: ${sendResponse.status.msg}")
                        Result.failure(Exception(sendResponse.status.msg))
                    }
                } ?: Result.failure(Exception("响应体为空"))
            } else {
                Log.e(tag, "HTTP error: ${response.code()}")
                Result.failure(Exception("发送失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error sending message", e)
            Result.failure(e)
        }
    }

    private fun generateUlid(timestampMs: Long = System.currentTimeMillis()): String {
        val bytes = ByteArray(16)
        var time = timestampMs
        for (i in 5 downTo 0) {
            bytes[i] = (time and 0xFF).toByte()
            time = time shr 8
        }

        val randomBytes = ByteArray(10)
        ulidRandom.nextBytes(randomBytes)
        System.arraycopy(randomBytes, 0, bytes, 6, randomBytes.size)

        // 让格式更接近 UUID4：设置 version / variant 位，但仍保持 32 位十六进制字符串。
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

        val hexChars = CharArray(32)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = HEX_CHARS[v ushr 4]
            hexChars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(hexChars)
    }

    /**
     * 撤回消息
     * 使用protobuf的recall_msg_send
     */
    suspend fun recallMessage(
        chatId: String,
        chatType: Int,
        msgId: String
    ): Result<Boolean> {
        return try {
            val tokenFlow = tokenRepository.getToken()
            val token = tokenFlow.first()?.token
            if (token.isNullOrEmpty()) {
                Log.e(tag, "❌ Token为空")
                return Result.failure(Exception("用户未登录"))
            }
            
            // Log.d(tag, "📤 ========== 撤回消息 ==========")
            // Log.d(tag, "📤 msgId: $msgId")
            // Log.d(tag, "📤 chatId: $chatId")
            // Log.d(tag, "📤 chatType: $chatType")
            
            // 构建protobuf请求
            val request = recall_msg_send.newBuilder()
                .setMsgId(msgId)
                .setChatId(chatId)
                .setChatType(chatType.toLong())
                .build()
            
            val requestBody = request.toByteArray().toRequestBody("application/x-protobuf".toMediaType())
            
            // Log.d(tag, "📤 发送撤回请求...")
            
            val response = apiService.recallMessage(token, requestBody)
            
            // Log.d(tag, "📥 服务器响应码: ${response.code()}")
            
            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val bytes = responseBody.bytes()
                    val recallResponse = recall_msg.parseFrom(bytes)
                    
                    // Log.d(tag, "📥 响应状态码: ${recallResponse.status.code}")
                    // Log.d(tag, "📥 响应消息: ${recallResponse.status.msg}")
                    
                    if (recallResponse.status.code == 1) {
                        // Log.d(tag, "✅ ========== 消息撤回成功！ ==========")
                        Result.success(true)
                    } else {
                        Log.e(tag, "❌ 撤回失败: ${recallResponse.status.msg}")
                        Result.failure(Exception(recallResponse.status.msg))
                    }
                } ?: Result.failure(Exception("响应体为空"))
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(tag, "❌ HTTP错误: ${response.code()}, 错误详情: $errorBody")
                Result.failure(Exception("撤回失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ 撤回消息异常", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 批量撤回消息
     * 使用protobuf的recall_msg_batch_send
     */
    suspend fun recallMessagesBatch(
        chatId: String,
        chatType: Int,
        msgIds: List<String>
    ): Result<Boolean> {
        return try {
            val token = getToken()
            if (token.isNullOrEmpty()) {
                Log.e(tag, "❌ Token为空")
                return Result.failure(Exception("用户未登录"))
            }
            
            // Log.d(tag, "📤 ========== 批量撤回消息 ==========")
            // Log.d(tag, "📤 msgIds: $msgIds (共${msgIds.size}条)")
            // Log.d(tag, "📤 chatId: $chatId")
            // Log.d(tag, "📤 chatType: $chatType")
            
            // 构建protobuf请求
            val request = recall_msg_batch_send.newBuilder()
                .addAllMsgId(msgIds)
                .setChatId(chatId)
                .setChatType(chatType.toLong())
                .build()
            
            val requestBody = request.toByteArray().toRequestBody("application/x-protobuf".toMediaType())
            
            // Log.d(tag, "📤 发送批量撤回请求...")
            
            val response = apiService.recallMessagesBatchProto(token, requestBody)
            
            // Log.d(tag, "📥 服务器响应码: ${response.code()}")
            
            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val bytes = responseBody.bytes()
                    val recallResponse = recall_msg_batch.parseFrom(bytes)
                    
                    // Log.d(tag, "📥 响应状态码: ${recallResponse.status.code}")
                    // Log.d(tag, "📥 响应消息: ${recallResponse.status.msg}")
                    
                    if (recallResponse.status.code == 1) {
                        // Log.d(tag, "✅ ========== 批量撤回成功！ ==========")
                        Result.success(true)
                    } else {
                        Log.e(tag, "❌ 批量撤回失败: ${recallResponse.status.msg}")
                        Result.failure(Exception(recallResponse.status.msg))
                    }
                } ?: Result.failure(Exception("响应体为空"))
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(tag, "❌ HTTP错误: ${response.code()}, 错误详情: $errorBody")
                Result.failure(Exception("批量撤回失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ 批量撤回消息异常", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 编辑消息
     * 使用protobuf的edit_message_send
     */
    suspend fun editMessage(
        chatId: String,
        chatType: Int,
        msgId: String,
        content: String,
        contentType: Int,
        quoteMsgId: String? = null,
        quoteMsgText: String? = null,
        quoteImageUrl: String? = null,
        quoteImageName: String? = null,
        quoteVideoUrl: String? = null,
        quoteVideoTime: Long? = null,
        buttons: String? = null
    ): Result<Boolean> {
        return try {
            val tokenFlow = tokenRepository.getToken()
            val token = tokenFlow.first()?.token
            if (token.isNullOrEmpty()) {
                Log.e(tag, "❌ Token为空")
                return Result.failure(Exception("用户未登录"))
            }
            
            // Log.d(tag, "📤 ========== 编辑消息 ==========")
            // Log.d(tag, "📤 msgId: $msgId")
            // Log.d(tag, "📤 chatId: $chatId")
            // Log.d(tag, "📤 chatType: $chatType")
            // Log.d(tag, "📤 contentType: $contentType")
            // Log.d(tag, "📤 content: ${content.take(100)}...")
            
            // 构建protobuf请求
            val contentBuilder = edit_message_send.Content.newBuilder()
                .setText(content)

            if (!buttons.isNullOrEmpty()) {
                contentBuilder.setButtons(buttons)
            }
            
            // 添加引用消息的所有相关字段以保持原引用内容
            if (!quoteMsgText.isNullOrEmpty()) {
                contentBuilder.setQuoteMsgText(quoteMsgText)
            }
            if (!quoteImageUrl.isNullOrEmpty()) {
                contentBuilder.setQuoteImageUrl(quoteImageUrl)
            }
            if (!quoteImageName.isNullOrEmpty()) {
                contentBuilder.setQuoteImageName(quoteImageName)
            }
            if (!quoteVideoUrl.isNullOrEmpty()) {
                contentBuilder.setQuoteVideoUrl(quoteVideoUrl)
            }
            if (quoteVideoTime != null && quoteVideoTime > 0) {
                contentBuilder.setQuoteVideoTime(quoteVideoTime)
            }
            
            val requestBuilder = edit_message_send.newBuilder()
                .setMsgId(msgId)
                .setChatId(chatId)
                .setChatType(chatType)
                .setContent(contentBuilder.build())
                .setContentType(contentType.toLong())
            
            if (!quoteMsgId.isNullOrEmpty()) {
                requestBuilder.setQuoteMsgId(quoteMsgId)
            }

            val request = requestBuilder.build()
            
            val requestBody = request.toByteArray().toRequestBody("application/x-protobuf".toMediaType())
            
            // Log.d(tag, "📤 发送编辑请求...")
            
            val response = apiService.editMessage(token, requestBody)
            
            // Log.d(tag, "📥 服务器响应码: ${response.code()}")
            
            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val bytes = responseBody.bytes()
                    val editResponse = edit_message.parseFrom(bytes)
                    
                    // Log.d(tag, "📥 响应状态码: ${editResponse.status.code}")
                    // Log.d(tag, "📥 响应消息: ${editResponse.status.msg}")
                    
                    if (editResponse.status.code == 1) {
                        // Log.d(tag, "✅ ========== 消息编辑成功！ ==========")
                        Result.success(true)
                    } else {
                        Log.e(tag, "❌ 编辑失败: ${editResponse.status.msg}")
                        Result.failure(Exception(editResponse.status.msg))
                    }
                } ?: Result.failure(Exception("响应体为空"))
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(tag, "❌ HTTP错误: ${response.code()}, 错误详情: $errorBody")
                Result.failure(Exception("编辑失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ 编辑消息异常", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 将Proto消息转换为应用内消息模型
     */
    private fun convertProtoToMessage(protoMsg: Msg, chatId: String, chatType: Int): ChatMessage {
        val sender = MessageSender(
            chatId = protoMsg.sender.chatId,
            chatType = protoMsg.sender.chatType,
            name = protoMsg.sender.name,
            avatarUrl = protoMsg.sender.avatarUrl,
            tagOld = protoMsg.sender.tagOldList,
            tag = protoMsg.sender.tagList.map { tag ->
                MessageTag(
                    id = tag.id,
                    text = tag.text,
                    color = tag.color
                )
            }
        )

        val content = MessageContent(
            text = if (protoMsg.content.text.isNotEmpty()) protoMsg.content.text else null,
            buttons = if (protoMsg.content.buttons.isNotEmpty()) protoMsg.content.buttons else null,
            imageUrl = if (protoMsg.content.imageUrl.isNotEmpty()) protoMsg.content.imageUrl else null,
            fileName = if (protoMsg.content.fileName.isNotEmpty()) protoMsg.content.fileName else null,
            fileUrl = if (protoMsg.content.fileUrl.isNotEmpty()) protoMsg.content.fileUrl else null,
            form = if (protoMsg.content.form.isNotEmpty()) protoMsg.content.form else null,
            quoteMsgText = if (protoMsg.content.quoteMsgText.isNotEmpty()) protoMsg.content.quoteMsgText else null,
            quoteImageUrl = if (protoMsg.content.quoteImageUrl.isNotEmpty()) protoMsg.content.quoteImageUrl else null,
            quoteImageName = if (protoMsg.content.quoteImageName.isNotEmpty()) protoMsg.content.quoteImageName else null,
            quoteVideoUrl = if (protoMsg.content.quoteVideoUrl.isNotEmpty()) protoMsg.content.quoteVideoUrl else null,
            quoteVideoTime = if (protoMsg.content.quoteVideoTime > 0) protoMsg.content.quoteVideoTime else null,
            stickerUrl = if (protoMsg.content.stickerUrl.isNotEmpty()) protoMsg.content.stickerUrl else null,
            postId = if (protoMsg.content.postId.isNotEmpty()) protoMsg.content.postId else null,
            postTitle = if (protoMsg.content.postTitle.isNotEmpty()) protoMsg.content.postTitle else null,
            postContent = if (protoMsg.content.postContent.isNotEmpty()) protoMsg.content.postContent else null,
            postContentType = if (protoMsg.content.postContentType.isNotEmpty()) protoMsg.content.postContentType else null,
            expressionId = if (protoMsg.content.expressionId.isNotEmpty()) protoMsg.content.expressionId else null,
            fileSize = if (protoMsg.content.fileSize > 0) protoMsg.content.fileSize else null,
            videoUrl = if (protoMsg.content.videoUrl.isNotEmpty()) protoMsg.content.videoUrl else null,
            audioUrl = if (protoMsg.content.audioUrl.isNotEmpty()) protoMsg.content.audioUrl else null,
            audioTime = if (protoMsg.content.audioTime > 0) protoMsg.content.audioTime else null,
            stickerItemId = if (protoMsg.content.stickerItemId > 0) protoMsg.content.stickerItemId else null,
            stickerPackId = if (protoMsg.content.stickerPackId > 0) protoMsg.content.stickerPackId else null,
            callText = if (protoMsg.content.callText.isNotEmpty()) protoMsg.content.callText else null,
            callStatusText = if (protoMsg.content.callStatusText.isNotEmpty()) protoMsg.content.callStatusText else null,
            width = if (protoMsg.content.width > 0) protoMsg.content.width else null,
            height = if (protoMsg.content.height > 0) protoMsg.content.height else null
        )

        val cmd = if (protoMsg.hasCmd()) {
            MessageCmd(
                name = protoMsg.cmd.name,
                type = protoMsg.cmd.type
            )
        } else null

        return ChatMessage(
            msgId = protoMsg.msgId,
            sender = sender,
            direction = protoMsg.direction,
            contentType = protoMsg.contentType,
            content = content,
            sendTime = protoMsg.sendTime,
            cmd = cmd,
            msgDeleteTime = if (protoMsg.msgDeleteTime > 0) protoMsg.msgDeleteTime else null,
            quoteMsgId = if (protoMsg.quoteMsgId.isNotEmpty()) protoMsg.quoteMsgId else null,
            msgSeq = protoMsg.msgSeq,
            editTime = if (protoMsg.editTime > 0) protoMsg.editTime else null,
            chatId = chatId,  // 设置会话ID
            chatType = chatType  // 设置会话类型
        )
    }
    
    // ========== WebSocket相关的本地存储方法 ==========
    
    /**
     * 通过消息ID获取消息
     */
    suspend fun getMessageById(msgId: String): ChatMessage? {
        return try {
            cacheRepository.getMessageById(msgId)
        } catch (e: Exception) {
            Log.e(tag, "Error getting message by id: $msgId", e)
            null
        }
    }
    
    /**
     * 通过消息ID从API获取单条消息详情（使用 list-message-by-mid-seq）
     */
    suspend fun getMessageByIdFromApi(messageId: String, chatId: String, chatType: Int, msgSeq: Long = -1L): ChatMessage? {
        val messages = getMessagesByMsgId(chatId, chatType, messageId, msgCount = 1, msgSeq = msgSeq).getOrNull()
        return messages?.find { it.msgId == messageId } ?: messages?.lastOrNull()
    }
    
    /**
     * 通过msgId获取消息及其前后文（使用 list-message-by-mid-seq API）
     * @param chatId 会话ID
     * @param chatType 会话类型
     * @param msgId 目标消息ID
     * @param msgCount 获取的消息数量（包含目标消息在内，实际返回 msgCount + 1 条）
     * @param msgSeq 消息序列号（默认为uint64最大值 -1L / 0xFFFFFFFFFFFFFFFFL）
     * @return 包含目标消息及其前后文的消息列表
     */
    suspend fun getMessagesByMsgId(
        chatId: String,
        chatType: Int,
        msgId: String,
        msgCount: Int = 30,
        msgSeq: Long = -1L
    ): Result<List<ChatMessage>> {
        return try {
            val tokenFlow = tokenRepository.getToken()
            val token = tokenFlow.first()?.token
            if (token.isNullOrEmpty()) {
                Log.e(tag, "❌ Token为空")
                return Result.failure(Exception("用户未登录"))
            }

            // Log.d(tag, "📡 调用 list-message-by-mid-seq: chatId=$chatId, chatType=$chatType, msgId=$msgId, msgCount=$msgCount, msgSeq=$msgSeq")
            
            val request = list_message_by_mid_seq_send.newBuilder()
                .setChatId(chatId)
                .setChatType(chatType.toLong())
                .setMsgId(msgId)
                .setMsgCount(msgCount.toLong())
                .setMsgSeq(msgSeq)
                .setUnknown(0)
                .build()
            
            val requestBody = request.toByteArray().toRequestBody("application/x-protobuf".toMediaType())
            val response = apiService.listMessageByMidSeq(token, requestBody)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val bytes = responseBody.bytes()
                    val messageResponse = list_message_by_mid_seq.parseFrom(bytes)
                    // Log.d(tag, "✅ list-message-by-mid-seq 返回: code=${messageResponse.status.code}, 消息数=${messageResponse.msgList.size}, total=${messageResponse.total}")
                    
                    if (messageResponse.status.code == 1) {
                        val messages = messageResponse.msgList.map { protoMsg ->
                            convertProtoToMessage(protoMsg, chatId, chatType)
                        }
                        // 缓存到本地数据库
                        cacheRepository.cacheMessages(messages)
                        Result.success(messages)
                    } else {
                        Log.e(tag, "❌ list-message-by-mid-seq 错误: ${messageResponse.status.msg}")
                        Result.failure(Exception(messageResponse.status.msg.ifBlank { "获取消息失败" }))
                    }
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Log.e(tag, "❌ list-message-by-mid-seq HTTP错误: ${response.code()}")
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ list-message-by-mid-seq 请求异常: $msgId", e)
            Result.failure(e)
        }
    }
    
    /**
     * 插入新消息到本地缓存
     */
    suspend fun insertMessage(message: ChatMessage) {
        try {
            cacheRepository.cacheMessages(listOf(message))
            // Log.d(tag, "Inserted message: ${message.msgId}")
        } catch (e: Exception) {
            Log.e(tag, "Error inserting message: ${message.msgId}", e)
        }
    }
    
    /**
     * 更新本地消息
     */
    suspend fun updateMessage(message: ChatMessage) {
        try {
            // 先删除旧消息，再插入新消息
            cacheRepository.deleteMessage(message.msgId)
            cacheRepository.cacheMessages(listOf(message))
            // Log.d(tag, "Updated message: ${message.msgId}")
        } catch (e: Exception) {
            Log.e(tag, "Error updating message: ${message.msgId}", e)
        }
    }
    
    /**
     * 删除本地消息
     */
    suspend fun deleteMessage(msgId: String) {
        try {
            cacheRepository.deleteMessage(msgId)
            // Log.d(tag, "Deleted message: $msgId")
        } catch (e: Exception) {
            Log.e(tag, "Error deleting message: $msgId", e)
        }
    }
    
    /**
     * 获取会话的最后一条消息
     */
    suspend fun getLastMessage(chatId: String, chatType: Int): ChatMessage? {
        return try {
            cacheRepository.getLastMessage(chatId, chatType)
        } catch (e: Exception) {
            Log.e(tag, "Error getting last message for chat: $chatId", e)
            null
        }
    }
    
    /**
     * 上报按钮点击事件
     */
    suspend fun reportButtonClick(
        chatId: String,
        chatType: Int,
        msgId: String,
        userId: String,
        buttonValue: String
    ): Result<Unit> {
        return try {
            val tokenFlow = tokenRepository.getToken()
            val token = tokenFlow.first()?.token
            if (token.isNullOrEmpty()) {
                Log.e(tag, "Token is null or empty")
                return Result.failure(Exception("用户未登录"))
            }

            // 构建protobuf请求
            val request = button_report_send.newBuilder()
                .setMsgId(msgId)
                .setChatType(chatType.toLong())
                .setChatId(chatId)
                .setUserId(userId)
                .setButtonValue(buttonValue)
                .build()
            
            val requestBody = request.toByteArray().toRequestBody("application/x-protobuf".toMediaType())

            val chatTypeText = when (chatType) {
                1 -> "私聊用户"
                2 -> "群聊"
                3 -> "机器人"
                else -> "未知类型($chatType)"
            }
            // Log.d(tag, "Reporting button click: chatType=$chatTypeText, chatId=$chatId, msgId=$msgId, value=$buttonValue")
            
            val response = apiService.buttonReport(token, requestBody)
            
            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val bytes = responseBody.bytes()
                    val buttonResponse = recall_msg.parseFrom(bytes) // 使用 recall_msg 解析（只有status）
                    
                    if (buttonResponse.status.code == 1) {
                        // Log.d(tag, "Button click reported successfully")
                        Result.success(Unit)
                    } else {
                        Log.e(tag, "API error: ${buttonResponse.status.msg}")
                        Result.failure(Exception(buttonResponse.status.msg))
                    }
                } ?: Result.failure(Exception("响应体为空"))
            } else {
                Log.e(tag, "HTTP error: ${response.code()}")
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error reporting button click", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取消息编辑历史记录
     */
    /**
     * 添加表情到个人收藏
     */
    suspend fun addExpressionToFavorites(expressionId: String): Result<Unit> {
        return try {
            val tokenFlow = tokenRepository.getToken()
            val token = tokenFlow.first()?.token
            if (token.isNullOrEmpty()) {
                Log.e(tag, "Token is null or empty")
                return Result.failure(Exception("用户未登录"))
            }
            
            // 将String转换为Long
            val expressionIdLong = expressionId.toLongOrNull() ?: return Result.failure(Exception("无效的表情ID"))
            val request = AddExpressionRequest(id = expressionIdLong)
            
            // Log.d(tag, "Adding expression to favorites: $expressionId")
            
            val response = apiService.addExpression(token, request)
            
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    val message = body.message
                    if (body.code == 1) {
                        // Log.d(tag, "Successfully added expression: $expressionId")
                        Result.success(Unit)
                    } else {
                        Log.e(tag, "Failed to add expression: $message")
                        Result.failure(Exception(message))
                    }
                } ?: Result.failure(Exception("响应体为空"))
            } else {
                Log.e(tag, "Failed to add expression: ${response.message()}")
                Result.failure(Exception("添加表情失败: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error adding expression", e)
            Result.failure(e)
        }
    }
    
    suspend fun getMessageEditHistory(msgId: String, size: Int = 10, page: Int = 1): Result<List<MessageEditRecord>> {
        return try {
            val tokenFlow = tokenRepository.getToken()
            val token = tokenFlow.first()?.token
            if (token.isNullOrEmpty()) {
                Log.e(tag, "Token is null or empty")
                return Result.failure(Exception("用户未登录"))
            }
            
            val request = com.yhchat.canary.data.api.ListMessageEditRecordRequest(
                msgId = msgId,
                size = size,
                page = page
            )
            
            // Log.d(tag, "Getting edit history for message: $msgId")
            
            val response = apiService.listMessageEditRecord(token, request)
            
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    val code = body["code"] as? Double
                    val msg = body["msg"] as? String ?: "Unknown error"
                    if (code == 1.0) {
                        val data = body["data"] as? Map<*, *>
                        val list = data?.get("list") as? List<*>
                        
                        val records = list?.mapNotNull { item ->
                            val record = item as? Map<*, *> ?: return@mapNotNull null
                            MessageEditRecord(
                                id = (record["id"] as? Double)?.toLong() ?: 0L,
                                msgId = record["msgId"] as? String ?: "",
                                contentType = (record["contentType"] as? Double)?.toInt() ?: 1,
                                contentOld = record["contentOld"] as? String ?: "",
                                createTime = (record["createTime"] as? Double)?.toLong() ?: 0L,
                                msgTime = (record["msgTime"] as? Double)?.toLong() ?: 0L
                            )
                        } ?: emptyList()
                        
                        // Log.d(tag, "Successfully got ${records.size} edit records")
                        Result.success(records)
                    } else {
                        Log.e(tag, "API error: $msg")
                        Result.failure(Exception(msg))
                    }
                } ?: Result.failure(Exception("响应体为空"))
            } else {
                Log.e(tag, "HTTP error: ${response.code()}")
                Result.failure(Exception("网络请求失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting edit history", e)
            Result.failure(e)
        }
    }
}
