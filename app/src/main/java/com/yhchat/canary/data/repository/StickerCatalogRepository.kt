package com.yhchat.canary.data.repository

import android.util.Log
import com.yhchat.canary.data.model.StickerCatalogPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 云湖表情包目录（第三方）。
 *
 * 地址：https://sticker.vote1.cc.cd
 * 文档：https://sticker.vote1.cc.cd/api/docs
 *
 * 注意这是**第三方服务**，不是云湖官方接口：
 *   - 不需要 token，也没有鉴权；
 *   - 数据是社区维护的索引，可能有延迟或缺失；
 *   - 任何一步失败都只返回空列表，不能让用户详情页整个报错。
 *
 * 用 org.json 手工解析而不是 Gson：这个接口的 `data` 里字段名是 snake_case，
 * 而且将来可能只增不减。手工解析可以做到「缺字段就当没有」，不会因为
 * 服务端多返回一个没见过的字段或少了某个可选字段就抛异常。
 */
class StickerCatalogRepository {

    companion object {
        private const val TAG = "StickerCatalog"
        private const val BASE_URL = "https://sticker.vote1.cc.cd"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 查询某用户创建的全部表情包。
     *
     * @param userId 云湖用户 ID
     * @return 失败时返回空列表 —— 调用方（用户详情页）不应该因为第三方服务抖动而报错
     */
    suspend fun getPacksByAuthor(userId: String): List<StickerCatalogPack> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext emptyList()

        return@withContext try {
            val url = "$BASE_URL/api/v1/users/${java.net.URLEncoder.encode(userId, "UTF-8")}/packs"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                // 第三方服务，带个明确的 UA 方便对方排查问题
                .header("User-Agent", "YhchatCanary/Android")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "查询作者表情包失败: HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()

                parsePacks(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "查询作者表情包异常: ${e.javaClass.simpleName} ${e.message}")
            emptyList()
        }
    }

    private fun parsePacks(json: String): List<StickerCatalogPack> {
        return try {
            val root = JSONObject(json)
            val array = root.optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val authorObj = item.optJSONObject("author")
                    add(
                        StickerCatalogPack(
                            id = item.optInt("id", 0),
                            name = item.optString("name", ""),
                            author = authorObj?.let {
                                com.yhchat.canary.data.model.StickerCatalogAuthor(
                                    name = it.optString("name", ""),
                                    userId = it.optString("user_id", "")
                                )
                            },
                            stickerCount = item.optInt("sticker_count", 0),
                            createdAt = item.optString("created_at", ""),
                            createdUnix = item.optLong("created_unix", 0L)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "解析表情包响应失败: ${e.message}")
            emptyList()
        }
    }
}
