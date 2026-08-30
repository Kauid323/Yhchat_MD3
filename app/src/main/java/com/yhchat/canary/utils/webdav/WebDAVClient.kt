package com.yhchat.canary.utils

import android.util.Log
import com.yhchat.canary.data.model.MountSetting
import com.yhchat.canary.data.model.WebDAVFile
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * WebDAV 客户端工具类
 */
object WebDAVClient {
    
    private const val TAG = "WebDAVClient"
    
    /**
     * 列出指定路径的文件和文件夹
     */
    suspend fun listFiles(
        mountSetting: MountSetting,
        path: String = ""
    ): Result<List<WebDAVFile>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            val client = OkHttpClient()
            
            // 构建完整的 URL
            // webdavUrl + webdavRootPath 就是完整的默认路径
            val baseUrl = mountSetting.webdavUrl.trimEnd('/')
            val rootPath = mountSetting.webdavRootPath.trimStart('/')
            
            // 直接拼接 webdavUrl + webdavRootPath
            val defaultPath = "$baseUrl/$rootPath".replace("//", "/").replace("http:/", "http://").replace("https:/", "https://")
            
            // 如果有额外的子路径，则拼接
            val url = if (path.isNotEmpty()) {
                "$defaultPath/${path.trimStart('/').trimEnd('/')}"
            } else {
                defaultPath
            }.replace("//", "/").replace("http:/", "http://").replace("https:/", "https://")
            
            Log.d(TAG, "WebDAV PROPFIND: $url")
            
            // 创建 PROPFIND 请求
            val requestBody = """<?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:">
                    <d:prop>
                        <d:displayname/>
                        <d:resourcetype/>
                        <d:getcontentlength/>
                        <d:getlastmodified/>
                    </d:prop>
                </d:propfind>""".trimIndent()
            
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", requestBody.toRequestBody("application/xml".toMediaType()))
                .addHeader("Depth", "1")
                .addHeader("Authorization", Credentials.basic(
                    mountSetting.webdavUserName,
                    mountSetting.webdavPassword
                ))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("WebDAV 请求失败: ${response.code} ${response.message}"))
            }
            
            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("响应体为空"))
            
            // 解析 XML 响应
            // currentPath 是完整的路径，用于在解析响应时过滤当前目录
            val currentPath = if (path.isNotEmpty()) {
                "/$rootPath/$path".replace("//", "/").trimEnd('/')
            } else {
                "/$rootPath".replace("//", "/").trimEnd('/')
            }
            val files = parsePropfindResponse(responseBody, mountSetting, currentPath)
            
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "列出文件失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 解析 PROPFIND 响应
     */
    private fun parsePropfindResponse(
        xml: String,
        mountSetting: MountSetting,
        currentPath: String
    ): List<WebDAVFile> {
        val files = mutableListOf<WebDAVFile>()
        
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            
            // 使用 XPath 或直接遍历节点
            val responses = document.documentElement.getElementsByTagNameNS("*", "response")
            
            for (i in 0 until responses.length) {
                val response = responses.item(i) as? Element ?: continue
                
                // 获取 href（文件路径）- 尝试不同的命名空间
                var href: String? = null
                val hrefNodes = response.getElementsByTagNameNS("*", "href")
                if (hrefNodes.length > 0) {
                    href = hrefNodes.item(0)?.textContent
                }
                if (href == null) {
                    val hrefElements = response.getElementsByTagName("href")
                    if (hrefElements.length > 0) {
                        href = hrefElements.item(0)?.textContent
                    }
                }
                if (href.isNullOrEmpty()) continue
                
                // URL 解码
                val decodedHref = java.net.URLDecoder.decode(href, "UTF-8")
                
                // 跳过当前目录本身
                val normalizedHref = decodedHref.trimEnd('/')
                val normalizedCurrentPath = currentPath.trimEnd('/')
                if (normalizedHref == normalizedCurrentPath || normalizedHref.endsWith(normalizedCurrentPath)) {
                    continue
                }
                
                // 提取文件名
                val name = normalizedHref.substringAfterLast('/')
                if (name.isEmpty()) continue
                
                // 获取资源类型（判断是文件还是文件夹）
                var isDirectory = false
                val resourceTypeNodes = response.getElementsByTagNameNS("*", "resourcetype")
                if (resourceTypeNodes.length > 0) {
                    val resourceType = resourceTypeNodes.item(0) as? Element
                    if (resourceType != null) {
                        val collectionNodes = resourceType.getElementsByTagNameNS("*", "collection")
                        isDirectory = collectionNodes.length > 0
                    }
                }
                
                // 获取文件大小
                var size = 0L
                val contentLengthNodes = response.getElementsByTagNameNS("*", "getcontentlength")
                if (contentLengthNodes.length > 0) {
                    size = contentLengthNodes.item(0)?.textContent?.toLongOrNull() ?: 0L
                }
                
                // 获取最后修改时间
                var lastModified = 0L
                val lastModifiedNodes = response.getElementsByTagNameNS("*", "getlastmodified")
                if (lastModifiedNodes.length > 0) {
                    try {
                        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
                        lastModified = dateFormat.parse(lastModifiedNodes.item(0)?.textContent)?.time ?: 0L
                    } catch (e: Exception) {
                        // 忽略解析错误
                    }
                }
                
                files.add(WebDAVFile(
                    name = name,
                    path = normalizedHref,
                    isDirectory = isDirectory,
                    size = size,
                    lastModified = lastModified,
                    mountSetting = mountSetting
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 PROPFIND 响应失败", e)
        }
        
        return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
}

