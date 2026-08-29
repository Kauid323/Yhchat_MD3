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
    private const val AUDIO_MATCH_URL = "https://interface.music.163.com/api/music/audio/match"

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
                    val artistName = buildString {
                        if (arArray != null) {
                            for (j in 0 until arArray.length()) {
                                val ar = arArray.getJSONObject(j)
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
                            durationMs = duration
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
                    val artistName = buildString {
                        if (arArray != null) {
                            for (j in 0 until arArray.length()) {
                                val ar = arArray.getJSONObject(j)
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
                            durationMs = duration
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
     * 听歌识曲音频指纹识别
     * @param durationSec 音频时长（秒）
     * @param audioFP Base64 音频指纹
     */
    suspend fun matchAudioFingerprint(
        durationSec: Int,
        audioFP: String
    ): Result<NcmAudioMatchResult?> = withContext(Dispatchers.IO) {
        try {
            val safeDuration = durationSec.coerceIn(1, 15)
            var responseBody: String? = null

            // 优先使用 POST FormBody 避免 URL 过长触发网关 414 / RST 连接断开
            try {
                val formBody = FormBody.Builder()
                    .add("sessionId", "0123456789abcdef")
                    .add("algorithmCode", "shazam_v2")
                    .add("duration", safeDuration.toString())
                    .add("rawdata", audioFP)
                    .add("times", "1")
                    .add("decrypt", "1")
                    .build()

                val postRequest = Request.Builder()
                    .url(AUDIO_MATCH_URL)
                    .post(formBody)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; 23013RK75C Build/UKQ1.230804.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.230 Mobile Safari/537.36 NeteaseMusic/9.1.65")
                    .addHeader("Referer", "https://interface.music.163.com")
                    .addHeader("Origin", "https://interface.music.163.com")
                    .addHeader("Accept", "*/*")
                    .build()

                httpClient.newCall(postRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        responseBody = response.body?.string()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "POST 识曲请求异常，尝试 GET 降级: ${e.message}")
            }

            // 若 POST 失败或未返回，尝试 GET
            if (responseBody.isNullOrBlank()) {
                val encodedFP = URLEncoder.encode(audioFP, "UTF-8")
                val url = "$AUDIO_MATCH_URL?sessionId=0123456789abcdef&algorithmCode=shazam_v2&duration=$safeDuration&rawdata=$encodedFP&times=1&decrypt=1"

                val getRequest = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Referer", "https://interface.music.163.com")
                    .addHeader("Accept", "*/*")
                    .build()

                httpClient.newCall(getRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        responseBody = response.body?.string()
                    }
                }
            }

            val bodyStr = responseBody ?: return@withContext Result.success(null)
            val json = JSONObject(bodyStr)

            val dataObj = json.optJSONObject("data") ?: return@withContext Result.success(null)
            val resultArray = dataObj.optJSONArray("result")
                ?: dataObj.optJSONObject("data")?.optJSONArray("result")
                ?: dataObj.optJSONArray("resultSongs")
                ?: return@withContext Result.success(null)

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

                // 专辑
                val al = song.optJSONObject("album") ?: song.optJSONObject("al")
                val albumName = al?.optString("name") ?: ""
                var picUrl = al?.optString("picUrl") ?: ""
                if (picUrl.startsWith("http://")) {
                    picUrl = "https://" + picUrl.removePrefix("http://")
                }

                // 歌手
                val ar = song.optJSONArray("artists") ?: song.optJSONArray("ar")
                val artistName = buildString {
                    if (ar != null) {
                        for (k in 0 until ar.length()) {
                            val aName = ar.getJSONObject(k).optString("name")
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
                        coverUrl = picUrl
                    )
                )
            }

            if (songsList.isEmpty()) {
                Result.success(null)
            } else {
                Result.success(NcmAudioMatchResult(songs = songsList, startTimeMs = startTimeMs))
            }
        } catch (e: Exception) {
            Log.e(TAG, "网易云听歌识别失败", e)
            Result.failure(e)
        }
    }
}
