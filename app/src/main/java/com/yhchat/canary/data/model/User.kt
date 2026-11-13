package com.yhchat.canary.data.model

import com.google.gson.annotations.SerializedName

/**
 * 用户信息数据模型
 */
data class User(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    
    @SerializedName("avatar_id")
    val avatarId: Long? = null,
    
    @SerializedName("chat_id")
    val chatId: String,
    
    @SerializedName("chat_type")
    val chatType: Int, // 1-用户，2-群聊，3-机器人
    
    @SerializedName("tag")
    val tags: List<Tag>? = null,
    
    @SerializedName("certification_level")
    val certificationLevel: Int? = null // 1-官方，2-地区
)

/**
 * 用户标签
 */
data class Tag(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("text")
    val text: String,
    
    @SerializedName("color")
    val color: String
)

/**
 * 登录请求
 */
data class LoginRequest(
    @SerializedName("mobile")
    val mobile: String? = null,
    
    @SerializedName("captcha")
    val captcha: String? = null,
    
    @SerializedName("email")
    val email: String? = null,
    
    @SerializedName("password")
    val password: String? = null,
    
    @SerializedName("deviceId")
    val deviceId: String,
    
    @SerializedName("platform")
    val platform: String = "android"
)

/**
 * 基础响应
 */
data class BaseResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("msg")
    val message: String
)

/**
 * 登录响应
 */
data class LoginResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("msg")
    val message: String,
    
    @SerializedName("data")
    val data: LoginData? = null
)

/**
 * 登录数据
 */
data class LoginData(
    @SerializedName("token")
    val token: String
)

/**
 * 验证码响应
 */
data class CaptchaResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("msg")
    val message: String,
    
    @SerializedName("data")
    val data: CaptchaData? = null
)

/**
 * 验证码数据
 */
data class CaptchaData(
    @SerializedName("b64s")
    val b64s: String,

    @SerializedName("id")
    val id: String
)

/**
 * 滑块验证码响应（POST v1/captcha/get）
 *
 * 和上面的图形验证码不是同一个接口：图形码直接返回一张带字符的图，
 * 滑块码返回一张底图 + 一张拼图碎片，需要用户把碎片拖到缺口位置，
 * 后端只认最终的 x 坐标。
 */
data class SlideCaptchaResponse(
    @SerializedName("code")
    val code: Int,

    @SerializedName("msg")
    val message: String? = null,

    @SerializedName("data")
    val data: SlideCaptchaData? = null
)

/**
 * 滑块验证码数据
 *
 * master_image / tile_image 都是完整的 data URI（`data:image/jpeg;base64,...`），
 * 用之前要先截掉逗号前的部分再解码。
 */
data class SlideCaptchaData(
    @SerializedName("id")
    val id: String,

    @SerializedName("master_image")
    val masterImage: String,

    @SerializedName("master_size")
    val masterSize: SlideImageSize? = null,

    @SerializedName("tile_image")
    val tileImage: String,

    @SerializedName("tile_size")
    val tileSize: SlideImageSize? = null,

    /** 碎片在底图上的纵向位置（底图像素坐标，不是 dp） */
    @SerializedName("tile_y")
    val tileY: Int = 0,

    @SerializedName("type")
    val type: String? = null
)

/**
 * 图片尺寸。后端用的是底图像素坐标，缩放时要按这里的宽高换算。
 */
data class SlideImageSize(
    @SerializedName("width")
    val width: Int,

    @SerializedName("height")
    val height: Int
)

/**
 * 滑块验证码方式获取短信验证码的请求体
 *
 * 注意和 [SmsCaptchaRequest] 的区别：这个没有 `code`（用户没输字符），
 * 换成 `captcha_type` + `x`。x 是碎片在底图上的最终横坐标。
 */
data class SlideSmsCaptchaRequest(
    @SerializedName("mobile")
    val mobile: String,

    @SerializedName("platform")
    val platform: String,

    @SerializedName("captcha_type")
    val captchaType: String = "slide",

    @SerializedName("id")
    val id: String,

    @SerializedName("x")
    val x: Int
)

/**
 * 获取短信验证码请求
 */
data class SmsCaptchaRequest(
    @SerializedName("mobile")
    val mobile: String,

    @SerializedName("code")
    val code: String,

    @SerializedName("id")
    val id: String
)

/**
 * 搜索请求
 */
data class SearchRequest(
    @SerializedName("word")
    val word: String
)

/**
 * 搜索响应
 */
data class SearchResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: SearchData,
    
    @SerializedName("msg")
    val msg: String
)

/**
 * 搜索数据
 */
data class SearchData(
    @SerializedName("list")
    val list: List<SearchCategory>
)

/**
 * 搜索类别
 */
data class SearchCategory(
    @SerializedName("title")
    val title: String,

    @SerializedName("list")
    val list: List<SearchItem>? = null
)

/**
 * 搜索项
 */
data class SearchItem(
    @SerializedName("friendId")
    val friendId: String,

    @SerializedName("friendType")
    val friendType: Int,

    @SerializedName("nickname")
    val nickname: String,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("avatarUrl")
    val avatarUrl: String,

    @SerializedName("hit")
    val hit: Int
)

/**
 * 置顶会话响应
 */
data class StickyResponse(
    @SerializedName("code")
    val code: Int,

    @SerializedName("msg")
    val message: String,

    @SerializedName("data")
    val data: StickyData? = null
)

/**
 * 置顶会话数据
 */
data class StickyData(
    @SerializedName("sticky")
    val sticky: List<StickyItem>
)

/**
 * 置顶会话项
 */
data class StickyItem(
    @SerializedName("id")
    val id: Int,

    @SerializedName("chatType")
    val chatType: Int,

    @SerializedName("chatId")
    val chatId: String,

    @SerializedName("chatName")
    val chatName: String,

    @SerializedName("sort")
    val sort: Long,

    @SerializedName("avatarUrl")
    val avatarUrl: String,

    @SerializedName("createTime")
    val createTime: Long,

    @SerializedName("delFlag")
    val delFlag: Int,

    @SerializedName("userId")
    val userId: String,

    @SerializedName("certificationLevel")
    val certificationLevel: Int
)

/**
 * 置顶会话操作请求
 */
data class StickyOperationRequest(
    @SerializedName("chatId")
    val chatId: String,

    @SerializedName("chatType")
    val chatType: Int
)

/**
 * 置顶会话置顶请求
 */
data class StickyTopRequest(
    @SerializedName("id")
    val id: Int
)

// ========== 聊天消息相关数据模型 ==========

/**
 * 消息列表响应
 */
data class MessageListResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("msg")
    val message: String,
    @SerializedName("data")
    val data: MessageListData? = null
)

/**
 * 消息列表数据
 */
data class MessageListData(
    @SerializedName("status")
    val status: MessageStatus? = null,
    @SerializedName("msg")
    val messages: List<ChatMessage>? = null,
    @SerializedName("total")
    val total: Int? = null
)

/**
 * 消息状态
 */
data class MessageStatus(
    @SerializedName("number")
    val number: Long? = null,
    @SerializedName("code")
    val code: Int,
    @SerializedName("msg")
    val message: String
)

/**
 * 聊天消息
 */
data class ChatMessage(
    @SerializedName("msg_id")
    val msgId: String,
    @SerializedName("sender")
    val sender: MessageSender,
    @SerializedName("direction")
    val direction: String, // left/right
    @SerializedName("content_type")
    val contentType: Int,
    @SerializedName("content")
    val content: MessageContent,
    @SerializedName("send_time")
    val sendTime: Long, // 毫秒时间戳
    @SerializedName("cmd")
    val cmd: MessageCmd? = null,
    @SerializedName("msg_delete_time")
    val msgDeleteTime: Long? = null,
    @SerializedName("quote_msg_id")
    val quoteMsgId: String? = null,
    @SerializedName("msg_seq")
    val msgSeq: Long? = null,
    @SerializedName("edit_time")
    val editTime: Long? = null,
    // 添加会话信息字段，用于确定消息属于哪个会话
    @SerializedName("chat_id")
    val chatId: String? = null,
    @SerializedName("chat_type")
    val chatType: Int? = null,
    @SerializedName("recv_id") 
    val recvId: String? = null
)

/**
 * 消息发送者
 */
data class MessageSender(
    @SerializedName("chat_id")
    val chatId: String,
    @SerializedName("chat_type")
    val chatType: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("avatar_url")
    val avatarUrl: String,
    @SerializedName("tag_old")
    val tagOld: List<String>? = null,
    @SerializedName("tag")
    val tag: List<MessageTag>? = null
)

/**
 * 消息标签
 */
data class MessageTag(
    @SerializedName("id")
    val id: Long,
    @SerializedName("text")
    val text: String,
    @SerializedName("color")
    val color: String
)

/**
 * 消息内容
 */
data class MessageContent(
    @SerializedName("text")
    val text: String? = null,
    @SerializedName("buttons")
    val buttons: String? = null,
    @SerializedName("image_url")
    val imageUrl: String? = null,
    @SerializedName("file_name")
    val fileName: String? = null,
    @SerializedName("file_url")
    val fileUrl: String? = null,
    @SerializedName("form")
    val form: String? = null,
    @SerializedName("quote_msg_text")
    val quoteMsgText: String? = null,
    @SerializedName("quote_image_url")
    val quoteImageUrl: String? = null,
    @SerializedName("quote_image_name")
    val quoteImageName: String? = null,
    @SerializedName("quote_video_url")
    val quoteVideoUrl: String? = null,
    @SerializedName("quote_video_time")
    val quoteVideoTime: Long? = null,
    @SerializedName("sticker_url")
    val stickerUrl: String? = null,
    @SerializedName("post_id")
    val postId: String? = null,
    @SerializedName("post_title")
    val postTitle: String? = null,
    @SerializedName("post_content")
    val postContent: String? = null,
    @SerializedName("post_content_type")
    val postContentType: String? = null,
    @SerializedName("expression_id")
    val expressionId: String? = null,
    @SerializedName("file_size")
    val fileSize: Long? = null,
    @SerializedName("video_url")
    val videoUrl: String? = null,
    @SerializedName("audio_url")
    val audioUrl: String? = null,
    @SerializedName("audio_time")
    val audioTime: Long? = null,
    @SerializedName("sticker_item_id")
    val stickerItemId: Long? = null,
    @SerializedName("sticker_pack_id")
    val stickerPackId: Long? = null,
    @SerializedName("call_text")
    val callText: String? = null,
    @SerializedName("call_status_text")
    val callStatusText: String? = null,
    @SerializedName("width")
    val width: Long? = null,
    @SerializedName("height")
    val height: Long? = null
)

/**
 * 消息命令
 */
data class MessageCmd(
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: Int
)

/**
 * 获取消息列表请求（通过序列）
 */
data class ListMessageBySeqRequest(
    @SerializedName("msg_seq")
    val msgSeq: Long,
    @SerializedName("chat_type") 
    val chatType: Int,
    @SerializedName("chat_id")
    val chatId: String
)

/**
 * 消息编辑记录
 */
data class MessageEditRecord(
    val id: Long,
    val msgId: String,
    val contentType: Int,
    val contentOld: String, // JSON格式的旧内容
    val createTime: Long, // 创建时间戳（毫秒）
    val msgTime: Long // 编辑时间戳（毫秒）
)

/**
 * 获取消息列表请求
 */
data class ListMessageRequest(
    @SerializedName("msg_count")
    val msgCount: Int,
    @SerializedName("msg_id")
    val msgId: String? = null,
    @SerializedName("chat_type")
    val chatType: Int,
    @SerializedName("chat_id")
    val chatId: String
)

/**
 * 发送消息请求
 */
data class SendMessageRequest(
    @SerializedName("msg_id")
    val msgId: String,
    @SerializedName("chat_id")
    val chatId: String,
    @SerializedName("chat_type")
    val chatType: Int,
    @SerializedName("content")
    val content: SendMessageContent,
    @SerializedName("content_type")
    val contentType: Int,
    @SerializedName("command_id")
    val commandId: Long? = null,
    @SerializedName("quote_msg_id")
    val quoteMsgId: String? = null,
    @SerializedName("media")
    val media: MessageMedia? = null
)

/**
 * 发送消息内容
 */
data class SendMessageContent(
    @SerializedName("text")
    val text: String? = null,
    @SerializedName("buttons")
    val buttons: String? = null,
    @SerializedName("file_name")
    val fileName: String? = null,
    @SerializedName("file")
    val file: String? = null,
    @SerializedName("mentioned_id")
    val mentionedId: List<String>? = null,
    @SerializedName("form")
    val form: String? = null,
    @SerializedName("quote_msg_text")
    val quoteMsgText: String? = null,
    @SerializedName("image")
    val image: String? = null,
    @SerializedName("msg_text1")
    val msgText1: String? = null,
    @SerializedName("msg_text2")
    val msgText2: String? = null,
    @SerializedName("expression_id")
    val expressionId: String? = null,
    @SerializedName("file_size")
    val fileSize: Long? = null,
    @SerializedName("video")
    val video: String? = null,
    @SerializedName("audio")
    val audio: String? = null,
    @SerializedName("audio_time")
    val audioTime: Long? = null,
    @SerializedName("sticker_item_id")
    val stickerItemId: Long? = null,
    @SerializedName("sticker_pack_id")
    val stickerPackId: Long? = null,
    @SerializedName("room_name")
    val roomName: String? = null
)

/**
 * 消息媒体
 */
data class MessageMedia(
    @SerializedName("file_key")
    val fileKey: String,
    @SerializedName("file_hash")
    val fileHash: String,
    @SerializedName("file_type")
    val fileType: String,
    @SerializedName("image_height")
    val imageHeight: Long? = null,
    @SerializedName("image_width")
    val imageWidth: Long? = null,
    @SerializedName("file_size")
    val fileSize: Long,
    @SerializedName("file_key2")
    val fileKey2: String,
    @SerializedName("file_suffix")
    val fileSuffix: String
)

/**
 * API 通用状态响应
 */
data class ApiStatus(
    @SerializedName("number")
    val number: Long? = null,
    @SerializedName("code")
    val code: Int,
    @SerializedName("msg")
    val message: String
)

data class UserDataResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("data")
    val data: UserDataOuter? = null,
    @SerializedName("msg")
    val msg: String
)

data class UserDataOuter(
    @SerializedName("data")
    val data: UserData? = null
)

data class UserData(
    @SerializedName("id")
    val id: Long,
    @SerializedName("userId")
    val userId: String,
    @SerializedName("lastLoginTime")
    val lastLoginTime: Long,
    @SerializedName("update_time")
    val updateTime: Long,
    @SerializedName("introduction")
    val introduction: String? = null,
    @SerializedName("gender")
    val gender: Int? = null,
    @SerializedName("birthday")
    val birthday: Long? = null,
    @SerializedName("province")
    val province: String? = null,
    @SerializedName("city")
    val city: String? = null,
    @SerializedName("district")
    val district: String? = null,
    @SerializedName("locationCode")
    val locationCode: String? = null
)

data class SaveUserDataRequest(
    @SerializedName("introduction")
    val introduction: String,
    @SerializedName("gender")
    val gender: Int,
    @SerializedName("birthday")
    val birthday: Long,
    @SerializedName("province")
    val province: String,
    @SerializedName("city")
    val city: String,
    @SerializedName("district")
    val district: String,
    @SerializedName("locationCode")
    val locationCode: String
)

data class SaveUserRemarkRequest(
    @SerializedName("friendId")
    val friendId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("phone")
    val phone: String,
    @SerializedName("others")
    val others: String
)

/**
 * 七牛云音频上传 token 响应
 */
data class QiniuTokenResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("data")
    val data: QiniuTokenData,
    @SerializedName("msg")
    val msg: String
)

data class QiniuTokenData(
    @SerializedName("token")
    val token: String
)

/**
 * 七牛云上传域名查询响应
 */
data class QiniuHostsResponse(
    @SerializedName("hosts")
    val hosts: List<QiniuHost>,
    @SerializedName("ttl")
    val ttl: Long
)

data class QiniuHost(
    @SerializedName("region")
    val region: String,
    @SerializedName("ttl")
    val ttl: Long,
    @SerializedName("up")
    val up: QiniuUploadDomains
)

data class QiniuUploadDomains(
    @SerializedName("domains")
    val domains: List<String>
)

/**
 * 七牛云上传响应
 */
data class QiniuUploadResponse(
    @SerializedName("key")
    val key: String,
    @SerializedName("hash")
    val hash: String,
    @SerializedName("fsize")
    val fsize: Long,
    @SerializedName("avinfo")
    val avinfo: QiniuAudioInfo?
)

data class QiniuAudioInfo(
    @SerializedName("format")
    val format: QiniuAudioFormat?
)

data class QiniuAudioFormat(
    @SerializedName("duration")
    val duration: String?
)

/**
 * 语音消息发送响应
 */
data class SendMessageResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("msg")
    val msg: String,
    @SerializedName("data")
    val data: Any?
)

/**
 * 撤回消息请求
 */
data class RecallMessageRequest(
    @SerializedName("msg_id")
    val msgId: String,
    @SerializedName("chat_id")
    val chatId: String,
    @SerializedName("chat_type")
    val chatType: Int
)

/**
 * 批量撤回消息请求
 */
data class RecallMessageBatchRequest(
    @SerializedName("msg_id")
    val msgIds: List<String>,
    @SerializedName("chat_id")
    val chatId: String,
    @SerializedName("chat_type")
    val chatType: Int
)

/**
 * 聊天类型枚举
 */
enum class ChatType(val value: Int) {
    USER(1),
    GROUP(2),
    BOT(3)
}

/**
 * 当前用户个人信息
 */
data class CurrentUserProfile(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    
    @SerializedName("avatar_id")
    val avatarId: Long? = null,
    
    @SerializedName("phone")
    val phone: String? = null,
    
    @SerializedName("email")
    val email: String? = null,
    
    @SerializedName("coin")
    val coin: Double? = null,
    
    @SerializedName("is_vip")
    val isVip: Int? = null,
    
    @SerializedName("vip_expired_time")
    val vipExpiredTime: Long? = null,
    
    @SerializedName("invitation_code")
    val invitationCode: String? = null
)

/**
 * 用户信息API响应
 */
data class UserInfoResponse(
    @SerializedName("number")
    val number: Long? = null,
    
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("msg")
    val message: String,
    
    @SerializedName("data")
    val data: UserProfile? = null
)

/**
 * 更改密码请求
 */
data class ChangePasswordRequest(
    @SerializedName("email")
    val email: String,
    
    @SerializedName("captcha")
    val captcha: String,
    
    @SerializedName("password")
    val password: String
)

/**
 * 修改邀请码请求
 */
data class ChangeInviteCodeRequest(
    @SerializedName("code")
    val code: String
)

/**
 * 群聊信息响应
 */
data class GroupInfoResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: GroupInfoData,
    
    @SerializedName("msg")
    val msg: String
)

data class GroupInfoData(
    @SerializedName("group")
    val group: GroupInfoResponseGroupData
)

data class GroupInfoResponseGroupData(
    @SerializedName("id")
    val id: Int,
    @SerializedName("groupId")
    val groupId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("introduction")
    val introduction: String,
    @SerializedName("createBy")
    val createBy: String,
    @SerializedName("createTime")
    val createTime: Long,
    @SerializedName("avatarId")
    val avatarId: Int,
    @SerializedName("avatarUrl")
    val avatarUrl: String,
    @SerializedName("headcount")
    val headcount: Int,
    @SerializedName("readHistory")
    val readHistory: Int
)

/**
 * 机器人信息响应
 */
data class BotInfoResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: BotInfoData,
    
    @SerializedName("msg")
    val msg: String
)

data class BotInfoData(
    @SerializedName("bot")
    val bot: BotInfo
)

data class BotInfo(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("botId")
    val botId: String,
    
    @SerializedName("nickname")
    val nickname: String,
    
    @SerializedName("nicknameId")
    val nicknameId: Int,
    
    @SerializedName("avatarId")
    val avatarId: Int,
    
    @SerializedName("avatarUrl")
    val avatarUrl: String,
    
    @SerializedName("introduction")
    val introduction: String,
    
    @SerializedName("createBy")
    val createBy: String,
    
    @SerializedName("createTime")
    val createTime: Long,
    
    @SerializedName("headcount")
    val headcount: Int,
    
    @SerializedName("private")
    val isPrivate: Int
)

/**
 * 我创建的机器人列表响应
 */
data class MyBotListResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: MyBotListData,
    
    @SerializedName("message")
    val message: String?
)

data class MyBotListData(
    @SerializedName("botsTotal")
    val botsTotal: Int,
    
    @SerializedName("list")
    val list: MyBotList
)

data class MyBotList(
    @SerializedName("bots")
    val bots: List<CreatedBot>
)

data class CreatedBot(
    @SerializedName("id")
    val id: Int = 0,
    
    @SerializedName("botId")
    val botId: String,
    
    @SerializedName("nickname")
    val nickname: String,
    
    @SerializedName("nicknameId")
    val nicknameId: Int = 0,
    
    @SerializedName("avatarId")
    val avatarId: Int = 0,
    
    @SerializedName("avatarUrl")
    val avatarUrl: String,
    
    @SerializedName("token")
    val token: String? = null,
    
    @SerializedName("link")
    val link: String? = null,
    
    @SerializedName("introduction")
    val introduction: String,
    
    @SerializedName("createBy")
    val createBy: String? = null,
    
    @SerializedName("createTime")
    val createTime: Long = 0,
    
    @SerializedName("headcount")
    val headcount: Int = 0,
    
    @SerializedName("private")
    val private: Int = 0,
    
    @SerializedName("isStop")
    val isStop: Int = 0,
    
    @SerializedName("settingJson")
    val settingJson: String? = null,
    
    @SerializedName("del_flag")
    val delFlag: Int = 0,
    
    @SerializedName("alwaysAgree")
    val alwaysAgree: Int = 0,
    
    @SerializedName("groupLimit")
    val groupLimit: Int = 0,
    
    @SerializedName("banId")
    val banId: Int = 0,
    
    @SerializedName("linkStop")
    val linkStop: Int = 0,
    
    @SerializedName("isBigModel")
    val isBigModel: Int = 0,
    
    @SerializedName("uri")
    val uri: String? = null
)

data class BotIdRequest(
    @SerializedName("botId") val botId: String
)

data class ResetBotTokenResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: ResetTokenData?,
    @SerializedName("msg") val msg: String?
)

data class ResetTokenData(
    @SerializedName("token") val token: String
)

data class BotEventEditRequest(
    @SerializedName("botId") val botId: String,
    @SerializedName("messageReceiveNormal") val messageReceiveNormal: Int? = null,
    @SerializedName("messageReceiveInstruction") val messageReceiveInstruction: Int? = null,
    @SerializedName("botFollowed") val botFollowed: Int? = null,
    @SerializedName("botUnfollowed") val botUnfollowed: Int? = null,
    @SerializedName("groupJoin") val groupJoin: Int? = null,
    @SerializedName("groupLeave") val groupLeave: Int? = null,
    @SerializedName("botSetting") val botSetting: Int? = null,
    @SerializedName("typ") val typ: String? = null
)

data class BotEventSettingsResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: BotEventSettingsData?,
    @SerializedName("msg") val msg: String?
)

data class BotEventSettingsData(
    @SerializedName("list") val list: BotEventSettings?
)

data class BotEventSettings(
    @SerializedName("id") val id: Int,
    @SerializedName("botId") val botId: String,
    @SerializedName("messageReceiveNormal") val messageReceiveNormal: Int,
    @SerializedName("messageReceiveInstruction") val messageReceiveInstruction: Int,
    @SerializedName("botFollowed") val botFollowed: Int,
    @SerializedName("botUnfollowed") val botUnfollowed: Int,
    @SerializedName("groupJoin") val groupJoin: Int,
    @SerializedName("groupLeave") val groupLeave: Int,
    @SerializedName("botSetting") val botSetting: Int,
    @SerializedName("del_flag") val delFlag: Int
)

/**
 * 发现群聊分类请求
 */
data class RecommendCategoryRequest(
    @SerializedName("appChannel") val appChannel: String = "default"
)

/**
 * 发现群聊分类响应
 */
data class RecommendCategoryResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: RecommendCategoryData?,
    @SerializedName("msg") val msg: String?
)

data class RecommendCategoryData(
    @SerializedName("categories") val categories: List<String>
)

/**
 * 发现群聊列表请求
 */
data class RecommendGroupListRequest(
    @SerializedName("category") val category: String = "",
    @SerializedName("keyword") val keyword: String = "",
    @SerializedName("size") val size: Int = 30,
    @SerializedName("page") val page: Int = 1
)

/**
 * 发现群聊列表响应
 */
data class RecommendGroupListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: RecommendGroupListData?,
    @SerializedName("msg") val msg: String?
)

data class RecommendGroupListData(
    @SerializedName("groupList") val groupList: List<RecommendGroup>
)

/**
 * 推荐群聊信息
 */
data class RecommendGroup(
    @SerializedName("chatId") val chatId: String,
    @SerializedName("banId") val banId: Int = 0,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("introduction") val introduction: String?,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    @SerializedName("headcount") val headcount: Int,
    @SerializedName("createTime") val createTime: Long
)

/**
 * 搜索推荐群聊请求
 */
data class SearchRecommendGroupRequest(
    @SerializedName("keyword") val keyword: String,
    @SerializedName("categoryId") val categoryId: Int? = null
)

/**
 * 搜索推荐群聊响应
 */
data class SearchRecommendGroupResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: SearchRecommendGroupData?,
    @SerializedName("msg") val msg: String?
)

data class SearchRecommendGroupData(
    @SerializedName("groups") val groups: List<SearchRecommendGroupItem>
)

/**
 * 搜索到的群聊信息
 */
data class SearchRecommendGroupItem(
    @SerializedName("groupId") val groupId: String,
    @SerializedName("name") val name: String,
    @SerializedName("introduction") val introduction: String?,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    @SerializedName("headcount") val headcount: Int,
    @SerializedName("createTime") val createTime: Long,
    @SerializedName("banId") val banId: Int = 0,
    @SerializedName("alwaysAgree") val alwaysAgree: Int = 0,
    @SerializedName("categoryId") val categoryId: Int = 0,
    @SerializedName("category") val category: String? = null
)

/**
 * 推荐机器人列表响应
 */
data class RecommendBotListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: RecommendBotListData?,
    @SerializedName("msg") val msg: String?
)

data class RecommendBotListData(
    @SerializedName("botList") val botList: List<RecommendBot>
)

/**
 * 推荐机器人信息
 */
data class RecommendBot(
    @SerializedName("chatId") val chatId: String,
    @SerializedName("chatType") val chatType: String,
    @SerializedName("headcount") val headcount: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("introduction") val introduction: String?,
    @SerializedName("introductions") val introductions: String?,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    @SerializedName("isAdd") val isAdd: Int = 1,
    @SerializedName("isApply") val isApply: Int = 0,
    @SerializedName("alwaysAgree") val alwaysAgree: Int = 0
)

/**
 * 机器人商店Banner响应 (/v1/bot/banner)
 */
data class BotBannerResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: BotBannerData?,
    @SerializedName("msg") val msg: String?
)

data class BotBannerData(
    @SerializedName("banners") val banners: List<BotBanner>
)

data class BotBanner(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("introduction") val introduction: String?,
    @SerializedName("targetId") val targetId: String?,
    @SerializedName("targetUrl") val targetUrl: String?,
    @SerializedName("imageUrl") val imageUrl: String,
    @SerializedName("sort") val sort: Int,
    @SerializedName("delFlag") val delFlag: Int,
    @SerializedName("createTime") val createTime: Long,
    @SerializedName("remark") val remark: String?,
    @SerializedName("createBy") val createBy: Int,
    @SerializedName("typ") val typ: Int
)

/**
 * 机器人新列表响应 (/v1/bot/new-list)
 */
data class BotNewListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: BotNewListData?,
    @SerializedName("msg") val msg: String?
)

data class BotNewListData(
    @SerializedName("bots") val bots: List<BotNewItem>
)

data class BotNewItem(
    @SerializedName("chatId") val chatId: String,
    @SerializedName("chatType") val chatType: String,
    @SerializedName("headcount") val headcount: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("introduction") val introduction: String?,
    @SerializedName("instructions") val instructions: String?,
    @SerializedName("avatarUrl") val avatarUrl: String?
)

/**
 * 机器人详情响应 (/v1/bot/bot-detail)
 */
data class BotDetailResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: BotDetailData?,
    @SerializedName("msg") val msg: String?
)

data class BotDetailData(
    @SerializedName("bot") val bot: BotDetail,
    @SerializedName("groups") val groups: List<BotDetailGroup>?
)

data class BotDetail(
    @SerializedName("id") val id: Int,
    @SerializedName("botId") val botId: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("nicknameId") val nicknameId: Int,
    @SerializedName("avatarId") val avatarId: Int,
    @SerializedName("avatarUrl") val avatarUrl: String,
    @SerializedName("type") val type: Int,
    @SerializedName("introduction") val introduction: String?,
    @SerializedName("createBy") val createBy: String,
    @SerializedName("createTime") val createTime: Long,
    @SerializedName("headcount") val headcount: Int,
    @SerializedName("private") val private: Int,
    @SerializedName("isStop") val isStop: Int,
    @SerializedName("settingJson") val settingJson: String?,
    @SerializedName("del_flag") val delFlag: Int,
    @SerializedName("alwaysAgree") val alwaysAgree: Int,
    @SerializedName("banId") val banId: Int,
    @SerializedName("uri") val uri: String?
)

data class BotDetailGroup(
    @SerializedName("groupId") val groupId: String,
    @SerializedName("name") val name: String,
    @SerializedName("introduction") val introduction: String?,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    @SerializedName("headcount") val headcount: Int
)

data class BotDetailRequest(
    @SerializedName("id") val id: String
)

/**
 * 机器人指令列表响应 (/v1/instruction/web-list)
 */
data class BotInstructionListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: BotInstructionListData?,
    @SerializedName("msg") val msg: String?
)

data class BotInstructionListData(
    @SerializedName("list") val list: List<BotInstruction>
)

data class BotInstruction(
    @SerializedName("id") val id: Int,
    @SerializedName("botId") val botId: String,
    @SerializedName("name") val name: String,
    @SerializedName("desc") val desc: String?,
    @SerializedName("instructionType") val instructionType: Int, // 1-普通指令，2-直发指令，5-自定义输入指令
    @SerializedName("hintText") val hintText: String?,
    @SerializedName("defaultText") val defaultText: String?,
    @SerializedName("customJson") val customJson: String?,
    @SerializedName("createTime") val createTime: Long,
    @SerializedName("sort") val sort: Int,
    @SerializedName("hidden") val hidden: Int
) : java.io.Serializable

data class BotInstructionRequest(
    @SerializedName("botId") val botId: String
)

data class CreateInstructionRequest(
    @SerializedName("name") val name: String,
    @SerializedName("desc") val desc: String,
    @SerializedName("hintText") val hintText: String?,
    @SerializedName("defaultText") val defaultText: String?,
    @SerializedName("type") val type: Int,
    @SerializedName("botId") val botId: String,
    @SerializedName("customJson") val customJson: String? = null
)

data class EditInstructionRequest(
    @SerializedName("id") val id: Int,
    @SerializedName("botId") val botId: String,
    @SerializedName("name") val name: String,
    @SerializedName("desc") val desc: String?,
    @SerializedName("hintText") val hintText: String?,
    @SerializedName("defaultText") val defaultText: String?,
    @SerializedName("type") val type: Int,
    @SerializedName("customJson") val customJson: String? = null,
    @SerializedName("delFlag") val delFlag: Int? = null
)

data class UserHomepageResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: UserHomepageData,
    
    @SerializedName("msg")
    val msg: String
)

data class UserHomepageData(
    @SerializedName("user")
    val user: UserHomepageInfo
)

data class UserHomepageInfo(
    @SerializedName("userId")
    val userId: String,
    
    @SerializedName("nickname")
    val nickname: String,
    
    @SerializedName("avatarUrl")
    val avatarUrl: String,
    
    @SerializedName("registerTime")
    val registerTime: Long,
    
    @SerializedName("registerTimeText")
    val registerTimeText: String,
    
    @SerializedName("onLineDay")
    val onLineDay: Int,
    
    @SerializedName("continuousOnLineDay")
    val continuousOnLineDay: Int,
    
    @SerializedName("medals")
    val medals: List<Medal>,
    
    @SerializedName("isVip")
    val isVip: Int
)

data class Medal(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("desc")
    val desc: String,
    
    @SerializedName("imageUrl")
    val imageUrl: String,
    
    @SerializedName("sort")
    val sort: Int
)

/**
 * Deep Link 会话信息
 */
data class ChatAddInfo(
    val id: String,
    val type: ChatAddType,
    val displayName: String,
    val avatarUrl: String,
    val description: String,
    val additionalInfo: String = ""
)

enum class ChatAddType(val value: String, val chatType: Int) {
    USER("user", 1),
    GROUP("group", 2),
    BOT("bot", 3);
    
    companion object {
        fun fromString(type: String): ChatAddType? {
            return values().find { it.value == type }
        }
    }
}

/**
 * 添加好友请求
 */
data class AddFriendRequest(
    @SerializedName("chatId")
    val chatId: String,
    
    @SerializedName("chatType")
    val chatType: Int,
    
    @SerializedName("remark")
    val remark: String = ""
)

/**
 * 邮箱验证码请求
 */
data class EmailVerificationRequest(
    @SerializedName("email")
    val email: String,
    
    @SerializedName("code")
    val code: String,
    
    @SerializedName("id")
    val id: String
)

/**
 * 用户信息 (用于搜索)
 */
data class UserInfo(
    @SerializedName("user_id")
    val userId: String? = null,
    @SerializedName("nickname")
    val nickname: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("register_time")
    val registerTime: Long? = null,
    @SerializedName("register_time_text")
    val registerTimeText: String? = null,
    @SerializedName("on_line_day")
    val onLineDay: Int? = null,
    @SerializedName("continuous_on_line_day")
    val continuousOnLineDay: Int? = null,
    @SerializedName("is_vip")
    val isVip: Int? = null
)

// ========== 群聊相关数据模型 ==========

/**
 * 群聊详细信息（Proto解析专用）
 */
data class GroupDetail(
    val groupId: String,
    val name: String,
    val avatarUrl: String,
    val introduction: String,
    val memberCount: Int,
    val createBy: String,
    val directJoin: Boolean,
    val permissionLevel: Int,
    val historyMsgEnabled: Boolean,
    val categoryName: String,
    val categoryId: Long,
    val isPrivate: Boolean,
    val doNotDisturb: Boolean,
    val hideGroupMembers: Boolean,
    val denyMembersUploadToGroupDisk: Boolean,
    val communityId: Long,
    val communityName: String,
    val isTop: Boolean,
    val adminIds: List<String>,
    val ownerId: String,
    val limitedMsgType: String,
    val avatarId: Long? = null,
    val recommendation: Int? = null,
    val myGroupNickname: String? = null,
    val groupCode: String? = null,
    val autoDeleteMessage: Long = 0L,
    val unbanTimestamp: Long? = null,
    val banReason: String? = null
)

/**
 * 群聊成员信息（Proto解析专用）
 */
data class GroupMemberInfo(
    val userId: String,
    val name: String,
    val avatarUrl: String,
    val isVip: Boolean,
    val permissionLevel: Int,
    val gagTime: Long,
    val isGag: Boolean
)

/**
 * 添加个人表情收藏请求
 */
data class AddExpressionRequest(
    @SerializedName("id")
    val id: Long  // 表情ID，数字类型
)

/**
 * 表情包详情请求
 */
data class StickerPackDetailRequest(
    @SerializedName("id")
    val id: Long  // 表情包ID，数字类型
)

/**
 * 添加表情包请求
 */
data class AddStickerPackRequest(
    @SerializedName("id")
    val id: Long  // 表情包ID，数字类型
)

/**
 * 表情包详情信息
 */
data class StickerPackDetail(
    val id: Int,
    val name: String,
    val createBy: String,
    val createTime: Long,
    val userCount: Int,
    val uuid: String,
    val updateTime: Long,
    val stickerItems: List<StickerItem>,
    val creator: StickerPackCreator
)


// ========== 分享链接相关数据模型 ==========

/**
 * 分享链接信息请求
 */
data class ShareInfoRequest(
    @SerializedName("key")
    val key: String,
    @SerializedName("ts")
    val ts: String
)

/**
 * 分享链接信息响应
 */
data class ShareInfoResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("data")
    val data: ShareInfoData?,
    @SerializedName("msg")
    val message: String
)

/**
 * 分享链接数据
 */
data class ShareInfoData(
    @SerializedName("share")
    val share: ShareInfo
)

/**
 * 分享信息
 */
data class ShareInfo(
    @SerializedName("id")
    val id: Int,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("chat_name")
    val chatName: String,
    @SerializedName("chat_type")
    val chatType: Int,
    @SerializedName("chat_id")
    val chatId: String,
    @SerializedName("key")
    val key: String,
    @SerializedName("create_by")
    val createBy: String,
    @SerializedName("create_time")
    val createTime: Long,
    @SerializedName("imageUrl")
    val imageUrl: String?,
    @SerializedName("imageName")
    val imageName: String?
)

/**
 * 添加好友/群聊/机器人请求
 */
data class FriendApplyRequest(
    @SerializedName("chatId")
    val chatId: String,
    @SerializedName("chatType")
    val chatType: Int,
    @SerializedName("remark")
    val remark: String
)

/**
 * 群指令列表响应
 */
data class InstructionListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: InstructionListData?,
    @SerializedName("msg") val msg: String?
)

data class InstructionListData(
    @SerializedName("instructions") val instructions: List<Instruction>
)

/**
 * 群指令
 */
data class Instruction(
    @SerializedName("botId") val botId: String,
    @SerializedName("botName") val botName: String,
    @SerializedName("name") val name: String,
    @SerializedName("desc") val desc: String,
    @SerializedName("id") val id: Long, // 改为Long以匹配ProtoBuf的int64
    @SerializedName("sort") val sort: Int = 0,
    @SerializedName("auth") val auth: Int = 0, // 0-所有人可用，1-所有人禁用，2-群主可用，3-群主管理员可用
    // ProtoBuf额外字段
    val type: Int = 1, // 指令类型：1-普通指令，2-直发指令，5-自定义输入指令
    val hintText: String = "", // 输入框提示文字
    val defaultText: String = "", // 输入框默认文字
    val form: String = "" // 表单（自定义输入指令用）
) : java.io.Serializable

/**
 * 群ID请求（通用）
 */
data class GroupIdRequest(
    @SerializedName("groupId") val groupId: String
)

/**
 * 邀请加入群聊请求
 */
data class InviteGroupRequest(
    @SerializedName("chatId") val chatId: String,
    @SerializedName("chatType") val chatType: Int,
    @SerializedName("groupId") val groupId: String
)

data class RemoveGroupBotRequest(
    @SerializedName("groupId") val groupId: String,
    @SerializedName("botId") val botId: String
)

/**
 * 移除群聊机器人请求
 */
data class RemoveBotRequest(
    @SerializedName("botId") val botId: String,
    @SerializedName("groupId") val groupId: String
)

/**
 * 群聊机器人菜单按钮
 */
data class MenuButton(
    val id: Long,
    val botId: String,
    val name: String,
    val content: String, // 内容
    val menuType: Int, // 按钮类型,1-普通按钮 2-选中按钮 3-下拉选择
    val createTime: Long,
    val menuAction: Int, // 操作类型
    val select: String = "" // 选择的选项,在选择按钮的时候也作为状态,选中为1,未选中为0
)
