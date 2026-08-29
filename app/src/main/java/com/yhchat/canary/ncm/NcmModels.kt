package com.yhchat.canary.ncm

/**
 * 网易云音乐歌曲模型
 */
data class NcmSong(
    val id: Long,
    val name: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val durationMs: Long = 0L,
    val artistIds: List<Long> = emptyList()
) {
    val displaySubtitle: String
        get() = buildString {
            if (artist.isNotBlank()) append(artist)
            if (album.isNotBlank()) {
                if (isNotEmpty()) append(" - ")
                append("《$album》")
            }
        }
}

/**
 * 网易云搜索结果
 */
data class NcmSearchResult(
    val songs: List<NcmSong> = emptyList(),
    val totalCount: Int = 0
)

/**
 * 听歌识曲响应结果
 */
data class NcmAudioMatchResult(
    val songs: List<NcmSong> = emptyList(),
    val startTimeMs: Long = 0L
)
