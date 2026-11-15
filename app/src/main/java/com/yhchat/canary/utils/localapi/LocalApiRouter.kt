package com.yhchat.canary.utils.localapi

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.yhchat.canary.data.di.RepositoryFactory
import com.yhchat.canary.data.model.Conversation
import com.yhchat.canary.proto.user.get_user_send
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 本地 API 的路由与实现。
 *
 * ## 为什么要做这一层
 *
 * 云湖的服务端接口是 protobuf 的，agent / 脚本直接调要自己实现 protobuf 编解码，
 * 成本太高。这里把同一个 App 已经封装好的 Repository 暴露成 JSON HTTP 接口，
 * 让本机上的 agent 用最简单的 form 请求就能拿到数据。
 *
 * ## 设计约定（都为了「方便 agent 调用」）
 *
 *   - **不需要 token**：服务只监听本机，鉴权靠「谁能在本机跑」这道边界；
 *   - **请求体简洁**：参数走 query string，GET 就能调，不用构造 JSON body；
 *   - **响应统一信封**：`{"ok":true,"data":...}` / `{"ok":false,"error":"..."}`，
 *     调用方只需要判 `ok`；
 *   - **失败也给 200**：业务错误不占用 HTTP 状态码，避免 agent 把 500 当成网络故障重试。
 *     真正的协议级错误（路径不存在）才用 404。
 */
class LocalApiRouter(private val context: Context) {

    private val gson = Gson()
    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "LocalApiRouter"

        /** 单次查询上限，防止 agent 一次拉太多把内存打爆 */
        private const val MAX_PAGE_SIZE = 100
        private const val DEFAULT_PAGE_SIZE = 20

        /** 对外接口清单，`/` 直接返回，方便 agent 自动发现 */
        val ENDPOINTS = listOf(
            EndpointInfo("GET", "/", "接口清单", "本页"),
            EndpointInfo("GET", "/conversations", "会话列表", "limit=20"),
            EndpointInfo("GET", "/messages", "消息列表", "chatId=xxx&chatType=2&limit=20&msgId=（可选，用于翻页）"),
            EndpointInfo("GET", "/user", "用户详情", "userId=xxx"),
            EndpointInfo("GET", "/group", "群聊详情", "groupId=xxx"),
            EndpointInfo("GET", "/bot", "机器人详情", "botId=xxx"),
            EndpointInfo("GET", "/post", "文章详情", "postId=123"),
            EndpointInfo("GET", "/sticker-pack", "表情包详情", "packId=123"),
            EndpointInfo("GET", "/devices", "已知设备", "无参数"),
            EndpointInfo("POST", "/send", "发送消息", "chatId=xxx&chatType=2&text=hello&contentType=1")
        )
    }

    data class EndpointInfo(
        val method: String,
        val path: String,
        val title: String,
        val params: String
    )

    /**
     * 路由分发。
     *
     * 这里是 suspend 的，由 [LocalHttpServer] 在 IO 协程里调用，
     * 不会碰到主线程。
     */
    suspend fun route(request: HttpRequest): HttpResponse {
        // 预检
        if (request.method == "OPTIONS") {
            return HttpResponse.json("""{"ok":true}""")
        }

        return try {
            when (request.path) {
                "/", "/endpoints" -> listEndpoints()
                "/conversations" -> getConversations(request)
                "/messages" -> getMessages(request)
                "/user" -> getUser(request)
                "/group" -> getGroup(request)
                "/bot" -> getBot(request)
                "/post" -> getPost(request)
                "/sticker-pack" -> getStickerPack(request)
                "/devices" -> getDevices()
                "/send" -> sendMessage(request)
                else -> HttpResponse.notFound("unknown endpoint: ${request.path}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "路由失败: ${request.path}", e)
            fail(e.message ?: "internal error")
        }
    }

    // ------------------------------------------------------------------
    // 各端点
    // ------------------------------------------------------------------

    private fun listEndpoints(): HttpResponse {
        val data = ENDPOINTS.map { ep ->
            mapOf(
                "method" to ep.method,
                "path" to ep.path,
                "title" to ep.title,
                "params" to ep.params
            )
        }
        return ok(mapOf("endpoints" to data))
    }

    private suspend fun getConversations(request: HttpRequest): HttpResponse {
        val limit = request.limit()
        val conversationRepository = RepositoryFactory.getConversationRepository(appContext)
        val result = conversationRepository.getConversations()
        val list = result.getOrElse { return fail(it.message ?: "获取会话列表失败") }

        val data = list.take(limit).map { it.toApiJson() }
        return ok(mapOf("total" to list.size, "count" to data.size, "items" to data))
    }

    private suspend fun getMessages(request: HttpRequest): HttpResponse {
        val chatId = request.param("chatId") ?: request.param("chat_id")
            ?: return fail("缺少 chatId")
        // chatType 缺省按群聊算；用户/机器人的会话也用同一个接口
        val chatType = (request.param("chatType") ?: request.param("chat_type"))?.toIntOrNull() ?: 2
        val limit = request.limit()
        val msgId = request.param("msgId") ?: request.param("msg_id")

        val messageRepository = RepositoryFactory.getMessageRepository(appContext)
        val result = messageRepository.getMessages(
            chatId = chatId,
            chatType = chatType,
            msgCount = limit,
            msgId = msgId
        ).getOrElse { return fail(it.message ?: "获取消息失败") }

        val items = result.map { msg ->
            mapOf(
                "msgId" to msg.msgId,
                "senderId" to msg.sender.chatId,
                "senderName" to msg.sender.name,
                "senderType" to msg.sender.chatType,
                "contentType" to msg.contentType,
                "text" to (msg.content.text ?: ""),
                "imageUrl" to (msg.content.imageUrl ?: ""),
                "fileUrl" to (msg.content.fileUrl ?: ""),
                "fileName" to (msg.content.fileName ?: ""),
                "sendTime" to msg.sendTime,
                "quoteMsgId" to (msg.quoteMsgId ?: "")
            )
        }
        return ok(mapOf("count" to items.size, "items" to items))
    }

    private suspend fun getUser(request: HttpRequest): HttpResponse {
        val userId = request.param("userId") ?: request.param("user_id")
            ?: return fail("缺少 userId")

        val tokenRepository = RepositoryFactory.getTokenRepository(appContext)
        val token = tokenRepository.getTokenSync() ?: return fail("应用未登录，无法代理该请求")
        val userRepository = RepositoryFactory.getUserRepository(appContext)
        userRepository.setTokenRepository(tokenRepository)

        val proto = get_user_send.newBuilder().setId(userId).build()
        val body = proto.toByteArray()
            .toRequestBody("application/x-protobuf".toMediaTypeOrNull())

        val detail = userRepository.getUserDetail(token, body)
            .getOrElse { return fail(it.message ?: "获取用户详情失败") }

        return ok(
            mapOf(
                "userId" to detail.id,
                "name" to detail.name,
                "avatarUrl" to detail.avatarUrl,
                "isVip" to detail.isVip,
                "registerTime" to detail.registerTime,
                "onlineDay" to detail.onlineDay,
                "ipGeo" to (detail.ipGeo ?: ""),
                "medals" to (detail.medalList ?: emptyList<Any>()).map { it.name }
            )
        )
    }

    private suspend fun getGroup(request: HttpRequest): HttpResponse {
        val groupId = request.param("groupId") ?: request.param("group_id")
            ?: return fail("缺少 groupId")

        val groupRepository = RepositoryFactory.getGroupRepository(appContext)
        val detail = groupRepository.getGroupInfo(groupId)
            .getOrElse { return fail(it.message ?: "获取群详情失败") }

        return ok(
            mapOf(
                "groupId" to detail.groupId,
                "name" to detail.name,
                "avatarUrl" to detail.avatarUrl,
                "introduction" to detail.introduction,
                "memberCount" to detail.memberCount,
                "ownerId" to detail.ownerId,
                "adminIds" to detail.adminIds,
                "categoryName" to detail.categoryName,
                "isPrivate" to detail.isPrivate,
                "communityName" to (detail.communityName ?: "")
            )
        )
    }

    private suspend fun getBot(request: HttpRequest): HttpResponse {
        val botId = request.param("botId") ?: request.param("bot_id")
            ?: return fail("缺少 botId")

        val botRepository = RepositoryFactory.getBotRepository(appContext)
        val info = botRepository.getBotInfoSimple(botId)
            .getOrElse { return fail(it.message ?: "获取机器人详情失败") }

        return ok(
            mapOf(
                "botId" to info.botId,
                // BotInfo 里的显示名字段叫 nickname，不是 name
                "name" to info.nickname,
                "avatarUrl" to info.avatarUrl,
                "introduction" to info.introduction
            )
        )
    }

    private suspend fun getPost(request: HttpRequest): HttpResponse {
        val postId = request.param("postId")?.toIntOrNull()
            ?: request.param("post_id")?.toIntOrNull()
            ?: return fail("缺少 postId（应为数字）")

        val tokenRepository = RepositoryFactory.getTokenRepository(appContext)
        val token = tokenRepository.getTokenSync() ?: return fail("应用未登录，无法代理该请求")
        val communityRepository = RepositoryFactory.getCommunityRepository(appContext)

        val response = communityRepository.getPostDetail(token, postId)
            .getOrElse { return fail(it.message ?: "获取文章失败") }
        if (response.code != 1) return fail(response.msg.ifBlank { "获取文章失败" })

        val post = response.data.post
        return ok(
            mapOf(
                // 注意 CommunityPost 的主键字段叫 id，不是 postId
                "postId" to post.id,
                "title" to post.title,
                "content" to post.content,
                "contentType" to post.contentType,
                "authorId" to post.senderId,
                "boardId" to response.data.board.id,
                "boardName" to response.data.board.name
            )
        )
    }

    private suspend fun getStickerPack(request: HttpRequest): HttpResponse {
        val packId = request.param("packId")?.toLongOrNull()
            ?: request.param("pack_id")?.toLongOrNull()
            ?: return fail("缺少 packId（应为数字）")

        val stickerRepository = RepositoryFactory.getStickerRepository(appContext)
        val detail = stickerRepository.getStickerPackDetail(packId)
            .getOrElse { return fail(it.message ?: "获取表情包失败") }

        val pack = detail.stickerPack
        return ok(
            mapOf(
                "packId" to pack.id,
                "name" to pack.name,
                "createBy" to pack.createBy,
                "createTime" to pack.createTime,
                // 作者信息在 user 字段里，可能为空；字段名是 nickname
                "authorName" to (detail.user?.nickname ?: ""),
                "authorId" to (detail.user?.userId ?: "")
            )
        )
    }

    private suspend fun getDevices(): HttpResponse {
        val knownDeviceDao = com.yhchat.canary.data.local.GlobalDatabase
            .getDatabase(appContext).knownDeviceDao()
        val tokenRepository = RepositoryFactory.getTokenRepository(appContext)
        val userId = tokenRepository.getUserIdSync().orEmpty()
        if (userId.isBlank()) return fail("应用未登录")

        val devices = knownDeviceDao.getByUser(userId)
        return ok(
            mapOf(
                "count" to devices.size,
                "items" to devices.map { d ->
                    mapOf(
                        "deviceId" to d.deviceId,
                        "platform" to d.platform,
                        "loginTimestamp" to d.loginTimestamp,
                        "offlineByUser" to d.offlineByUser
                    )
                }
            )
        )
    }

    /**
     * 发送消息。
     *
     * 用 POST，但参数仍然走 query —— agent 不用构造 body。
     * 也接受 body 里带 JSON，两者都支持，谁方便用谁。
     */
    private suspend fun sendMessage(request: HttpRequest): HttpResponse {
        if (request.method != "POST") {
            return HttpResponse.error(405, "请使用 POST")
        }

        // 同时支持 query 和 JSON body
        val fromQuery = request.query
        val fromBody: Map<String, String> = if (request.body.isNotBlank()) {
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val map = gson.fromJson(request.body, Map::class.java) as? Map<String, Any?> ?: emptyMap()
                map.mapValues { (_, v) -> v?.toString().orEmpty() }
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        fun pick(vararg keys: String): String? {
            keys.forEach { key ->
                fromQuery[key]?.takeIf { it.isNotBlank() }?.let { return it }
                fromBody[key]?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return null
        }

        val chatId = pick("chatId", "chat_id") ?: return fail("缺少 chatId")
        val chatType = pick("chatType", "chat_type")?.toIntOrNull() ?: 2
        val text = pick("text", "content") ?: return fail("缺少 text")
        val contentType = pick("contentType", "content_type")?.toIntOrNull() ?: 1
        val quoteMsgId = pick("quoteMsgId", "quote_msg_id")

        val messageRepository = RepositoryFactory.getMessageRepository(appContext)
        messageRepository.sendMessage(
            chatId = chatId,
            chatType = chatType,
            text = text,
            contentType = contentType,
            quoteMsgId = quoteMsgId
        ).getOrElse { return fail(it.message ?: "发送失败") }

        return ok(mapOf("sent" to true, "chatId" to chatId, "chatType" to chatType))
    }

    // ------------------------------------------------------------------
    // 序列化辅助
    // ------------------------------------------------------------------

    private fun Conversation.toApiJson(): Map<String, Any?> = mapOf(
        "chatId" to chatId,
        "chatType" to chatType,
        "name" to name,
        "avatarUrl" to (avatarUrl ?: ""),
        "lastMessage" to chatContent,
        "timestamp" to timestampMs,
        "unreadCount" to unreadMessage,
        "doNotDisturb" to (doNotDisturb == 1),
        "isTop" to (timestamp != 0L && at != 0L)
    )

    private fun ok(data: Any?): HttpResponse {
        val json = gson.toJson(mapOf("ok" to true, "data" to data))
        return HttpResponse.json(json)
    }

    private fun fail(message: String): HttpResponse {
        val json = gson.toJson(mapOf("ok" to false, "error" to message))
        return HttpResponse.json(json)
    }

    /** 读取 limit 参数并夹到合理范围 */
    private fun HttpRequest.limit(): Int {
        val raw = param("limit")?.toIntOrNull() ?: DEFAULT_PAGE_SIZE
        return raw.coerceIn(1, MAX_PAGE_SIZE)
    }
}
