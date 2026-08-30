package com.yhchat.canary.data.model

import com.google.gson.annotations.SerializedName

/**
 * 贴纸包列表响应
 */
data class StickerPackListResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: StickerPackListData,
    
    @SerializedName("msg")
    val msg: String
)

data class StickerPackListData(
    @SerializedName("stickerPacks")
    val stickerPacks: List<StickerPack>
)

/**
 * 贴纸包
 */
data class StickerPack(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("name")
    val name: String,  // 表情包名称
    
    @SerializedName("createBy")
    val createBy: String,  // 创建者ID
    
    @SerializedName("createTime")
    val createTime: Long,  // 创建时间戳
    
    @SerializedName("delFlag")
    val delFlag: Int = 0,
    
    @SerializedName("userCount")
    val userCount: Int = 0,  // 使用人数
    
    @SerializedName("hot")
    val hot: Int = 0,  // 热度
    
    @SerializedName("uuid")
    val uuid: String = "",
    
    @SerializedName("updateTime")
    val updateTime: Long = 0,  // 更新时间戳
    
    @SerializedName("sort")
    val sort: Int = 0,
    
    @SerializedName("stickerItems")
    val stickerItems: List<StickerItem> = emptyList()
)

/**
 * 贴纸项
 */
data class StickerItem(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("name")
    val name: String,  // 表情名称
    
    @SerializedName("url")
    val url: String,  // 表情URL, 需要前面加上 https://chat-img.jwznb.com/
    
    @SerializedName("stickerPackId")
    val stickerPackId: Long,  // 所属表情包ID
    
    @SerializedName("createBy")
    val createBy: String,  // 创建者ID
    
    @SerializedName("createTime")
    val createTime: Long,  // 创建时间戳
    
    @SerializedName("delFlag")
    val delFlag: Int = 0
) {
    // 获取完整URL
    fun getFullUrl(): String {
        return if (url.startsWith("http")) url else "https://chat-img.jwznb.com/$url"
    }
}

/**
 * 贴纸包详情响应
 */
data class StickerPackDetailResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: StickerPackDetailData,
    
    @SerializedName("msg")
    val msg: String
)

data class StickerPackDetailData(
    @SerializedName("stickerPack")
    val stickerPack: StickerPack,
    
    @SerializedName("user")
    val user: StickerPackCreator?
)

data class StickerPackCreator(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("nickname")
    val nickname: String,
    
    @SerializedName("avatar_url")
    val avatarUrl: String
)

/**
 * 贴纸包操作请求
 */
data class StickerPackActionRequest(
    @SerializedName("id")
    val id: Long
)

/**
 * 贴纸包排序请求
 */
data class StickerPackSortRequest(
    @SerializedName("sort")
    val sort: String  // JSON字符串格式的排序数组
)

/**
 * 创建表情包请求
 */
data class CreateStickerPackRequest(
    @SerializedName("name")
    val name: String
)

/**
 * 创建表情包响应
 */
data class CreateStickerPackResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: CreateStickerPackData? = null,
    
    @SerializedName("msg")
    val msg: String? = null
)

data class CreateStickerPackData(
    @SerializedName("id")
    val id: Long
)

/**
 * 重命名表情包请求
 */
data class RenameStickerPackRequest(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("name")
    val name: String
)

/**
 * 添加表情请求
 */
data class AddStickerRequest(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("url")
    val url: String,
    
    @SerializedName("stickerPackId")
    val stickerPackId: Long
)

/**
 * 重命名表情请求
 */
data class RenameStickerRequest(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("name")
    val name: String
)


