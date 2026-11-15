package com.yhchat.canary.data.model

import com.google.gson.annotations.SerializedName

/**
 * 非 VIP 分块上传的文件清单（`.splitfiles`）。
 *
 * ## 要解决的问题
 *
 * 普通账号单文件有大小上限，大文件发不出去。这里把文件切成若干块分别上传到七牛云，
 * 拿到每块的 key/hash，再把它们汇总成一份 JSON 清单本身作为一个**很小的文件**发出去。
 * 接收端看到 `.splitfiles`，解析清单、逐块下载、按顺序拼接还原原文件。
 *
 * ## 为什么是文件而不是文本消息
 *
 * 之前那套 `type=split_files` 是文本消息，依赖服务端/机器人配合。这一版完全在客户端之间
 * 完成：清单本身就是一个普通文件，不依赖任何服务端能力，也不挑会话类型。
 */
data class SplitFileManifest(
    /** 协议标识，接收端据此判断能不能处理 */
    @SerializedName("protocol")
    val protocol: String = PROTOCOL_NAME,

    /** 协议版本，便于以后演进 */
    @SerializedName("version")
    val version: Int = 1,

    /** 合并后的原始文件名（含扩展名） */
    @SerializedName("name")
    val fileName: String,

    /** 合并后的总字节数 */
    @SerializedName("size")
    val totalSize: Long,

    /** 每块的目标大小（最后一块可能更小） */
    @SerializedName("chunkSize")
    val chunkSize: Long,

    /** 块数 */
    @SerializedName("count")
    val count: Int,

    /** 原文件 MIME，用于下载后打开 */
    @SerializedName("mime")
    val mime: String? = null,

    /** 按块顺序排列的分块列表 */
    @SerializedName("parts")
    val parts: List<SplitPartEntry> = emptyList(),

    /** 清单生成时间（毫秒），仅用于展示 */
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 协议标识 */
        const val PROTOCOL_NAME = "yhchat-splitfiles"

        /** 清单元数据文件的后缀（带点） */
        const val FILE_EXTENSION = ".splitfiles"

        /**
         * 默认分块大小 32MB。
         *
         * 用户要求 30–40MB，取中间值：太小会让块数变多、清单变长、上传轮次增加；
         * 太大则单次上传更容易超时（七牛上传用的是 120s 超时）。
         */
        const val DEFAULT_CHUNK_SIZE = 32L * 1024 * 1024
    }
}

/**
 * 一个分块。
 *
 * `key` / `hash` 直接来自七牛上传响应 —— 七牛的 `hash` 是 ETag，
 * 可用于下载后逐块校验（合并前就能定位坏块，不用整包重下）。
 */
data class SplitPartEntry(
    /** 块序号，**从 0 开始**，拼接时必须严格按它排序 */
    @SerializedName("index")
    val index: Int,

    /** 七牛资源 key，下载地址 = 图床前缀 + key */
    @SerializedName("key")
    val key: String,

    /** 七牛返回的 hash（ETag），用于完整性校验 */
    @SerializedName("hash")
    val hash: String,

    /** 该块字节数 */
    @SerializedName("size")
    val size: Long
)

/**
 * 分块上传进度，供 UI 展示。
 */
data class SplitUploadProgress(
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    /** 当前块的上传进度 0f..1f */
    val chunkFraction: Float = 0f,
    val stage: Stage = Stage.Preparing,
    val errorMessage: String? = null
) {
    enum class Stage {
        /** 正在读取、切块 */
        Preparing,
        /** 正在上传第 N 块 */
        Uploading,
        /** 正在生成清单文件 */
        WritingManifest,
        /** 完成 */
        Done
    }

    /** 总进度 */
    val fraction: Float
        get() {
            if (totalChunks <= 0) return 0f
            return ((currentChunk + chunkFraction) / totalChunks).coerceIn(0f, 1f)
        }
}
