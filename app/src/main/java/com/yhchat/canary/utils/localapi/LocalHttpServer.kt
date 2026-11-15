package com.yhchat.canary.utils.localapi

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 极简 HTTP/1.1 服务器。
 *
 * 为什么自己写而不是引入 NanoHTTPD / Ktor：
 *   - 只需要「收一个请求 → 回一段 JSON」，不需要静态文件、WebSocket、路由树；
 *   - 引入框架要加依赖，而这个项目已经有 6.6MB 的体积压力；
 *   - 自己写能精确控制「绝不阻塞主线程」这一点。
 *
 * 支持的够用就好：
 *   - GET / POST
 *   - URL query 参数
 *   - `application/json` 请求体（带 Content-Length 才读）
 *   - 每个连接一个请求，处理完即关（Connection: close）
 *
 * 明确不支持（也不该在本地 agent 场景里用到）：chunked 编码、keep-alive、multipart。
 */
class LocalHttpServer(
    private val host: String,
    private val port: Int,
    private val handler: suspend (HttpRequest) -> HttpResponse
) {

    companion object {
        private const val TAG = "LocalHttpServer"
        /** 单个请求体上限，防止有人往本地端口灌 100MB */
        private const val MAX_BODY_BYTES = 1 shl 20 // 1MB
        private const val SOCKET_TIMEOUT_MS = 15_000
    }

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    val isRunning: Boolean get() = running.get() && serverSocket != null

    /**
     * 启动。已在运行则直接返回。
     *
     * @return 启动失败时返回异常原因（端口被占用是最常见的情况）
     */
    fun start(): Result<Unit> {
        if (isRunning) return Result.success(Unit)

        return runCatching {
            val socket = ServerSocket()
            // 必须设置 reuseAddress，否则杀掉进程后立刻重启会报 "Address already in use"
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName(host), port), 50)
            serverSocket = socket
            running.set(true)

            acceptJob = scope.launch {
                Log.i(TAG, "本地 API 服务已启动: http://$host:$port")
                while (running.get()) {
                    val client = try {
                        socket.accept()
                    } catch (e: Exception) {
                        // stop() 关闭 socket 会让 accept 抛异常，这是正常退出路径
                        if (running.get()) {
                            Log.w(TAG, "accept 异常: ${e.message}")
                        }
                        break
                    }
                    // 每个连接单独起协程，避免一个慢请求卡住其它请求
                    launch { handleConnection(client) }
                }
            }
        }.onFailure {
            Log.e(TAG, "启动失败: ${it.message}", it)
            running.set(false)
            runCatching { serverSocket?.close() }
            serverSocket = null
        }
    }

    fun stop() {
        running.set(false)
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        Log.i(TAG, "本地 API 服务已停止")
    }

    private suspend fun handleConnection(client: Socket) {
        try {
            client.soTimeout = SOCKET_TIMEOUT_MS
            val request = withContext(Dispatchers.IO) { readRequest(client.getInputStream()) }
            val response = if (request == null) {
                HttpResponse.badRequest("malformed request")
            } else {
                runCatching { handler(request) }.getOrElse { e ->
                    Log.e(TAG, "处理请求失败: ${request.method} ${request.path}", e)
                    HttpResponse.error(500, e.message ?: "internal error")
                }
            }
            withContext(Dispatchers.IO) { writeResponse(client, response) }
        } catch (e: Exception) {
            Log.w(TAG, "连接处理异常: ${e.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    private fun readRequest(input: InputStream): HttpRequest? {
        // 逐字节读到 \r\n\r\n，比按行读更可靠（不依赖 BufferedReader 的编码）
        val headerBytes = ArrayList<Byte>(1024)
        var matched = 0
        val terminator = byteArrayOf(13, 10, 13, 10)
        while (matched < terminator.size) {
            val b = input.read()
            if (b == -1) return null
            headerBytes.add(b.toByte())
            if (b.toByte() == terminator[matched]) {
                matched++
            } else {
                matched = if (b.toByte() == terminator[0]) 1 else 0
            }
            // 头不能无限长
            if (headerBytes.size > 32 * 1024) return null
        }

        val headerText = String(headerBytes.toByteArray(), Charsets.ISO_8859_1)
        val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val requestLine = lines.first().split(" ")
        if (requestLine.size < 2) return null
        val method = requestLine[0].uppercase()
        val rawTarget = requestLine[1]

        val headers = mutableMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val index = line.indexOf(':')
            if (index > 0) {
                headers[line.substring(0, index).trim().lowercase()] =
                    line.substring(index + 1).trim()
            }
        }

        // query 与 path 分离
        val pathPart = rawTarget.substringBefore('?')
        val queryPart = rawTarget.substringAfter('?', "")
        val query = parseQuery(queryPart)

        // 只有带 Content-Length 才读 body；chunked 不支持
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            if (contentLength > MAX_BODY_BYTES) {
                return null
            }
            val buffer = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(buffer, read, contentLength - read)
                if (n <= 0) break
                read += n
            }
            String(buffer, 0, read, Charsets.UTF_8)
        } else {
            ""
        }

        return HttpRequest(
            method = method,
            path = pathPart.trimEnd('/').ifEmpty { "/" },
            query = query,
            headers = headers,
            body = body
        )
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            val decodedKey = runCatching { URLDecoder.decode(key, "UTF-8") }.getOrDefault(key)
            val decodedValue = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
            decodedKey to decodedValue
        }.toMap()
    }

    private fun writeResponse(client: Socket, response: HttpResponse) {
        val bodyBytes = response.body.toByteArray(Charsets.UTF_8)
        val reason = when (response.status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            500 -> "Internal Server Error"
            else -> "OK"
        }
        val header = buildString {
            append("HTTP/1.1 ${response.status} $reason\r\n")
            append("Content-Type: ${response.contentType}\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            // 允许浏览器里的调试页面直接跨域调用
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        BufferedOutputStream(client.getOutputStream()).use { out ->
            out.write(header.toByteArray(Charsets.ISO_8859_1))
            out.write(bodyBytes)
            out.flush()
        }
    }
}

/** 解析后的请求 */
data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: String
) {
    /** 取 query 参数，空串也当没有 */
    fun param(name: String): String? = query[name]?.takeIf { it.isNotBlank() }
}

/** 构造的响应 */
data class HttpResponse(
    val status: Int,
    val contentType: String,
    val body: String
) {
    companion object {
        fun json(body: String): HttpResponse =
            HttpResponse(200, "application/json; charset=utf-8", body)

        fun error(status: Int, message: String): HttpResponse =
            HttpResponse(
                status,
                "application/json; charset=utf-8",
                """{"ok":false,"error":${message.toJsonString()}}"""
            )

        fun badRequest(message: String): HttpResponse = error(400, message)

        fun notFound(message: String = "endpoint not found"): HttpResponse = error(404, message)
    }
}

/** 极简 JSON 字符串转义，避免手拼 JSON 时把引号/换行写坏 */
internal fun String.toJsonString(): String {
    val sb = StringBuilder(this.length + 16)
    sb.append('"')
    for (ch in this) {
        when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            else -> {
                if (ch < ' ') {
                    sb.append("\\u").append(String.format("%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
    }
    sb.append('"')
    return sb.toString()
}
