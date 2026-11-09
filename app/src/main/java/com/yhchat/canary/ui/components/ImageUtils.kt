package com.yhchat.canary.ui.components

import android.content.Context
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.request.ImageRequest

/**
 * 图片加载工具类
 * 支持多种图片格式包括 GIF、AVIF、WEBP、SVG 等
 */
object ImageUtils {
    private fun isDataSaverEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("data_saver", false)
    }

    /**
     * 这个 URL 是不是本站图床的、需要带 Referer 才能取到图。
     *
     * 按域名后缀判断，不用 contains("chat-img.jwznb.com")：
     * 图床有多套域名并存（chat-img、chat-img3、以后可能还有别的），
     * 写死某一个的话，其余几套就漏了 —— 曾经就是这样，走 chat-img3 的
     * 一部分用户头像一直加载不出来，因为 chat-img3.jwznb.com 里
     * 并不包含 chat-img.jwznb.com 这个子串。
     */
    internal fun needsReferer(url: String): Boolean {
        if (url.isBlank()) return false
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return false
        return host.endsWith("jwznb.com")
    }
    
    /**
     * 创建支持多格式的ImageLoader
     */
    fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // GIF支持
                add(GifDecoder.Factory())
                // Android 9+ 支持 AVIF, WEBP 等新格式
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                }
                // SVG支持
                add(SvgDecoder.Factory())
            }
            .build()
    }
    
    /**
     * 创建表情图片请求
     * 包含必要的referer头和格式支持
     */
    fun createStickerImageRequest(
        context: Context,
        url: String,
        enableHardware: Boolean = true
    ): ImageRequest {
        if (isDataSaverEnabled(context)) {
            return ImageRequest.Builder(context)
                .data(null)
                .build()
        }
        val builder = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(enableHardware)
            .crossfade(true)
            
        // 为相关域名添加Referer头
        if (needsReferer(url)) {
            builder.setHeader("Referer", "https://myapp.jwznb.com")
            builder.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
        }
        
        return builder.build()
    }
    
    /**
     * 创建普通图片请求
     * 支持多种图片格式：jpg, jpeg, png, gif, webp, avif, svg
     */
    fun createImageRequest(
        context: Context,
        url: String,
        enableHardware: Boolean = true
    ): ImageRequest {
        if (isDataSaverEnabled(context)) {
            return ImageRequest.Builder(context)
                .data(null)
                .build()
        }
        val builder = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(enableHardware)
            .crossfade(true)
            
        // 为相关域名添加Referer头
        if (needsReferer(url)) {
            builder.setHeader("Referer", "https://myapp.jwznb.com")
            builder.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
        }
        
        return builder.build()
    }
    
    /**
     * 创建头像图片请求
     * 支持多种图片格式，包含referer头
     */
    fun createAvatarImageRequest(
        context: Context,
        url: String,
        enableHardware: Boolean = true
    ): ImageRequest {
        if (isDataSaverEnabled(context)) {
            return ImageRequest.Builder(context)
                .data(null)
                .build()
        }
        val builder = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(enableHardware)
            .crossfade(true)
            
        // 为相关域名添加Referer头
        if (needsReferer(url)) {
            builder.setHeader("Referer", "https://myapp.jwznb.com")
            builder.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
        }
        
        return builder.build()
    }
    
    /**
     * 创建机器人图片请求
     * 专门为机器人相关图片添加必要的referer头
     */
    fun createBotImageRequest(
        context: Context,
        url: String,
        enableHardware: Boolean = true
    ): ImageRequest {
        if (isDataSaverEnabled(context)) {
            return ImageRequest.Builder(context)
                .data(null)
                .build()
        }
        return ImageRequest.Builder(context)
            .data(url)
            .setHeader("Referer", "https://myapp.jwznb.com")
            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            .allowHardware(enableHardware)
            .crossfade(true)
            .build()
    }
    
    /**
     * 创建分区相关图片请求
     * 支持.webp, .avif等格式，包含必要头信息
     */
    fun createBoardImageRequest(
        context: Context,
        url: String,
        enableHardware: Boolean = true
    ): ImageRequest {
        if (isDataSaverEnabled(context)) {
            return ImageRequest.Builder(context)
                .data(null)
                .build()
        }
        return ImageRequest.Builder(context)
            .data(url)
            .setHeader("Referer", "https://myapp.jwznb.com")
            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            .allowHardware(enableHardware)
            .crossfade(true)
            .build()
    }
}
