package com.yhchat.canary.data.websocket

import android.util.Log
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.model.MessageCmd
import com.yhchat.canary.data.model.MessageContent
import com.yhchat.canary.data.model.MessageSender
import com.yhchat.canary.data.model.MessageTag
import com.yhchat.canary.proto.chat_ws_go.WsMsg
import com.yhchat.canary.proto.chat_ws_go.bot_board_message
import com.yhchat.canary.proto.chat_ws_go.draft_input
import com.yhchat.canary.proto.chat_ws_go.edit_message
import com.yhchat.canary.proto.chat_ws_go.file_send_message
import com.yhchat.canary.proto.chat_ws_go.heartbeat_ack
import com.yhchat.canary.proto.chat_ws_go.push_message
import com.yhchat.canary.proto.chat_ws_go.stream_message

/**
 * WebSocket消息解析器 - 参考yh_user_sdk/core/ws.py的decode方法
 */
object WebSocketMessageParser {
    
    private const val TAG = "WebSocketMessageParser"
    
    /**
     * 解析WebSocket二进制消息 - 参考yh_user_sdk/core/ws.py
     */
    fun parseWebSocketMessage(bytes: ByteArray): ParsedMessage? {
        return try {
            // 先尝试解析为heartbeat_ack获取命令类型和seq，参考yh_user_sdk
            val tempMsg = heartbeat_ack.parseFrom(bytes)
            val cmd = tempMsg.info.cmd
            val seq = tempMsg.info.seq

            Log.d(TAG, "Parsing message - cmd: $cmd, seq: $seq")

            when (cmd) {
                "heartbeat_ack" -> {
                    ParsedMessage.HeartbeatAck(seq)
                }
                
                "push_message" -> {
                    val pushMessage = push_message.parseFrom(bytes)
                    if (pushMessage.hasData() && pushMessage.data.hasMsg()) {
                        val message = convertProtoToMessage(pushMessage.data.msg)
                        ParsedMessage.NewMessage(message, seq)
                    } else {
                        Log.w(TAG, "push_message has no data or msg")
                        null
                    }
                }
                
                "edit_message" -> {
                    val editMessage = edit_message.parseFrom(bytes)
                    if (editMessage.hasData() && editMessage.data.hasMsg()) {
                        val message = convertProtoToMessage(editMessage.data.msg)
                        ParsedMessage.EditedMessage(message, seq)
                    } else {
                        Log.w(TAG, "edit_message has no data or msg")
                        null
                    }
                }
                
                "draft_input" -> {
                    val draftInput = draft_input.parseFrom(bytes)
                    if (draftInput.hasData() && draftInput.data.hasDraft()) {
                        val draft = draftInput.data.draft
                        ParsedMessage.DraftInput(
                            chatId = draft.chatId,
                            input = draft.input,
                            seq = seq
                        )
                    } else {
                        Log.w(TAG, "draft_input has no data or draft")
                        null
                    }
                }
                
                "file_send_message" -> {
                    val fileSendMessage = file_send_message.parseFrom(bytes)
                    if (fileSendMessage.hasData() && fileSendMessage.data.hasSender()) {
                        val sender = fileSendMessage.data.sender
                        ParsedMessage.FileSendMessage(
                            sendUserId = sender.sendUserId,
                            userId = sender.userId,
                            sendType = sender.sendType,
                            data = sender.data,
                            sendDeviceId = sender.sendDeviceId,
                            seq = seq
                        )
                    } else {
                        Log.w(TAG, "file_send_message has no data or sender")
                        null
                    }
                }
                
                "bot_board_message" -> {
                    val botBoardMessage = bot_board_message.parseFrom(bytes)
                    if (botBoardMessage.hasData() && botBoardMessage.data.hasBoard()) {
                        val board = botBoardMessage.data.board
                        ParsedMessage.BotBoardMessage(
                            botId = board.botId,
                            chatId = board.chatId,
                            chatType = board.chatType,
                            content = board.content,
                            contentType = board.contentType,
                            lastUpdateTime = board.lastUpdateTime,
                            botName = board.botName,
                            seq = seq
                        )
                    } else {
                        Log.w(TAG, "bot_board_message has no data or board")
                        null
                    }
                }
                
                "stream_message" -> {
                    val streamMsg = stream_message.parseFrom(bytes)
                    if (streamMsg.hasData() && streamMsg.data.hasMsg()) {
                        val msg = streamMsg.data.msg
                        ParsedMessage.StreamMessage(
                            msgId = msg.msgId,
                            recvId = msg.recvId,
                            chatId = msg.chatId,
                            content = msg.content,
                            seq = seq
                        )
                    } else {
                        Log.w(TAG, "stream_message has no data or msg")
                        null
                    }
                }
                
                else -> {
                    Log.w(TAG, "Unknown command: $cmd")
                    ParsedMessage.Unknown(cmd, seq)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WebSocket message", e)
            null
        }
    }
    
    /**
     * 将ProtoBuf消息转换为ChatMessage对象
     */
    private fun convertProtoToMessage(protoMsg: WsMsg): ChatMessage {
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
                type = protoMsg.cmd.id.toInt()
            )
        } else null

        return ChatMessage(
            msgId = protoMsg.msgId,
            sender = sender,
            direction = "left", // WebSocket消息通常是接收的消息
            contentType = protoMsg.contentType,
            content = content,
            sendTime = protoMsg.timestamp,
            cmd = cmd,
            msgDeleteTime = if (protoMsg.deleteTime > 0) protoMsg.deleteTime else null,
            quoteMsgId = if (protoMsg.quoteMsgId.isNotEmpty()) protoMsg.quoteMsgId else null,
            msgSeq = protoMsg.msgSeq,
            editTime = if (protoMsg.editTime > 0) protoMsg.editTime else null,
            // 关键修复：使用protoMsg的chatId和chatType，而不是sender的
            chatId = protoMsg.chatId,
            chatType = protoMsg.chatType,
            recvId = protoMsg.recvId
        )
    }
}

/**
 * 解析后的消息类型
 */
sealed class ParsedMessage {
    data class HeartbeatAck(val seq: String) : ParsedMessage()
    
    data class NewMessage(
        val message: ChatMessage,
        val seq: String
    ) : ParsedMessage()
    
    data class EditedMessage(
        val message: ChatMessage,
        val seq: String
    ) : ParsedMessage()
    
    data class DraftInput(
        val chatId: String,
        val input: String,
        val seq: String
    ) : ParsedMessage()
    
    data class FileSendMessage(
        val sendUserId: String,
        val userId: String,
        val sendType: String,
        val data: String,
        val sendDeviceId: String,
        val seq: String
    ) : ParsedMessage()
    
    data class BotBoardMessage(
        val botId: String,
        val chatId: String,
        val chatType: Int,
        val content: String,
        val contentType: Int,
        val lastUpdateTime: Long,
        val botName: String,
        val seq: String
    ) : ParsedMessage()
    
    data class StreamMessage(
        val msgId: String,
        val recvId: String,
        val chatId: String,
        val content: String,
        val seq: String
    ) : ParsedMessage()
    
    data class Unknown(
        val cmd: String,
        val seq: String
    ) : ParsedMessage()
}