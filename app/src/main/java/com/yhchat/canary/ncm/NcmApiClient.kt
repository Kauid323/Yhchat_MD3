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

            val eparams = NcmCrypto.encryptLinuxApi(reqJson.toString())
            val formBody = FormBody.Builder()
                .add("eparams", eparams)
                .build()

            val request = Request.Builder()
                .url(LINUX_FORWARD_URL)
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36")
                .addHeader("Referer", "https://music.163.com")
                .addHeader("Cookie", "os=linux; appver=1.2.1.0428; osver=Deepin 20.9")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext Result.success(emptyList())
                val decrypted = NcmCrypto.decryptLinuxApi(bodyStr)
                val json = JSONObject(decrypted)

                val resultObj = json.optJSONObject("result") ?: return@withContext Result.success(emptyList())
                val songsArray = resultObj.optJSONArray("songs") ?: return@withContext Result.success(emptyList())

                val songsList = mutableListOf<NcmSong>()
                for (i in 0 until songsArray.length()) {
                    val songObj = songsArray.getJSONObject(i)
                    val id = songObj.optLong("id", 0L)
                    if (id == 0L) continue
                    val name = songObj.optString("name", "未知歌曲")

                    // 歌手解析
                    val arArray = songObj.optJSONArray("ar") ?: songObj.optJSONArray("artists")
                    val artists = buildString {
                        if (arArray != null) {
                            for (j in 0 until arArray.length()) {
                                val aName = arArray.getJSONObject(j).optString("name")
                                if (aName.isNotBlank()) {
                                    if (isNotEmpty()) append(" / ")
                                    append(aName)
                                }
                            }
                        }
                    }

                    // 专辑及封面
                    val alObj = songObj.optJSONObject("al") ?: songObj.optJSONObject("album")
                    val albumName = alObj?.optString("name") ?: ""
                    var picUrl = alObj?.optString("picUrl") ?: ""
                    if (picUrl.startsWith("http://")) {
                        picUrl = "https://" + picUrl.removePrefix("http://")
                    }

                    val dt = songObj.optLong("dt", 0L)

                    songsList.add(
                        NcmSong(
                            id = id,
                            name = name,
                            artist = artists,
                            album = albumName,
                            coverUrl = picUrl,
                            durationMs = dt
                        )
                    )
                }

                Result.success(songsList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "网易云搜索失败: keyword=$keyword", e)
            Result.failure(e)
        }
    }

    /**
     * 获取歌曲真实播放直链
     * @param songId 网易云歌曲 ID
     */
    suspend fun getSongPlayUrl(songId: Long): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val paramsJson = JSONObject().apply {
                put("ids", JSONArray().apply { put(songId) })
                put("br", 999000)
            }

            val reqJson = JSONObject().apply {
                put("method", "POST")
                put("url", "https://music.163.com/api/song/enhance/player/url")
                put("params", paramsJson)
            }

            val eparams = NcmCrypto.encryptLinuxApi(reqJson.toString())
            val formBody = FormBody.Builder()
                .add("eparams", eparams)
                .build()

            val request = Request.Builder()
                .url(LINUX_FORWARD_URL)
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36")
                .addHeader("Referer", "https://music.163.com")
                .addHeader("Cookie", "os=linux; appver=1.2.1.0428; osver=Deepin 20.9")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext Result.success(null)
                val decrypted = NcmCrypto.decryptLinuxApi(bodyStr)
                val json = JSONObject(decrypted)
                val dataArray = json.optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    val firstItem = dataArray.getJSONObject(0)
                    var songUrl = firstItem.optString("url")
                    if (songUrl.isNotBlank() && songUrl != "null") {
                        if (songUrl.startsWith("http://")) {
                            songUrl = "https://" + songUrl.removePrefix("http://")
                        }
                        return@withContext Result.success(songUrl)
                    }
                }

                // 官方外链备用降级地址
                val fallbackUrl = "https://music.163.com/song/media/outer/url?id=$songId.mp3"
                Result.success(fallbackUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取歌曲直链失败: songId=$songId", e)
            // 出错时尝试返回官方外链
            Result.success("https://music.163.com/song/media/outer/url?id=$songId.mp3")
        }
    }

    /**
     * 获取单曲详情（含高品质封面等元数据）
     */
    suspend fun getSongDetail(songId: Long): Result<NcmSong?> = withContext(Dispatchers.IO) {
        try {
            val paramsJson = JSONObject().apply {
                put("c", "[{\"id\":$songId}]")
            }

            val reqJson = JSONObject().apply {
                put("method", "POST")
                put("url", "https://music.163.com/api/v3/song/detail")
                put("params", paramsJson)
            }

            val eparams = NcmCrypto.encryptLinuxApi(reqJson.toString())
            val formBody = FormBody.Builder()
                .add("eparams", eparams)
                .build()

            val request = Request.Builder()
                .url(LINUX_FORWARD_URL)
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36")
                .addHeader("Cookie", "os=linux; appver=1.2.1.0428; osver=Deepin 20.9")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext Result.success(null)
                val decrypted = NcmCrypto.decryptLinuxApi(bodyStr)
                val json = JSONObject(decrypted)
                val songsArray = json.optJSONArray("songs")
                if (songsArray != null && songsArray.length() > 0) {
                    val songObj = songsArray.getJSONObject(0)
                    val id = songObj.optLong("id", songId)
                    val name = songObj.optString("name", "未知歌曲")

                    val arArray = songObj.optJSONArray("ar") ?: songObj.optJSONArray("artists")
                    val artists = buildString {
                        if (arArray != null) {
                            for (j in 0 until arArray.length()) {
                                val aName = arArray.getJSONObject(j).optString("name")
                                if (aName.isNotBlank()) {
                                    if (isNotEmpty()) append(" / ")
                                    append(aName)
                                }
                            }
                        }
                    }

                    val alObj = songObj.optJSONObject("al") ?: songObj.optJSONObject("album")
                    val albumName = alObj?.optString("name") ?: ""
                    var picUrl = alObj?.optString("picUrl") ?: ""
                    if (picUrl.startsWith("http://")) {
                        picUrl = "https://" + picUrl.removePrefix("http://")
                    }

                    val dt = songObj.optLong("dt", 0L)

                    return@withContext Result.success(
                        NcmSong(
                            id = id,
                            name = name,
                            artist = artists,
                            album = albumName,
                            coverUrl = picUrl,
                            durationMs = dt
                        )
                    )
                }

                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取歌曲详情失败: songId=$songId", e)
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
            val encodedFP = URLEncoder.encode(audioFP, "UTF-8")
            val url = "$AUDIO_MATCH_URL?sessionId=0123456789abcdef&algorithmCode=shazam_v2&duration=$durationSec&rawdata=$encodedFP&times=1&decrypt=1"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "NeteaseMusic/9.1.65.240927161425(9001065);Dalvik/2.1.0 (Linux; U; Android 14; 23013RK75C Build/UKQ1.230804.001)")
                .addHeader("Referer", "https://interface.music.163.com")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext Result.success(null)
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "网易云听歌识别失败", e)
            Result.failure(e)
        }
    }
}
