package com.yhchat.canary.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 播放模式 */
enum class AudioPlayMode(val title: String) {
    SEQUENCE("顺序播放"),
    LIST_LOOP("列表循环"),
    SINGLE_LOOP("单曲循环"),
    SHUFFLE("随机播放");

    fun next(): AudioPlayMode = when (this) {
        SEQUENCE -> LIST_LOOP
        LIST_LOOP -> SINGLE_LOOP
        SINGLE_LOOP -> SHUFFLE
        SHUFFLE -> SEQUENCE
    }
}

/** 播放列表实体 */
@Entity(tableName = "audio_playlists")
data class AudioPlaylist(
    @PrimaryKey
    val id: String,                        // UUID
    val name: String,                      // 列表名称（"当前播放" 或用户自定义）
    val createdAt: Long = System.currentTimeMillis(),
    val isAutoQueue: Boolean = false       // true = "当前播放" 自动队列，不可删除
)

