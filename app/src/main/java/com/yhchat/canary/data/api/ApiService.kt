package com.yhchat.canary.data.api

import com.google.gson.annotations.SerializedName
import com.yhchat.canary.data.model.*
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * 云湖API服务接口
 */
interface ApiService {
    
    // ==================== 用户相关API ====================
    @GET("v1/user/info")
    suspend fun getUserInfo(
        @Header("token") token: String
    ): Response<User>
    
    @GET("v1/user/info")
    suspend fun getUserProfile(
        @Header("token") token: String
    ): Response<ResponseBody>
    
    /**
     * 修改用户头像
     */
    @POST("v1/user/edit-avatar")
    suspend fun editAvatar(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<ResponseBody>
    
    /**
     * 获取用户详细信息
     */
    @POST("v1/user/get-user")
    suspend fun getUserDetail(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<ResponseBody>

    @POST("v1/user/get-user-data")
    suspend fun getUserData(
        @Header("token") token: String
    ): Response<UserDataResponse>

    @POST("v1/user/save-user-data")
    suspend fun saveUserData(
        @Header("token") token: String,
        @Body request: SaveUserDataRequest
    ): Response<ApiStatus>

    @POST("v1/user/save-user-remarks")
    suspend fun saveUserRemarks(
        @Header("token") token: String,
        @Body request: SaveUserRemarkRequest
    ): Response<ApiStatus>
    
    // 获取七牛云音频上传 token
    @GET("v1/misc/qiniu-token-audio")
    suspend fun getQiniuAudioToken(
        @Header("token") token: String
    ): Response<QiniuTokenResponse>
    
    // 发送语音消息
    @POST("v1/msg/send-message")
    suspend fun sendVoiceMessage(
        @Header("token") token: String,
        @Body request: ByteArray
    ): Response<SendMessageResponse>
    
    /**
     * 获取用户统计数据
     */
    @POST("v1/user/invite/invite-progress")
    suspend fun getUserStats(
        @Header("token") token: String
    ): Response<UserStatsResponse>

    
    @POST("v1/user/captcha")
    suspend fun getCaptcha(): Response<CaptchaResponse>

    @POST("v1/verification/get-verification-code")
    suspend fun getSmsCaptcha(
        @Body request: SmsCaptchaRequest
    ): Response<Map<String, Any>>

    @POST("v1/verification/get-email-verification-code")
    suspend fun getEmailVerificationCode(
        @Body request: EmailVerificationRequest
    ): Response<Map<String, Any>>

    @POST("v1/user/bing-email")
    suspend fun bindEmail(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>

    @POST("v1/user/change-email-check")
    suspend fun changeEmail(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>

    @POST("v1/user/forget-password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest
    ): Response<Map<String, Any>>

    @POST("v1/search/home-search")
    suspend fun homeSearch(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.SearchRequest
    ): Response<com.yhchat.canary.data.model.SearchResponse>

    @POST("v1/sticky/list")
    suspend fun getStickyList(
        @Header("token") token: String
    ): Response<StickyResponse>

    @POST("v1/sticky/add")
    suspend fun addSticky(
        @Header("token") token: String,
        @Body request: StickyOperationRequest
    ): Response<Map<String, Any>>

    @POST("v1/sticky/delete")
    suspend fun deleteSticky(
        @Header("token") token: String,
        @Body request: StickyOperationRequest
    ): Response<Map<String, Any>>

    @POST("v1/sticky/topping")
    suspend fun topSticky(
        @Header("token") token: String,
        @Body request: StickyTopRequest
    ): Response<Map<String, Any>>

    @POST("v1/user/verification-login")
    suspend fun verificationLogin(
        @Body request: LoginRequest
    ): Response<LoginResponse>
    
    @POST("v1/user/email-login")
    suspend fun emailLogin(
        @Body request: LoginRequest
    ): Response<LoginResponse>
    
    @POST("v1/user/logout")
    suspend fun logout(
        @Header("token") token: String,
        @Body request: Map<String, String>
    ): Response<Map<String, Any>>
    
    // ========== 消息相关API（使用protobuf） ==========
    
    /**
     * 获取消息列表（按序列）- 使用protobuf
     */
    @POST("v1/msg/list-message-by-seq")
    suspend fun listMessageBySeq(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 获取消息列表 - 使用protobuf
     */
    @POST("v1/msg/list-message")
    suspend fun listMessage(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 按消息ID列出消息（包含消息id指定的消息）- 使用protobuf
     */
    @POST("v1/msg/list-message-by-mid-seq")
    suspend fun listMessageByMidSeq(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 发送消息 - 使用protobuf
     */
    @POST("v1/msg/send-message")
    suspend fun sendMessage(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 撤回消息 - 使用protobuf
     */
    @POST("v1/msg/recall-msg")
    suspend fun recallMessage(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 编辑消息 - 使用protobuf
     */
    @POST("v1/msg/edit-message")
    suspend fun editMessage(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>

    /**
     * 转发消息 - JSON
     */
    @POST("v1/msg/msg-forward")
    suspend fun forwardMessage(
        @Header("token") token: String,
        @Body request: MsgForwardRequest
    ): Response<BaseResponse>

    @POST("v1/group/live-room")
    suspend fun getGroupLiveRooms(
        @Header("token") token: String,
        @Body request: GroupLiveRoomsRequest
    ): Response<LiveRoomsResponse>

    @POST("v1/live/add")
    suspend fun joinLiveRoom(
        @Header("token") token: String,
        @Body request: LiveJoinRequest
    ): Response<LiveJoinResponse>

    @POST("v1/live/room-info")
    suspend fun getLiveRoomInfo(
        @Header("token") token: String,
        @Body request: LiveRoomInfoRequest
    ): Response<LiveRoomInfoResponse>

    @POST("v1/live/hang_up")
    suspend fun hangUpLiveRoom(
        @Header("token") token: String,
        @Body request: LiveHangUpRequest
    ): Response<BaseResponse>
    
    // ========== 机器人相关API（使用protobuf） ==========
    
    /**
     * 获取机器人详细信息 - 使用protobuf
     */
    @POST("v1/bot/bot-info")
    suspend fun getBotInfo(
        @Header("token") token: String,
        @Body body: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 获取可用的大模型列表
     */
    @POST("v1/bot/llm/llm-setting-list")
    suspend fun getBotLlmModelList(
        @Header("token") token: String
    ): Response<BotLlmSettingListResponse>

    /**
     * 获取机器人当前的大模型配置
     */
    @POST("v1/bot/llm/llm-setting-ref-info")
    suspend fun getBotLlmSettings(
        @Header("token") token: String,
        @Body request: BotIdRequest
    ): Response<BotLlmSettingRefResponse>

    @POST("v1/bot/llm/llm-setting-ref-params")
    suspend fun getBotLlmRefParams(
        @Header("token") token: String,
        @Body request: StickyOperationRequest
    ): Response<BotLlmRefParamsResponse>

    /**
     * 保存机器人 LLM 设置
     */
    @POST("v1/bot/llm/llm-save")
    suspend fun saveBotLlmSettings(
        @Header("token") token: String,
        @Body request: BotLlmSaveRequest
    ): Response<BaseResponse>

    /**
     * 清空机器人 LLM 上下文
     */
    @POST("v1/bot/llm/clean-content")
    suspend fun cleanBotContext(
        @Header("token") token: String,
        @Body request: BotIdRequest
    ): Response<BaseResponse>

    /**
     * 获取机器人看板 - 使用protobuf
     */
    @POST("v1/bot/board")
    suspend fun getBotBoard(
        @Header("token") token: String,
        @Body body: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>

    /**
     * 获取机器人使用的用户列表 - ProtoBuf
     */
    @POST("v1/bot/follower-list")
    suspend fun getBotFollowerList(
        @Header("token") token: String,
        @Body body: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>

    /**
     * 获取机器人加入的群聊列表 - ProtoBuf
     */
    @POST("v1/bot/join-group-list")
    suspend fun getBotJoinGroupList(
        @Header("token") token: String,
        @Body body: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>

    @POST("v1/bot/remove-follower")
    suspend fun removeBotFollower(
        @Header("token") token: String,
        @Body request: BotUsageRemoveFollowerRequest
    ): Response<ApiStatus>

    @POST("v1/bot/remove-group")
    suspend fun removeBotGroup(
        @Header("token") token: String,
        @Body request: BotUsageRemoveGroupRequest
    ): Response<ApiStatus>
    

    /**
     * 重置机器人token
     */
    @POST("v1/bot/reset-bot-token")
    suspend fun resetBotToken(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.BotIdRequest
    ): Response<com.yhchat.canary.data.model.ResetBotTokenResponse>
    
    /**
     * 获取机器人商店Banner
     */
    @POST("v1/bot/banner")
    suspend fun getBotBanner(
        @Header("token") token: String
    ): Response<com.yhchat.canary.data.model.BotBannerResponse>
    
    /**
     * 获取机器人商店列表
     */
    @POST("v1/bot/new-list")
    suspend fun getBotNewList(
        @Header("token") token: String
    ): Response<com.yhchat.canary.data.model.BotNewListResponse>
    
    /**
     * 获取机器人详情
     */
    @POST("v1/bot/bot-detail")
    suspend fun getBotDetail(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.BotDetailRequest
    ): Response<com.yhchat.canary.data.model.BotDetailResponse>
    
    /**
     * 获取机器人指令列表
     */
    @POST("v1/instruction/web-list")
    suspend fun getBotInstructionList(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.BotInstructionRequest
    ): Response<com.yhchat.canary.data.model.BotInstructionListResponse>
    
    /**
     * 创建机器人指令
     */
    @POST("v1/instruction/create")
    suspend fun createBotInstruction(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.CreateInstructionRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 编辑机器人指令
     */
    @POST("v1/instruction/edit")
    suspend fun editBotInstruction(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.EditInstructionRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>

    /**
     * 获取机器人私聊指令（ProtoBuf）
     * POST /v1/instruction/list
     */
    @POST("v1/instruction/list")
    suspend fun getBotInstructionListProto(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>

    /**
     * 获取机器人事件订阅设置
     */
    @POST("v1/event/list")
    suspend fun getBotEventSettings(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.BotIdRequest
    ): Response<com.yhchat.canary.data.model.BotEventSettingsResponse>
    
    /**
     * 设置机器人事件订阅
     */
    @POST("v1/event/edit")
    suspend fun editBotEventSettings(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.BotEventEditRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 编辑机器人信息
     */
    @POST("v1/bot/web-edit-bot")
    suspend fun editBot(
        @Header("token") token: String,
        @Body request: EditBotRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 删除群聊对机器人的添加
     * POST /v1/bot/remove-group
     */
    @POST("v1/bot/remove-group")
    suspend fun removeGroupBot(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.RemoveBotRequest
    ): Response<com.yhchat.canary.data.model.ApiStatus>

    /**
     * 编辑机器人在群聊中的管理权限
     */
    @POST("v1/bot/group-permission-edit")
    suspend fun editBotGroupPermission(
        @Header("token") token: String,
        @Body request: BotGroupPermissionEditRequest
    ): Response<ApiStatus>

    /**
     * 获取机器人在群聊中的管理权限
     */
    @POST("v1/bot/group-permission-get")
    suspend fun getBotGroupPermission(
        @Header("token") token: String,
        @Body request: BotGroupPermissionGetRequest
    ): Response<BotGroupPermissionGetResponse>
    
    /**
     * 批量撤回消息 - 使用protobuf
     */
    @POST("v1/msg/recall-msg-batch")
    suspend fun recallMessagesBatchProto(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    @POST("v1/msg/button-report")
    suspend fun buttonReport(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    @POST("v1/msg/list-message-edit-record")
    suspend fun listMessageEditRecord(
        @Header("token") token: String,
        @Body request: ListMessageEditRecordRequest
    ): Response<Map<String, Any>>
    
    @POST("v1/msg/file-download-record")
    suspend fun fileDownloadRecord(
        @Header("token") token: String,
        @Body request: FileDownloadRecordRequest
    ): Response<Map<String, Any>>
    
    // ==================== 表情包相关API ====================
    /**
     * 获取个人表情收藏
     */
    @POST("v1/expression/list")
    suspend fun getExpressionList(
        @Header("token") token: String
    ): Response<ExpressionListResponse>
    
    /**
     * 添加图片到个人表情收藏
     */
    @POST("v1/expression/create")
    suspend fun addExpression(
        @Header("token") token: String,
        @Body request: ExpressionActionRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 删除个人表情收藏中的表情
     */
    @POST("v1/expression/delete")
    suspend fun deleteExpression(
        @Header("token") token: String,
        @Body request: ExpressionActionRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 置顶个人表情收藏中的表情
     */
    @POST("v1/expression/topping")
    suspend fun topExpression(
        @Header("token") token: String,
        @Body request: ExpressionActionRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 添加已有表情包
     */
    @POST("v1/expression/add")
    suspend fun addExistingExpression(
        @Header("token") token: String,
        @Body request: ExpressionActionRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    // ==================== 举报相关API ====================
    /**
     * 提交举报
     */
    @POST("v1/report/create")
    suspend fun submitReport(
        @Header("token") token: String,
        @Body request: ReportRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    // ==================== 分享相关API ====================
    /**
     * 创建分享链接
     */
    @POST("v1/share/create")
    suspend fun createShareLink(
        @Header("token") token: String,
        @Body request: CreateShareRequest
    ): Response<CreateShareResponse>
    
    // ==================== 聊天背景相关API ====================
    /**
     * 设置聊天背景
     */
    @POST("v1/chat-background/edit")
    suspend fun setChatBackground(
        @Header("token") token: String,
        @Body request: SetChatBackgroundRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 获取背景设置列表
     */
    @POST("v1/chat-background/list")
    suspend fun getChatBackgroundList(
        @Header("token") token: String
    ): Response<ChatBackgroundListResponse>
    
    // ==================== 贴纸功能相关API ====================
    /**
     * 获取收藏表情包
     */
    @POST("v1/sticker/list")
    suspend fun getStickerPackList(
        @Header("token") token: String
    ): Response<StickerPackListResponse>
    
    /**
     * 查看表情包详情
     */
    @POST("v1/sticker/detail")
    suspend fun getStickerPackDetail(
        @Header("token") token: String,
        @Body request: StickerPackActionRequest
    ): Response<StickerPackDetailResponse>
    
    /**
     * 添加表情包
     */
    @POST("v1/sticker/add")
    suspend fun addStickerPack(
        @Header("token") token: String,
        @Body request: StickerPackActionRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 移除收藏表情包
     */
    @POST("v1/sticker/remove-sticker-pack")
    suspend fun removeStickerPack(
        @Header("token") token: String,
        @Body request: StickerPackActionRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 更改收藏表情包的排序
     */
    @POST("v1/sticker/sort")
    suspend fun sortStickerPacks(
        @Header("token") token: String,
        @Body request: StickerPackSortRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>

    /**
     * 创建表情包
     */
    @POST("v1/sticker/create-pack")
    suspend fun createStickerPack(
        @Header("token") token: String,
        @Body request: CreateStickerPackRequest
    ): Response<CreateStickerPackResponse>

    /**
     * 重命名表情包
     */
    @POST("v1/sticker/rename-pack")
    suspend fun renameStickerPack(
        @Header("token") token: String,
        @Body request: RenameStickerPackRequest
    ): Response<BaseResponse>

    /**
     * 删除表情包
     */
    @POST("v1/sticker/delete-pack")
    suspend fun deleteStickerPack(
        @Header("token") token: String,
        @Body request: StickerPackActionRequest
    ): Response<BaseResponse>

    /**
     * 添加表情至表情包
     */
    @POST("v1/sticker/add-sticker")
    suspend fun addSticker(
        @Header("token") token: String,
        @Body request: AddStickerRequest
    ): Response<BaseResponse>

    /**
     * 重命名表情包里的表情
     */
    @POST("v1/sticker/rename-sticker")
    suspend fun renameSticker(
        @Header("token") token: String,
        @Body request: RenameStickerRequest
    ): Response<BaseResponse>

    /**
     * 删除表情
     */
    @POST("v1/sticker/remove-sticker")
    suspend fun removeSticker(
        @Header("token") token: String,
        @Body request: StickerPackActionRequest
    ): Response<BaseResponse>

    /**
     * 获取表情上传 token
     */
    @GET("v1/misc/qiniu-token-sticker")
    suspend fun getQiniuStickerToken(
        @Header("token") token: String
    ): Response<QiniuTokenResponse>

    @GET("v1/misc/qiniu-token-expression")
    suspend fun getQiniuExpressionToken(
        @Header("token") token: String
    ): Response<QiniuTokenResponse>
    
    // ==================== 云盘/文件存储相关API ====================
    /**
     * 创建群网盘文件夹
     */
    @POST("v1/disk/create-folder")
    suspend fun createFolder(
        @Header("token") token: String,
        @Body request: CreateFolderRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 获取群网盘文件列表
     */
    @POST("v1/disk/file-list")
    suspend fun getFileList(
        @Header("token") token: String,
        @Body request: GetFileListRequest
    ): Response<FileListResponse>
    
    /**
     * 上传文件到群网盘
     */
    @POST("v1/disk/upload-file")
    suspend fun uploadFileToDisk(
        @Header("token") token: String,
        @Body request: UploadFileRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 更改文件名
     */
    @POST("v1/disk/rename")
    suspend fun renameFile(
        @Header("token") token: String,
        @Body request: RenameFileRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 删除文件
     */
    @POST("v1/disk/remove")
    suspend fun removeFile(
        @Header("token") token: String,
        @Body request: RemoveFileRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    // ==================== 金币系统相关API ====================
    /**
     * 商品获取
     */
    @POST("v1/coin/shop/product-recommend")
    suspend fun getProductList(
        @Header("token") token: String,
        @Body request: ProductListRequest
    ): Response<ProductListResponse>
    
    /**
     * 我的金币任务获取
     */
    @POST("v1/coin/task/my-task-info")
    suspend fun getMyTaskInfo(
        @Header("token") token: String
    ): Response<MyTaskInfoResponse>
    
    /**
     * 获取商品详情
     */
    @POST("v1/coin/shop/product-detail")
    suspend fun getProductDetail(
        @Header("token") token: String,
        @Body request: ProductDetailRequest
    ): Response<ProductDetailResponse>
    
    /**
     * 购买商品
     */
    @POST("v1/coin/shop/order-create")
    suspend fun purchaseProduct(
        @Header("token") token: String,
        @Body request: PurchaseProductRequest
    ): Response<PurchaseProductResponse>
    
    /**
     * 获取金币增减记录
     */
    @POST("v1/user/gold-coin-increase-decrease-record")
    suspend fun getGoldCoinIncreaseDecreaseRecord(
        @Header("token") token: String,
        @Body request: GoldCoinRecordRequest
    ): Response<GoldCoinRecordResponse>
    
    /**
     * 获取打赏记录（文章/评论）
     */
    @POST("v1/community/reward-record")
    suspend fun getRewardRecord(
        @Header("token") token: String,
        @Body request: RewardRecordRequest
    ): Response<RewardRecordResponse>
    
    // ==================== VIP会员相关API ====================
    /**
     * VIP价格获取
     */
    @POST("v1/vip/vip-product-list")
    suspend fun getVipProductList(
        @Header("token") token: String,
        @Body request: VipProductListRequest
    ): Response<VipProductListResponse>
    
    /**
     * VIP特权获取
     */
    @GET("v1/vip/vip-benefits-list")
    suspend fun getVipBenefitsList(
        @Header("token") token: String
    ): Response<VipBenefitsListResponse>
    
    // ==================== 会话相关API ====================
    @POST("v1/conversation/list")
    suspend fun listConversations(
        @Header("token") token: String
    ): Response<ResponseBody>
    
    @POST("v1/conversation/dismiss-notification")
    suspend fun dismissNotification(
        @Header("token") token: String,
        @Body request: DismissNotificationRequest
    ): Response<DismissNotificationResponse>
    
    @POST("v1/conversation/remove")
    suspend fun removeConversation(
        @Header("token") token: String,
        @Body request: RemoveConversationRequest
    ): Response<ApiStatus>
    
    // 社区相关API
    @POST("v1/community/ba/following-ba-list")
    suspend fun getBoardList(
        @Header("token") token: String,
        @Body request: BoardListRequest
    ): Response<com.yhchat.canary.data.model.BoardListResponse>
    
    @POST("v1/community/ba/following-ba-list")
    suspend fun getFollowingBoardList(
        @Header("token") token: String,
        @Body request: BoardListRequest
    ): Response<com.yhchat.canary.data.model.FollowingBoardListResponse>

    @POST("v1/community/ba/following-ba-list")
    suspend fun getAllBoardList(
        @Header("token") token: String,
        @Body request: BoardListRequest
    ): Response<com.yhchat.canary.data.model.FollowingBoardListResponse>
    
    @POST("v1/community/posts/my-post-list")
    suspend fun getMyPostList(
        @Header("token") token: String,
        @Body request: MyPostListRequest
    ): Response<com.yhchat.canary.data.model.MyPostListResponse>
    
    @POST("v1/community/ba/info")
    suspend fun getBoardInfo(
        @Header("token") token: String,
        @Body request: BoardInfoRequest
    ): Response<com.yhchat.canary.data.model.BoardInfoResponse>
    
    @POST("v1/community/ba/user-follow-ba")
    suspend fun followBoard(
        @Header("token") token: String,
        @Body request: FollowBoardRequest
    ): Response<ApiStatus>

    @POST("v1/community/ba/user-unfollow-ba")
    suspend fun unfollowBoard(
        @Header("token") token: String,
        @Body request: UnfollowBoardRequest
    ): Response<ApiStatus>

    @POST("v1/community/ba/manage")
    suspend fun manageBoard(
        @Header("token") token: String,
        @Body request: ManageBoardRequest
    ): Response<ApiStatus>

    @POST("v1/community/ba/edit")
    suspend fun editBoard(
        @Header("token") token: String,
        @Body request: EditBoardRequest
    ): Response<ApiStatus>

    @POST("v1/community/ba/delete")
    suspend fun deleteBoard(
        @Header("token") token: String,
        @Body request: DeleteBoardRequest
    ): Response<ApiStatus>

    @POST("v1/community/ba/follower-list")
    suspend fun getBaFollowerList(
        @Header("token") token: String,
        @Body request: BaFollowerListRequest
    ): Response<com.yhchat.canary.data.model.BaFollowerListResponse>

    @POST("v1/community/ba/manage-setting")
    suspend fun setBaManageSetting(
        @Header("token") token: String,
        @Body request: BaManageSettingRequest
    ): Response<ApiStatus>

    @POST("v1/community/ba/list-by-create")
    suspend fun listBoardsByCreate(
        @Header("token") token: String,
        @Body request: BoardsByCreateRequest
    ): Response<com.yhchat.canary.data.model.BoardsByCreateResponse>
    
    @POST("v1/community/posts/post-list")
    suspend fun getPostList(
        @Header("token") token: String,
        @Body request: PostListRequest
    ): Response<com.yhchat.canary.data.model.PostListResponse>

    @POST("v1/community/posts/post-list-recommend")
    suspend fun getRecommendPostList(
        @Header("token") token: String,
        @Body request: RecommendPostListRequest
    ): Response<com.yhchat.canary.data.model.PostListResponse>

    @POST("v1/community/posts/post-collect-list")
    suspend fun getCollectPostList(
        @Header("token") token: String,
        @Body request: CollectPostListRequest
    ): Response<com.yhchat.canary.data.model.PostListResponse>
    
    @POST("v1/community/posts/post-detail")
    suspend fun getPostDetail(
        @Header("token") token: String,
        @Body request: PostDetailRequest
    ): Response<com.yhchat.canary.data.model.PostDetailResponse>
    
    @POST("v1/community/comment/comment-list")
    suspend fun getCommentList(
        @Header("token") token: String,
        @Body request: CommentListRequest
    ): Response<com.yhchat.canary.data.model.CommentListResponse>
    
    @POST("v1/community/posts/post-like")
    suspend fun likePost(
        @Header("token") token: String,
        @Body request: LikePostRequest
    ): Response<ApiStatus>
    
    @POST("v1/community/comment/comment-like")
    suspend fun likeComment(
        @Header("token") token: String,
        @Body request: LikeCommentRequest
    ): Response<ApiStatus>
    
    @POST("v1/community/posts/post-collect")
    suspend fun collectPost(
        @Header("token") token: String,
        @Body request: CollectPostRequest
    ): Response<ApiStatus>
    
    @POST("v1/community/posts/post-reward")
    suspend fun rewardPost(
        @Header("token") token: String,
        @Body request: RewardPostRequest
    ): Response<ApiStatus>
    
    @POST("v1/community/comment/comment")
    suspend fun commentPost(
        @Header("token") token: String,
        @Body request: CommentPostRequest
    ): Response<ApiStatus>

    @POST("v1/community/comment/delete")
    suspend fun deleteComment(
        @Header("token") token: String,
        @Body request: DeleteCommentRequest
    ): Response<ApiStatus>
    
    @POST("v1/community/posts/create")
    suspend fun createPost(
        @Header("token") token: String,
        @Body request: CreatePostRequest
    ): Response<com.yhchat.canary.data.model.CreatePostResponse>
    
    @POST("v1/community/posts/edit")
    suspend fun editPost(
        @Header("token") token: String,
        @Body request: EditPostRequest
    ): Response<com.yhchat.canary.data.model.EditPostResponse>
    
    @POST("v1/community/posts/delete")
    suspend fun deletePost(
        @Header("token") token: String,
        @Body request: DeletePostRequest
    ): Response<ApiStatus>

    @POST("v1/community/posts/post-move")
    suspend fun movePost(
        @Header("token") token: String,
        @Body request: MovePostRequest
    ): Response<ApiStatus>

    @POST("v1/community/posts/edit-sticky")
    suspend fun editPostSticky(
        @Header("token") token: String,
        @Body request: PostStickyRequest
    ): Response<ApiStatus>
    
    @POST("v1/community/search")
    suspend fun searchCommunity(
        @Header("token") token: String,
        @Body request: SearchRequest
    ): SearchResponse
    
    @POST("v1/community/ba/group-list")
    suspend fun getBoardGroupList(
        @Header("token") token: String,
        @Body request: GroupListRequest
    ): GroupListResponse
    
    /**
     * 创建分区
     */
    @POST("v1/community/ba/create")
    suspend fun createBoard(
        @Header("token") token: String,
        @Body request: CreateBoardRequest
    ): Response<CreateBoardResponse>
    
    /**
     * 屏蔽/取消屏蔽用户
     */
    @POST("v1/community/set-black-list")
    suspend fun setBlackList(
        @Header("token") token: String,
        @Body request: SetBlackListRequest
    ): Response<ApiStatus>
    
    /**
     * 获取屏蔽用户列表
     */
    @POST("v1/community/black-list")
    suspend fun getBlackList(
        @Header("token") token: String,
        @Body request: BlackListRequest
    ): Response<com.yhchat.canary.data.model.BlockedUserListResponse>
    
    /**
     * 举报文章
     */
    @POST("v1/community/report")
    suspend fun reportPost(
        @Header("token") token: String,
        @Body request: ReportPostRequest
    ): Response<ApiStatus>
    
    /**
     * 搜索聊天记录
     */
    @POST("v1/search/chat-search")
    suspend fun searchChatMessages(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.ChatSearchRequest
    ): Response<com.yhchat.canary.data.model.ChatSearchResponse>

    @POST("v1/search/chat-search")
    suspend fun searchChatMessagesRaw(
        @Header("token") token: String,
        @Body request: RequestBody
    ): Response<com.yhchat.canary.data.model.ChatSearchResponse>
    
    /**
     * 获取在线设备列表 (Protobuf)
     */
    @GET("v1/user/clients")
    suspend fun getOnlineDevices(
        @Header("token") token: String
    ): Response<okhttp3.ResponseBody>

    @POST("v1/user/invite/change-user-invite-code")
    suspend fun changeInviteCode(
        @Header("token") token: String,
        @Body request: ChangeInviteCodeRequest
    ): Response<Map<String, Any>>

    @POST("v1/user/edit-nickname")
    suspend fun editNickname(
        @Header("token") token: String,
        @Body requestBody: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 获取发现群聊分区列表
     */
    @POST("v1/user/recommend-category-list")
    suspend fun getRecommendCategoryList(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.RecommendCategoryRequest
    ): Response<com.yhchat.canary.data.model.RecommendCategoryResponse>
    
    /**
     * 获取发现群聊列表
     */
    @POST("v1/user/recommend-list")
    suspend fun getRecommendGroupList(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.RecommendGroupListRequest
    ): Response<com.yhchat.canary.data.model.RecommendGroupListResponse>
    
    /**
     * 获取机器人推荐列表
     */
    @POST("v1/user/recommend")
    suspend fun getRecommendBotList(
        @Header("token") token: String
    ): Response<com.yhchat.canary.data.model.RecommendBotListResponse>


    /**
     * 添加好友/加入群聊/机器人
     */
    @POST("v1/friend/apply")
    suspend fun addFriend(
        @Header("token") token: String,
        @Body request: AddFriendRequest
    ): Response<ApiStatus>
    
    /**
     * 删除好友/群聊/机器人
     */
    @POST("v1/friend/delete-friend")
    suspend fun deleteFriend(
        @Header("token") token: String,
        @Body request: DeleteFriendRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 处理好友申请
     */
    @POST("v1/friend/agree-apply")
    suspend fun agreeApply(
        @Header("token") token: String,
        @Body request: AgreeApplyRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>

    @POST("v1/group/agree-invite")
    suspend fun agreeInvite(
        @Header("token") token: String,
        @Body request: AgreeApplyRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 获取所有聊天对象（通讯录）- 使用protobuf
     */
    @POST("v1/friend/address-book-list")
    suspend fun getAddressBookList(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>

    @POST("v1/friend/request-list")
    suspend fun getFriendRequestList(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>

    @POST("v1/friend/no-notify")
    suspend fun setNoNotify(
        @Header("token") token: String,
        @Body request: NoNotifyRequest
    ): Response<ApiStatus>
    
    // ========== 表情相关API ==========
    
    /**
     * 添加个人表情收藏
     */
    @POST("v1/expression/add")
    suspend fun addExpression(
        @Header("token") token: String,
        @Body request: AddExpressionRequest
    ): Response<ApiStatus>
    
    // 分享链接相关API
    @POST("v1/share/info")
    suspend fun getShareInfo(
        @Header("token") token: String,
        @Body request: ShareInfoRequest
    ): Response<ShareInfoResponse>
    
    @POST("v1/friend/apply")
    suspend fun applyFriend(
        @Header("token") token: String,
        @Body request: FriendApplyRequest
    ): Response<ApiStatus>
    
    // ========== 表情包相关API ==========
    
    /**
     * 获取表情包详情
     */
    @POST("v1/sticker/detail")
    suspend fun getStickerPackDetail(
        @Header("token") token: String,
        @Body request: StickerPackDetailRequest
    ): Response<Map<String, Any>>
    
    /**
     * 添加表情包到收藏
     */
    @POST("v1/sticker/add")
    suspend fun addStickerPack(
        @Header("token") token: String,
        @Body request: AddStickerPackRequest
    ): Response<ApiStatus>
    
    // ========== Misc相关API ==========
    
    /**
     * 获取七牛图片上传token
     */
    @GET("v1/misc/qiniu-token")
    suspend fun getQiniuImageToken(
        @Header("token") token: String
    ): Response<QiniuTokenResponse>
    
    /**
     * 获取文件上传token
     * GET /v1/misc/qiniu-token2
     */
    @GET("v1/misc/qiniu-token2")
    suspend fun getQiniuFileToken(
        @Header("token") token: String
    ): Response<QiniuTokenResponse>
    
    /**
     * 获取视频上传token
     * GET /v1/misc/qiniu-token-video
     */
    @GET("v1/misc/qiniu-token-video")
    suspend fun getQiniuVideoToken(
        @Header("token") token: String
    ): Response<QiniuTokenResponse>
    
    // ========== 群组标签相关API ==========
    
    /**
     * 获取群组标签列表
     */
    @POST("v1/group-tag/list")
    suspend fun getGroupTagList(
        @Header("token") token: String,
        @Body request: GroupTagListRequest
    ): Response<GroupTagListResponse>
    
    /**
     * 创建群组标签
     */
    @POST("v1/group-tag/create")
    suspend fun createGroupTag(
        @Header("token") token: String,
        @Body request: CreateGroupTagRequest
    ): Response<ApiStatus>
    
    /**
     * 编辑群组标签
     */
    @POST("v1/group-tag/edit")
    suspend fun editGroupTag(
        @Header("token") token: String,
        @Body request: EditGroupTagRequest
    ): Response<ApiStatus>
    
    /**
     * 删除群组标签
     */
    @POST("v1/group-tag/delete")
    suspend fun deleteGroupTag(
        @Header("token") token: String,
        @Body request: DeleteGroupTagRequest
    ): Response<ApiStatus>

    /**
     * 标签排序
     */
    @POST("v1/group-tag/sort")
    suspend fun sortGroupTag(
        @Header("token") token: String,
        @Body request: SortGroupTagRequest
    ): Response<ApiStatus>
    
    /**
     * 关联用户标签
     */
    @POST("v1/group-tag/relate")
    suspend fun relateUserTag(
        @Header("token") token: String,
        @Body request: RelateUserTagRequest
    ): Response<ApiStatus>
    
    /**
     * 取消关联用户标签
     */
    @POST("v1/group-tag/relate-cancel")
    suspend fun cancelRelateUserTag(
        @Header("token") token: String,
        @Body request: RelateUserTagRequest
    ): Response<ApiStatus>
    
    /**
     * 设置我的群昵称
     */
    @POST("v1/group/edit-my-group-nickname")
    suspend fun editMyGroupNickname(
        @Header("token") token: String,
        @Body request: Map<String, String>
    ): Response<Map<String, Any>>
    
    /**
     * 设置群口令（仅群主）
     */
    @POST("v1/group/edit-group-keyword")
    suspend fun editGroupKeyword(
        @Header("token") token: String,
        @Body request: Map<String, String>
    ): Response<ApiStatus>
    
    /**
     * 获取标签绑定的用户列表（ProtoBuf）
     */
    @POST("v1/group-tag/members")
    suspend fun getTagMembers(
        @Header("token") token: String,
        @Body body: okhttp3.RequestBody
    ): Response<ResponseBody>
    
    /**
     * 搜索推荐群聊
     * POST /v1/group/recommend/list
     */
    @POST("v1/group/recommend/list")
    suspend fun searchRecommendGroups(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.SearchRecommendGroupRequest
    ): Response<com.yhchat.canary.data.model.SearchRecommendGroupResponse>
    
    /**
     * 获取群机器人列表（ProtoBuf，包含详细指令信息）
     * POST /v1/group/bot-list
     */
    @POST("v1/group/bot-list")
    suspend fun getGroupBotList(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>

    @POST("v1/group/edit-auto-delete-message")
    suspend fun editAutoDeleteMessage(
        @Header("token") token: String,
        @Body request: EditAutoDeleteMessageRequest
    ): Response<ApiStatus>
    
    /**
     * 获取群指令列表（JSON API）
     * POST /v1/group/instruction-list
     */
    @POST("v1/group/instruction-list")
    suspend fun getInstructionList(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.GroupIdRequest
    ): Response<com.yhchat.canary.data.model.InstructionListResponse>
    
    /**
     * 邀请加入群聊
     * POST /v1/group/invite
     */
    @POST("v1/group/invite")
    suspend fun inviteToGroup(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.InviteGroupRequest
    ): Response<com.yhchat.canary.data.model.ApiStatus>
    
    /**
     * 移除群聊机器人
     */
    @POST("v1/group/remove-bot")
    suspend fun removeGroupBot(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.RemoveGroupBotRequest
    ): Response<com.yhchat.canary.data.model.ApiStatus>
    
    /**
     * 创建群聊
     * POST /v1/group/create-group
     */
    @POST("v1/group/create-group")
    suspend fun createGroup(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 创建机器人
     * POST /v1/bot/create-bot
     */
    @POST("v1/bot/create-bot")
    suspend fun createBot(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 点击消息输入框上面按钮的反馈
     * POST /v1/menu/event
     */
    @POST("v1/menu/event")
    suspend fun menuEvent(
        @Header("token") token: String,
        @Body request: MenuEventRequest
    ): Response<BaseResponse>
    
    /**
     * 获取内测状态
     * POST /v1/beta/info
     */
    @POST("v1/beta/info")
    suspend fun getBetaInfo(
        @Header("token") token: String
    ): Response<com.yhchat.canary.data.model.BetaInfoResponse>
    
    /**
     * 设置机器人消息订阅接口
     * POST /v1/bot/edit-subscribed-link
     */
    @POST("v1/bot/edit-subscribed-link")
    suspend fun editBotSubscribedLink(
        @Header("token") token: String,
        @Body request: EditBotSubscribedLinkRequest
    ): Response<com.yhchat.canary.data.model.BaseResponse>
    
    /**
     * 停用/启用机器人
     * POST /v1/bot/stop-bot
     */
    @POST("v1/bot/stop-bot")
    suspend fun stopBot(
        @Header("token") token: String,
        @Body request: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 获取 WebDAV 挂载点列表
     * POST /v1/mount-setting/list
     */
    @POST("v1/mount-setting/list")
    suspend fun getMountSettingList(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.MountSettingRequest
    ): Response<com.yhchat.canary.data.model.MountSettingResponse>

    /**
     * 创建 WebDAV 挂载点
     * POST /v1/mount-setting/create
     */
    @POST("v1/mount-setting/create")
    suspend fun createMountSetting(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.MountSettingCreateRequest
    ): Response<com.yhchat.canary.data.model.ApiStatus>

    /**
     * 删除 WebDAV 挂载点
     * POST /v1/mount-setting/delete
     */
    @POST("v1/mount-setting/delete")
    suspend fun deleteMountSetting(
        @Header("token") token: String,
        @Body request: com.yhchat.canary.data.model.MountSettingDeleteRequest
    ): Response<com.yhchat.canary.data.model.ApiStatus>
    
    // ==================== 社区草稿相关API ====================
    
    /**
     * 获取草稿列表
     * GET /v1/community/drafts
     */
    @GET("v1/community/drafts")
    suspend fun getDraftList(
        @Header("token") token: String
    ): Response<DraftListResponse>
    
    /**
     * 保存草稿
     * POST /v1/community/draft/save
     */
    @POST("v1/community/draft/save")
    suspend fun saveDraft(
        @Header("token") token: String,
        @Body request: SaveDraftRequest
    ): Response<DraftResponse>
    
    /**
     * 更新草稿
     * POST /v1/community/draft/update
     */
    @POST("v1/community/draft/update")
    suspend fun updateDraft(
        @Header("token") token: String,
        @Body request: UpdateDraftRequest
    ): Response<DraftResponse>
    
    /**
     * 删除草稿
     * POST /v1/community/draft/delete
     */
    @POST("v1/community/draft/delete")
    suspend fun deleteDraft(
        @Header("token") token: String,
        @Body request: DeleteDraftRequest
    ): Response<BaseResponse>
}

// ==================== 社区相关请求模型（JSON） ====================

data class RecommendPostListRequest(
    @SerializedName("size")
    val size: Int = 20,

    @SerializedName("page")
    val page: Int = 1
)

/**
 * 列表消息请求
 */
data class ListMessageRequest(
    @SerializedName("msg_count")
    val msgCount: Int,
    
    @SerializedName("msg_id")
    val msgId: String = "",
    
    @SerializedName("chat_type")
    val chatType: Int,
    
    @SerializedName("chat_id")
    val chatId: String
)

/**
 * 按序列列表消息请求
 */
data class ListMessageBySeqRequest(
    @SerializedName("msg_start")
    val msgStart: Long = 0,
    
    @SerializedName("chat_type")
    val chatType: Int,
    
    @SerializedName("chat_id")
    val chatId: String
)

/**
 * 按消息ID列表消息请求
 */
data class ListMessageByMidSeqRequest(
    @SerializedName("request_id")
    val requestId: Long? = null,
    
    @SerializedName("msg_seq")
    val msgSeq: Long = 0L,
    
    @SerializedName("chat_type")
    val chatType: Int,
    
    @SerializedName("chat_id")
    val chatId: String,
    
    @SerializedName("unknown")
    val unknown: Int = 0,
    
    @SerializedName("msg_count")
    val msgCount: Int,
    
    @SerializedName("msg_id")
    val msgId: String
)

/**
 * 编辑消息请求
 */
data class EditMessageRequest(
    @SerializedName("msg_id")
    val msgId: String,
    
    @SerializedName("chat_id")
    val chatId: String,
    
    @SerializedName("chat_type")
    val chatType: Int,
    
    @SerializedName("content_type")
    val contentType: Int,
    
    @SerializedName("content")
    val content: MessageContent,
    
    @SerializedName("quote_msg_id")
    val quoteMsgId: String? = null
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
data class RecallMessagesBatchRequest(
    @SerializedName("msg_id")
    val msgIds: List<String>,
    
    @SerializedName("chat_id")
    val chatId: String,
    
    @SerializedName("chat_type")
    val chatType: Int
)

/**
 * 按钮报告请求
 */
data class ButtonReportRequest(
    @SerializedName("msg_id")
    val msgId: String,
    
    @SerializedName("chat_type")
    val chatType: Int,
    
    @SerializedName("chat_id")
    val chatId: String,
    
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("button_value")
    val buttonValue: String
)

/**
 * 消息编辑记录请求
 */
data class ListMessageEditRecordRequest(
    @SerializedName("msgId")
    val msgId: String,
    
    @SerializedName("size")
    val size: Int = 10,
    
    @SerializedName("page")
    val page: Int = 1
)

data class CollectPostListRequest(
    @SerializedName("size")
    val size: Int = 20,
    @SerializedName("page")
    val page: Int = 1
)

/**
 * 文件下载记录请求
 */
data class FileDownloadRecordRequest(
    @SerializedName("msgId")
    val msgId: String,
    
    @SerializedName("downloadPath")
    val downloadPath: String
)

/**
 * 分区列表请求
 */
data class BoardListRequest(
    @SerializedName("typ")
    val typ: Int = 2,
    
    @SerializedName("size")
    val size: Int = 20,
    
    @SerializedName("page")
    val page: Int = 1
)

/**
 * 分区信息请求
 */
data class BoardInfoRequest(
    @SerializedName("id")
    val id: Int
)

data class ManageBoardRequest(
    @SerializedName("baId")
    val baId: Int,
    @SerializedName("visibleRange")
    val visibleRange: Int,
    @SerializedName("publishAuthority")
    val publishAuthority: Int
)

data class EditBoardRequest(
    @SerializedName("baId")
    val baId: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("avatar")
    val avatar: String
)

data class DeleteBoardRequest(
    @SerializedName("baId")
    val baId: Int
)

data class BaFollowerListRequest(
    @SerializedName("id")
    val id: Int,
    @SerializedName("size")
    val size: Int = 20,
    @SerializedName("page")
    val page: Int = 1,
    @SerializedName("memberName")
    val memberName: String = ""
)

data class BaManageSettingRequest(
    @SerializedName("baId")
    val baId: Int,
    @SerializedName("userId")
    val userId: String,
    @SerializedName("userLevel")
    val userLevel: Int
)

data class BoardsByCreateRequest(
    @SerializedName("userId")
    val userId: String
)

data class EditAutoDeleteMessageRequest(
    @SerializedName("groupId")
    val groupId: String,
    @SerializedName("autoDeleteMessage")
    val autoDeleteMessage: Int
)

data class NoNotifyRequest(
    @SerializedName("chatId")
    val chatId: String,
    @SerializedName("noNotify")
    val noNotify: Int
)

/**
 * 文章列表请求
 */
data class PostListRequest(
    @SerializedName("typ")
    val typ: Int = 1,
    
    @SerializedName("baId")
    val baId: Int,
    
    @SerializedName("size")
    val size: Int = 20,
    
    @SerializedName("page")
    val page: Int = 1
)

/**
 * 文章详情请求
 */
data class PostDetailRequest(
    @SerializedName("id")
    val id: Int
)

/**
 * 评论列表请求
 */
data class CommentListRequest(
    @SerializedName("postId")
    val postId: Int,
    
    @SerializedName("size")
    val size: Int = 10,
    
    @SerializedName("page")
    val page: Int = 1
)

/**
 * 点赞文章请求
 */
data class LikePostRequest(
    @SerializedName("id")
    val id: Int
)

/**
 * 点赞评论请求
 */
data class LikeCommentRequest(
    @SerializedName("id")
    val id: Int // 评论ID
)

/**
 * 收藏文章请求
 */
data class CollectPostRequest(
    @SerializedName("id")
    val id: Int
)

/**
 * 打赏文章请求
 */
data class RewardPostRequest(
    @SerializedName("postId")
    val postId: Int,
    
    @SerializedName("amount")
    val amount: Double
)

/**
 * 评论文章请求
 */
data class CommentPostRequest(
    @SerializedName("postId")
    val postId: Int,
    
    @SerializedName("commentId")
    val commentId: Int = 0,
    
    @SerializedName("content")
    val content: String
)

/**
 * 删除评论请求
 */
data class DeleteCommentRequest(
    @SerializedName("commentId")
    val commentId: Int
)

/**
 * 创建文章请求
 */
data class CreatePostRequest(
    @SerializedName("baId")
    val baId: Int,

    @SerializedName("groupId")
    val groupId: String = "",

    @SerializedName("title")
    val title: String,

    @SerializedName("content")
    val content: String,

    @SerializedName("contentType")
    val contentType: Int  // 1-文本，2-markdown
)

/**
 * 编辑文章请求
 */
data class EditPostRequest(
    @SerializedName("postId")
    val postId: Int,

    @SerializedName("title")
    val title: String,

    @SerializedName("content")
    val content: String,

    @SerializedName("contentType")
    val contentType: Int  // 1-文本，2-markdown
)

/**
 * 删除文章请求
 */
data class DeletePostRequest(
    @SerializedName("postId")
    val postId: Int
)

data class MovePostRequest(
    @SerializedName("baId")
    val baId: Int,
    @SerializedName("id")
    val id: Int
)

data class PostStickyRequest(
    @SerializedName("postId")
    val postId: Int
)

/**
 * 搜索社区请求
 */
data class SearchRequest(
    @SerializedName("typ")
    val typ: Int = 3,
    
    @SerializedName("keyword")
    val keyword: String,
    
    @SerializedName("size")
    val size: Int = 50,
    
    @SerializedName("page")
    val page: Int = 1
)

/**
 * 搜索社区响应
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
    @SerializedName("ba")
    val boards: List<com.yhchat.canary.data.model.CommunityBoard>? = emptyList(),
    
    @SerializedName("posts") 
    val posts: List<com.yhchat.canary.data.model.CommunityPost>? = emptyList()
)

/**
 * 群聊列表请求
 */
data class GroupListRequest(
    @SerializedName("baId")
    val baId: Int,
    
    @SerializedName("size")
    val size: Int = 20,
    
    @SerializedName("page")
    val page: Int = 1
)

/**
 * 群聊列表响应
 */
data class GroupListResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: GroupListData,
    
    @SerializedName("msg")
    val msg: String
)

/**
 * 我的文章列表请求
 */
data class MyPostListRequest(
    @SerializedName("size")
    val size: Int = 20,
    
    @SerializedName("page")
    val page: Int = 1
)

/**
 * 群聊列表数据
 */
data class GroupListData(
    @SerializedName("groups")
    val groups: List<com.yhchat.canary.data.model.CommunityGroup>,
    
    @SerializedName("total")
    val total: Int
)

/**
 * 关注分区请求
 */
data class FollowBoardRequest(
    @SerializedName("baId")
    val baId: Int,
    
    @SerializedName("followSource")
    val followSource: Int = 2
)

/**
 * 取消关注分区请求
 */
data class UnfollowBoardRequest(
    @SerializedName("baId")
    val baId: Int
)

/**
 * 七牛上传token响应
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
 * 七牛上传响应
 */
data class QiniuUploadResponse(
    @SerializedName("key")
    val key: String,
    
    @SerializedName("hash")
    val hash: String,
    
    @SerializedName("fsize")
    val fsize: Long,
    
    @SerializedName("avinfo")
    val avinfo: QiniuAvInfo? = null
)

data class QiniuAvInfo(
    @SerializedName("video")
    val video: QiniuVideoInfo? = null
)

data class QiniuVideoInfo(
    @SerializedName("width")
    val width: Int = 0,
    
    @SerializedName("height")
    val height: Int = 0
)

/**
 * 编辑机器人请求
 */
data class EditBotRequest(
    @SerializedName("botId")
    val botId: String,
    
    @SerializedName("nickname")
    val nickname: String,
    
    @SerializedName("introduction")
    val introduction: String,
    
    @SerializedName("avatarUrl")
    val avatarUrl: String,
    
    @SerializedName("private")
    val private: Int
)

// ========== 群组标签相关数据模型 ==========

/**
 * 群组标签列表请求
 */
data class GroupTagListRequest(
    @SerializedName("groupId")
    val groupId: String,
    @SerializedName("size")
    val size: Int = 50,
    @SerializedName("page")
    val page: Int = 1,
    @SerializedName("tag")
    val tag: String = ""
)

/**
 * 群组标签列表响应
 */
data class GroupTagListResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("data")
    val data: GroupTagListData?,
    @SerializedName("msg")
    val msg: String
)

data class GroupTagListData(
    @SerializedName("list")
    val list: List<GroupTag>
)

/**
 * 群组标签
 */
data class GroupTag(
    @SerializedName("id")
    val id: Long,
    @SerializedName("groupId")
    val groupId: String,
    @SerializedName("tag")
    val tag: String,
    @SerializedName("color")
    val color: String,
    @SerializedName("desc")
    val desc: String = "",
    @SerializedName("sort")
    val sort: Int = 0,
    @SerializedName("delFlag")
    val delFlag: Int = 0,
    @SerializedName("createTime")
    val createTime: Long = 0
)

/**
 * 创建群组标签请求
 */
data class CreateGroupTagRequest(
    @SerializedName("groupId")
    val groupId: String,
    @SerializedName("tag")
    val tag: String,
    @SerializedName("color")
    val color: String,
    @SerializedName("desc")
    val desc: String = "",
    @SerializedName("sort")
    val sort: Int = 0
)

/**
 * 编辑群组标签请求
 */
data class EditGroupTagRequest(
    @SerializedName("id")
    val id: Long,
    @SerializedName("groupId")
    val groupId: String,
    @SerializedName("tag")
    val tag: String,
    @SerializedName("color")
    val color: String,
    @SerializedName("desc")
    val desc: String = "",
    @SerializedName("sort")
    val sort: Int = 0
)

/**
 * 删除群组标签请求
 */
data class DeleteGroupTagRequest(
    @SerializedName("id")
    val id: Long
)

/**
 * 标签排序请求
 */
data class SortGroupTagRequest(
    @SerializedName("sort")
    val sort: String,
    @SerializedName("groupId")
    val groupId: String
)

/**
 * 标签排序项
 */
data class SortGroupTagItem(
    @SerializedName("groupId")
    val groupId: String,
    @SerializedName("tagId")
    val tagId: String,
    @SerializedName("sort")
    val sort: String
)

/**
 * 菜单按钮点击事件请求
 */
data class MenuEventRequest(
    @SerializedName("id")
    val id: Long, // 按钮id
    @SerializedName("chatId")
    val chatId: String, // 聊天id
    @SerializedName("chatType")
    val chatType: Int, // 会话类型
    @SerializedName("value")
    val value: String = "" // 按钮的值
)

/**
 * 关联用户标签请求
 */
data class RelateUserTagRequest(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("tagGroupId")
    val tagGroupId: Long
)

/**
 * 设置机器人消息订阅接口请求
 */
data class EditBotSubscribedLinkRequest(
    @SerializedName("botId")
    val botId: String,
    @SerializedName("link")
    val link: String,
    @SerializedName("subscribeType")
    val subscribeType: Int
)

/**
 * 屏蔽/取消屏蔽用户请求
 */
data class SetBlackListRequest(
    @SerializedName("isAdd")
    val isAdd: Int, // 0-取消屏蔽，1-屏蔽
    @SerializedName("authorId")
    val authorId: String
)

/**
 * 获取屏蔽用户列表请求
 */
data class BlackListRequest(
    @SerializedName("size")
    val size: Int = 20,
    @SerializedName("page")
    val page: Int = 1
)

/**
 * 创建分区请求
 */
data class CreateBoardRequest(
    @SerializedName("name")
    val name: String, // 分区名称
    @SerializedName("avatar")
    val avatar: String // 分区头像url
)

/**
 * 创建分区响应
 */
data class CreateBoardResponse(
    val code: Int,
    val data: CreateBoardData,
    val msg: String
)

data class CreateBoardData(
    val id: Int // 分区id
)

/**
 * 举报文章请求
 */
data class ReportPostRequest(
    @SerializedName("typ")
    val typ: Int = 1, // 类型默认1
    @SerializedName("id")
    val id: Int, // 文章id
    @SerializedName("content")
    val content: String, // 补充说明
    @SerializedName("reason")
    val reason: String = "", // 举报原因
    @SerializedName("url")
    val url: String? = null // 举报图片url，可选
)

data class BotGroupPermissionEditRequest(
    @SerializedName("groupId")
    val groupId: String,
    @SerializedName("botId")
    val botId: String,
    @SerializedName("allowEditGroupInfo")
    val allowEditGroupInfo: Int,
    @SerializedName("allowGagMember")
    val allowGagMember: Int,
    @SerializedName("allowRemoveMember")
    val allowRemoveMember: Int,
    @SerializedName("allowGroupTagManage")
    val allowGroupTagManage: Int
)

data class BotGroupPermissionGetRequest(
    @SerializedName("botId")
    val botId: String,
    @SerializedName("groupId")
    val groupId: String
)

data class BotGroupPermissionGetResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("data")
    val data: BotGroupPermissionData?,
    @SerializedName("msg")
    val msg: String
)

data class BotGroupPermissionData(
    @SerializedName("allowEditGroupInfo")
    val allowEditGroupInfo: Int,
    @SerializedName("allowGagMember")
    val allowGagMember: Int,
    @SerializedName("allowRemoveMember")
    val allowRemoveMember: Int,
    @SerializedName("allowGroupTagManage")
    val allowGroupTagManage: Int
)
