package com.yhchat.canary.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * ntfy 转发。
 *
 * 为什么要有这个：应用自身的系统通知只在进程活着的时候才发得出来，一旦被系统
 * 杀掉就彻底哑了。ntfy 是外部的推送通道 —— 手机上的 ntfy app 订阅一个 topic，
 * 我们把消息 POST 过去，由 ntfy 自己的推送链路负责送达，这样即使本应用被回收，
 * 用户依然能收到提醒。
 *
 * 「密钥」就是 ntfy 的 topic：谁拿到它谁就能收发，所以随机生成、不写死。
 */
object NtfyNotifier {

    private const val TAG = "NtfyNotifier"

    private const val PREFS_NAME = "notification_settings"
    const val KEY_ENABLED = "ntfy_enabled"
    const val KEY_TOPIC = "ntfy_topic"
    const val KEY_SERVER = "ntfy_server"

    /** 默认公共服务器。自建的把这里换掉即可，设置里也能改 */
    const val DEFAULT_SERVER = "https://ntfy.sh"

    /**
     * ntfy 的 topic 只允许 A-Z a-z 0-9 和 - _，长度上限 64。
     * 生成时只用字母数字，天然合法；换成别的前缀/后缀也不会越界。
     */
    private const val TOPIC_ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /** topic 长度在这个区间里随机取：够长不至于撞车，又不至于手输到怀疑人生 */
    private const val TOPIC_MIN_LENGTH = 8
    private const val TOPIC_MAX_LENGTH = 16

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // 通知是"发了就不管"，超时没必要拖太长，避免后台堆请求
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getTopic(context: Context): String =
        prefs(context).getString(KEY_TOPIC, "").orEmpty().trim()

    fun saveTopic(context: Context, topic: String) {
        prefs(context).edit().putString(KEY_TOPIC, topic.trim()).apply()
    }

    /**
     * 服务器地址，已做规整：补 scheme、去掉结尾斜杠。
     *
     * 用户填 "ntfy.sh" 或 "https://ntfy.sh/" 都当成同一个地方；
     * 填了个解析不了的地址就当没配置，退回默认值 —— 否则拼出来的 URL
     * 会在 OkHttp 那里直接抛 IllegalArgumentException。
     */
    fun getServer(context: Context): String {
        val raw = prefs(context).getString(KEY_SERVER, DEFAULT_SERVER)
            ?.trim()
            .orEmpty()
            .ifBlank { DEFAULT_SERVER }
        return normalizeServer(raw)
    }

    fun saveServer(context: Context, server: String) {
        prefs(context).edit().putString(KEY_SERVER, server.trim()).apply()
    }

    /** 把用户输入的地址补成可用形式；实在解析不出来就返回默认服务器 */
    fun normalizeServer(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) return DEFAULT_SERVER
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            value = "https://$value"
        }
        value = value.trimEnd('/')
        // toHttpUrlOrNull 解析失败会返回 null，不会抛 —— 用户填个 "abc def"
        // 也能安全地退回默认服务器，而不是在发通知时崩掉
        val parsed = value.toHttpUrlOrNull() ?: return DEFAULT_SERVER
        return parsed.toString().trimEnd('/')
    }

    /**
     * 生成一个随机 topic，长度在 8~16 之间随机。
     *
     * 用 SecureRandom 而不是 Random：这个串就是访问凭据，
     * 可预测的随机数等于把口令送人。
     */
    fun generateTopicKey(): String {
        val random = SecureRandom()
        val length = TOPIC_MIN_LENGTH +
            random.nextInt(TOPIC_MAX_LENGTH - TOPIC_MIN_LENGTH + 1)
        val builder = StringBuilder(length)
        repeat(length) {
            builder.append(TOPIC_ALPHABET[random.nextInt(TOPIC_ALPHABET.length)])
        }
        return builder.toString()
    }

    /**
     * 把一条消息转发到 ntfy。
     *
     * 不阻塞调用方：网络请求在 IO 线程上跑，失败只记日志 ——
     * 推送通道是"有则更好"，它挂了不该影响本地通知和消息入库。
     */
    fun notify(context: Context, title: String, message: String, force: Boolean = false) {
        // force 给「发送测试通知」用：用户可能还没打开开关就想先验证配置，
        // 这时按开关判断会静默什么也不做，点一下像没反应
        if (!force && !isEnabled(context)) return

        val topic = getTopic(context)
        if (topic.isBlank()) {
            Log.w(TAG, "ntfy 已开启但没有配置密钥，跳过")
            return
        }

        val server = getServer(context)

        scope.launch {
            try {
                // 用 JSON 发布（POST 到服务器根路径）而不是 "POST /topic + Title 头"。
                // 后者要求 header 是 ISO-8859-1，中文标题会被截成一堆问号；
                // JSON 走 UTF-8，标题、正文都能原样送达。
                val payload = JsonObject().apply {
                    addProperty("topic", topic)
                    addProperty("title", title.ifBlank { "新消息" })
                    addProperty("message", message)
                    addProperty("priority", 3)
                    add("tags", JsonArray().apply { add("speech_balloon") })
                }

                val body = gson.toJson(payload)
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("$server/")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "ntfy 发布失败: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ntfy 发布异常: ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }

    /** 发一条测试消息，设置页点「发送测试通知」时用。不受开关影响 */
    fun sendTest(context: Context) {
        notify(
            context = context,
            title = "Yhchat Canary",
            message = "这是一条 ntfy 测试通知，收到就说明配置正确。",
            force = true
        )
    }
}
