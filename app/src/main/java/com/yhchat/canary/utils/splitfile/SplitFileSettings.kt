package com.yhchat.canary.utils.splitfile

import android.content.Context

/**
 * 分块上传的可配置项。
 *
 * 单独放一个对象而不是散落在设置页和 ViewModel 里：分块大小既被设置界面读写，
 * 也被上传流程读取，两处各写一份 "1..40" 和默认值，迟早会不一致。
 */
object SplitFileSettings {

    private const val PREFS = "split_upload_settings"
    private const val KEY_CHUNK_MB = "chunk_size_mb"

    /** 最小分块 1MB */
    const val MIN_CHUNK_MB = 1

    /** 最大分块 40MB */
    const val MAX_CHUNK_MB = 40

    /** 默认 32MB —— 接近上限但留有余量，兼顾块数与单次上传超时风险 */
    const val DEFAULT_CHUNK_MB = 32

    /**
     * 超过这个大小才走分块。
     *
     * 5MB 是个折中：更小的文件切块后要多传一轮清单，反而更慢；
     * 更大的话一些本可直接上传的文件也会被切。
     */
    const val SPLIT_THRESHOLD_BYTES = 5L * 1024 * 1024

    /** 当前配置的分块大小（MB） */
    fun chunkSizeMb(context: Context): Int {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CHUNK_MB, DEFAULT_CHUNK_MB)
        return raw.coerceIn(MIN_CHUNK_MB, MAX_CHUNK_MB)
    }

    const val KEY_PROTOCOL = "upload_protocol"
    const val CANARY = "canary"
    const val RICKKOBE = "yunhu-rickkobe"

    fun uploadProtocol(context: Context): String = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROTOCOL, CANARY)
        .takeIf { it == RICKKOBE } ?: CANARY

    fun setUploadProtocol(context: Context, protocol: String) {
        require(protocol == CANARY || protocol == RICKKOBE)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PROTOCOL, protocol).apply()
    }

    /** 当前配置的分块大小（字节） */
    fun chunkSizeBytes(context: Context): Long = chunkSizeMb(context) * 1024L * 1024L

    fun setChunkSizeMb(context: Context, mb: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CHUNK_MB, mb.coerceIn(MIN_CHUNK_MB, MAX_CHUNK_MB))
            .apply()
    }
}
