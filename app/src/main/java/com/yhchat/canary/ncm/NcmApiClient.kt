package com.yhchat.canary.ncm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 网易云音乐 API 客户端
 * 提供歌曲搜索、直链解析、歌曲详情以及听歌识曲 HTTP 接口交互
 */
object NcmApiClient {

    private const val TAG = "NcmApiClient"
    private const val LINUX_FORWARD_URL = "https://music.163.com/api/linux/forward"
    private const val AUDIO_MATCH_URL = "https://interface.music.163.com/eapi/music/audio/match"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 单曲搜索
     * @param keyword 搜索关键词
     * @param limit 每页数量
     * @param offset 偏移量
     */
    suspend fun searchSongs(
        keyword: String,
        limit: Int = 30,
        offset: Int = 0
    ): Result<List<NcmSong>> = withContext(Dispatchers.IO) {
        try {
            if (keyword.isBlank()) {
                return@withContext Result.success(emptyList())
            }

            val paramsJson = JSONObject().apply {
                put("s", keyword.trim())
                put("type", 1) // 1: 单曲
                put("limit", limit)
                put("offset", offset)
                put("total", true)
            }

            val reqJson = JSONObject().apply {
                put("method", "POST")
                put("url", "https://music.163.com/api/cloudsearch/pc")
                put("params", paramsJson)
            }

            val encrypted = NcmCrypto.encryptLinuxApi(reqJson.toString())
            val formBody = FormBody.Builder()
                .add("eparams", encrypted)
                .build()

            val request = Request.Builder()
                .url(LINUX_FORWARD_URL)
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36")
                .addHeader("Referer", "https://music.163.com")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext Result.success(emptyList())
                val json = JSONObject(bodyStr)
                if (json.optInt("code", -1) != 200) {
                    return@withContext Result.success(emptyList())
                }

                val resultObj = json.optJSONObject("result") ?: return@withContext Result.success(emptyList())
                val songsArray = resultObj.optJSONArray("songs") ?: return@withContext Result.success(emptyList())

                val songs = mutableListOf<NcmSong>()
                for (i in 0 until songsArray.length()) {
                    val songObj = songsArray.getJSONObject(i)
                    val id = songObj.optLong("id")
                    val name = songObj.optString("name", "未知单曲")
                    val duration = songObj.optLong("dt", 0L)

                    // 艺术家列表
                    val arArray = songObj.optJSONArray("ar")
                    val artistIds = mutableListOf<Long>()
                    val artistName = buildString {
                        if (arArray != null) {
                            for (j in 0 until arArray.length()) {
                                val ar = arArray.getJSONObject(j)
                                val aId = ar.optLong("id", 0L)
                                if (aId > 0L) artistIds.add(aId)
                                val aName = ar.optString("name")
                                if (aName.isNotBlank()) {
                                    if (isNotEmpty()) append(" / ")
                                    append(aName)
                                }
                            }
                        }
                    }

                    // 专辑
                    val alObj = songObj.optJSONObject("al")
                    val albumName = alObj?.optString("name") ?: ""
                    var picUrl = alObj?.optString("picUrl") ?: ""
                    if (picUrl.startsWith("http://")) {
                        picUrl = "https://" + picUrl.removePrefix("http://")
                    }

                    songs.add(
                        NcmSong(
                            id = id,
                            name = name,
                            artist = artistName,
                            album = albumName,
                            coverUrl = picUrl,
                            durationMs = duration,
                            artistIds = artistIds
                        )
                    )
                }

                Result.success(songs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "搜索网易云单曲失败", e)
            Result.failure(e)
        }
    }

    /**
     * 解析歌曲播放直链
     * @param songId 歌曲 ID
     * @param level 音质等级: standard(标准), higher(极高), exhigh(高品), lossless(无损), hires(Hi-Res)
     */
    suspend fun getSongPlayUrl(
        songId: Long,
        level: String = "standard"
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val paramsJson = JSONObject().apply {
                put("ids", JSONArray().put(songId))
                put("level", level)
                put("encodeType", "mp3")
            }

            val reqJson = JSONObject().apply {
                put("method", "POST")
                put("url", "https://music.163.com/api/song/enhance/player/url/v1")
                put("params", paramsJson)
            }

            val encrypted = NcmCrypto.encryptLinuxApi(reqJson.toString())
            val formBody = FormBody.Builder()
                .add("eparams", encrypted)
                .build()

            val request = Request.Builder()
                .url(LINUX_FORWARD_URL)
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36")
                .addHeader("Referer", "https://music.163.com")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext Result.success(null)
                val json = JSONObject(bodyStr)
                if (json.optInt("code", -1) != 200) {
                    return@withContext Result.success(null)
                }

                val dataArray = json.optJSONArray("data") ?: return@withContext Result.success(null)
                if (dataArray.length() == 0) return@withContext Result.success(null)

                val firstItem = dataArray.getJSONObject(0)
                var url = firstItem.optString("url")
                if (url.isNullOrBlank() || url == "null") {
                    return@withContext Result.success(null)
                }

                if (url.startsWith("http://")) {
                    url = "https://" + url.removePrefix("http://")
                }

                Result.success(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析网易云单曲直链失败: $songId", e)
            Result.failure(e)
        }
    }

    /**
     * 批量获取歌曲详情（包含封面图片）
     * @param songIds 歌曲 ID 列表
     */
    suspend fun getSongDetail(songIds: List<Long>): Result<List<NcmSong>> = withContext(Dispatchers.IO) {
        try {
            if (songIds.isEmpty()) return@withContext Result.success(emptyList())

            val cArray = JSONArray()
            for (id in songIds) {
                cArray.put(JSONObject().apply { put("id", id) })
            }

            val paramsJson = JSONObject().apply {
                put("c", cArray.toString())
                put("ids", JSONArray(songIds).toString())
            }

            val reqJson = JSONObject().apply {
                put("method", "POST")
                put("url", "https://music.163.com/api/v3/song/detail")
                put("params", paramsJson)
            }

            val encrypted = NcmCrypto.encryptLinuxApi(reqJson.toString())
            val formBody = FormBody.Builder()
                .add("eparams", encrypted)
                .build()

            val request = Request.Builder()
                .url(LINUX_FORWARD_URL)
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36")
                .addHeader("Referer", "https://music.163.com")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext Result.success(emptyList())
                val json = JSONObject(bodyStr)
                if (json.optInt("code", -1) != 200) {
                    return@withContext Result.success(emptyList())
                }

                val songsArray = json.optJSONArray("songs") ?: return@withContext Result.success(emptyList())
                val songs = mutableListOf<NcmSong>()

                for (i in 0 until songsArray.length()) {
                    val sObj = songsArray.getJSONObject(i)
                    val id = sObj.optLong("id")
                    val name = sObj.optString("name", "未知单曲")
                    val duration = sObj.optLong("dt", 0L)

                    val arArray = sObj.optJSONArray("ar")
                    val artistIds = mutableListOf<Long>()
                    val artistName = buildString {
                        if (arArray != null) {
                            for (j in 0 until arArray.length()) {
                                val ar = arArray.getJSONObject(j)
                                val aId = ar.optLong("id", 0L)
                                if (aId > 0L) artistIds.add(aId)
                                val aName = ar.optString("name")
                                if (aName.isNotBlank()) {
                                    if (isNotEmpty()) append(" / ")
                                    append(aName)
                                }
                            }
                        }
                    }

                    val alObj = sObj.optJSONObject("al")
                    val albumName = alObj?.optString("name") ?: ""
                    var picUrl = alObj?.optString("picUrl") ?: ""
                    if (picUrl.startsWith("http://")) {
                        picUrl = "https://" + picUrl.removePrefix("http://")
                    }

                    songs.add(
                        NcmSong(
                            id = id,
                            name = name,
                            artist = artistName,
                            album = albumName,
                            coverUrl = picUrl,
                            durationMs = duration,
                            artistIds = artistIds
                        )
                    )
                }

                Result.success(songs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取网易云单曲详情失败", e)
            Result.failure(e)
        }
    }

    /**
     * 听歌识曲音频指纹识别 (使用 EAPI 接口与加密协议)
     * @param durationSec 音频时长（秒）
     * @param audioFP Base64 音频指纹
     */
    suspend fun matchAudioFingerprint(
        durationSec: Int,
        audioFP: String
    ): Result<NcmAudioMatchResult?> = withContext(Dispatchers.IO) {
        try {
            val safeDuration = String.format(java.util.Locale.US, "%.1f", durationSec.toDouble().coerceIn(1.0, 15.0))
            val sessionId = java.util.UUID.randomUUID().toString()

            val dataJson = JSONObject().apply {
                put("duration", safeDuration)
                put("times", "4")
                put("rawdata", audioFP)
                put("sessionId", sessionId)
                put("algorithmCode", "shazam_v2")
                put("header", "{}")
                put("e_r", "true")
            }

            // EAPI 加密请求体: params
            val encryptedParams = NcmCrypto.encryptEApi("/api/music/audio/match", dataJson.toString())
            val formBody = FormBody.Builder()
                .add("params", encryptedParams)
                .build()

            val request = Request.Builder()
                .url(AUDIO_MATCH_URL)
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; 23013RK75C Build/UKQ1.230804.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.230 Mobile Safari/537.36 NeteaseMusic/9.1.65")
                .addHeader("Referer", "https://interface.music.163.com")
                .addHeader("Cookie", "os=android; appver=9.1.65; osver=14")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val respBytes = response.body?.bytes() ?: return@withContext Result.success(null)
                val decryptedJsonStr = NcmCrypto.decryptEApi(respBytes)
                // Log.d(TAG, "EAPI 识曲解密响应: $decryptedJsonStr")

                if (decryptedJsonStr.isBlank()) {
                    return@withContext Result.success(null)
                }

                val json = JSONObject(decryptedJsonStr)
                val dataObj = json.optJSONObject("data") ?: return@withContext Result.success(null)
                val resultArray = dataObj.optJSONArray("result")
                    ?: dataObj.optJSONObject("data")?.optJSONArray("result")
                    ?: dataObj.optJSONArray("resultSongs")
                    ?: return@withContext Result.success(null)

                if (resultArray.length() == 0) {
                    return@withContext Result.success(null)
                }

                val songsList = mutableListOf<NcmSong>()
                var startTimeMs = 0L

                for (i in 0 until resultArray.length()) {
                    val item = resultArray.getJSONObject(i)
                    if (startTimeMs == 0L) {
                        startTimeMs = item.optLong("startTime", 0L)
                    }

                    val entry = item.optJSONObject("song")
                        ?: item.optJSONObject("data")?.optJSONObject("song")
                        ?: item

                    val song = entry.optJSONObject("song") ?: entry
                    val songId = song.optLong("id", song.optLong("songId", 0L))
                    if (songId == 0L) continue

                    val songName = song.optString("name", song.optJSONObject("song")?.optString("name") ?: "未知歌曲")

                    // 专辑及封面
                    val al = song.optJSONObject("album") ?: song.optJSONObject("al")
                    val albumName = al?.optString("name") ?: ""
                    var picUrl = al?.optString("picUrl") ?: al?.optString("blurPicUrl") ?: ""
                    if (picUrl.startsWith("http://")) {
                        picUrl = "https://" + picUrl.removePrefix("http://")
                    }

                    // 歌手
                    val ar = song.optJSONArray("artists") ?: song.optJSONArray("ar")
                    val artistIds = mutableListOf<Long>()
                    val artistName = buildString {
                        if (ar != null) {
                            for (k in 0 until ar.length()) {
                                val obj = ar.getJSONObject(k)
                                val aId = obj.optLong("id", 0L)
                                if (aId > 0L) artistIds.add(aId)
                                val aName = obj.optString("name")
                                if (aName.isNotBlank()) {
                                    if (isNotEmpty()) append(" / ")
                                    append(aName)
                                }
                            }
                        }
                    }

                    songsList.add(
                        NcmSong(
                            id = songId,
                            name = songName,
                            artist = artistName,
                            album = albumName,
                            coverUrl = picUrl,
                            artistIds = artistIds
                        )
                    )
                }

                if (songsList.isEmpty()) {
                    Result.success(null)
                } else {
                    Result.success(NcmAudioMatchResult(songs = songsList, startTimeMs = startTimeMs))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "网易云 EAPI 听歌识别失败", e)
            Result.failure(e)
        }
    }

    /**
     * 听完网络音频后调用网易云活动打卡接口 (+1)
     * @param context Context 用于获取持久化的网易云 Cookie
     * @param songId 网易云歌曲 ID
     * @param artistIds 艺人 ID 列表
     */
    suspend fun reportSongListened(
        context: android.content.Context,
        songId: Long,
        artistIds: List<Long> = emptyList()
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (songId <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("无效的歌曲ID: $songId"))
            }

            val actualArtistIds = if (artistIds.isEmpty()) {
                val detailResult = getSongDetail(listOf(songId)).getOrNull()
                detailResult?.firstOrNull()?.artistIds ?: emptyList()
            } else {
                artistIds
            }

            val artistIdsFormatted = if (actualArtistIds.isNotEmpty()) {
                actualArtistIds.joinToString(",") { "{$it}" }
            } else {
                "{0}"
            }

            val dataJson = JSONObject().apply {
                put("songId", songId.toString())
                put("artistIds", artistIdsFormatted)
                put("header", "{}")
                put("e_r", true)
            }

            // EAPI 加密: url 为 /api/activity/attract/artist/song/listened
            val encryptedParams = NcmCrypto.encryptEApi(
                "/api/activity/attract/artist/song/listened",
                dataJson.toString()
            )

            val formBody = FormBody.Builder()
                .add("params", encryptedParams)
                .build()

            val cookieHeader = NcmAccountManager.buildFullCookie(context)
            val userAgent = "NeteaseMusic/8.10.60.230824122627(8010060);Dalvik/2.1.0 (Linux; U; ${android.os.Build.VERSION.RELEASE}; ${android.os.Build.MODEL} Build/${android.os.Build.ID})"

            val request = Request.Builder()
                .url("https://interface3.music.163.com/eapi/activity/attract/artist/song/listened")
                .post(formBody)
                .addHeader("cm_no_encrypt_native_tag_20220105", "false")
                .addHeader("x-music-loc-site", "300_st.music.163.com/c/poplayer")
                .addHeader("user-agent", userAgent)
                .addHeader("cmpageid", "PlayerActivity")
                .addHeader("x-mam-custommark", "okhttp")
                .addHeader("Cookie", cookieHeader)
                .build()

            // Log.d(TAG, "开始调用网易云打卡接口(+1): songId=$songId, artistIds=$artistIdsFormatted")

            httpClient.newCall(request).execute().use { response ->
                val respBytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("响应体为空"))
                val decryptedJsonStr = NcmCrypto.decryptEApi(respBytes)
                // Log.d(TAG, "网易云打卡(+1)解密响应: $decryptedJsonStr")

                if (decryptedJsonStr.isBlank()) {
                    return@withContext Result.failure(Exception("解密响应为空"))
                }

                val json = JSONObject(decryptedJsonStr)
                val code = json.optInt("code", -1)
                val data = json.optBoolean("data", false)

                if (code == 200 && data) {
                    // Log.d(TAG, "🎉 网易云打卡(+1)成功: songId=$songId")
                    Result.success(true)
                } else {
                    val msg = json.optString("msg", "打卡返回数据不符合成功预期")
                    Log.w(TAG, "⚠️ 网易云打卡未能成功: code=$code, data=$data, msg=$msg")
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "网易云打卡接口调用异常: songId=$songId", e)
            Result.failure(e)
        }
    }
}
