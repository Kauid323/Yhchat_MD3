package com.yhchat.canary.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 播放列表实体 */
@Entity(tableName = "audio_playlists")
data class AudioPlaylist(
    @PrimaryKey
    val id: String,                        // UUID
    val name: String,                      // 列表名称（"当前播放" 或用户自定义）
    val createdAt: Long = System.currentTimeMillis(),
    val isAutoQueue: Boolean = false       // true = "当前播放" 自动队列，不可删除
)
