package com.yhchat.canary.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 播放列表中的单个音频项 */
@Entity(
    tableName = "audio_playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = AudioPlaylist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class AudioPlaylistItem(
    @PrimaryKey
    val id: String,               // UUID
    val playlistId: String,       // 所属播放列表 ID
    val title: String,            // 显示标题
    val url: String,              // 网络 URL 或本地 content:// URI
    val source: String,           // "CHAT" | "LOCAL" | "ONLINE"
    val durationMs: Long = 0L,    // 音频时长（毫秒）
    val addedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0        // 在列表内的排序
)
