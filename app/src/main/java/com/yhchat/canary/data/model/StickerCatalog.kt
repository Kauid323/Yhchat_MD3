package com.yhchat.canary.data.model

import com.google.gson.annotations.SerializedName

/**
 * 云湖表情包目录（第三方服务 sticker.vote1.cc.cd）的一条表情包记录。
 *
 * 接口：`GET /api/v1/users/{user_id}/packs`
 * 响应：`{"total":N,"data":[ ... ]}`，字段见下方。
 *
 * 这是第三方 API，与云湖官方接口无关，也不需要 token。
 */
data class StickerCatalogPack(
    @SerializedName("id")
    val id: Int = 0,
    @SerializedName("name")
    val name: String = "",
    @SerializedName("author")
    val author: StickerCatalogAuthor? = null,
    @SerializedName("sticker_count")
    val stickerCount: Int = 0,
    @SerializedName("created_at")
    val createdAt: String = "",
    @SerializedName("created_unix")
    val createdUnix: Long = 0L
)

data class StickerCatalogAuthor(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("user_id")
    val userId: String = ""
)

data class StickerCatalogResponse(
    @SerializedName("total")
    val total: Int = 0,
    @SerializedName("data")
    val data: List<StickerCatalogPack>? = null
)
