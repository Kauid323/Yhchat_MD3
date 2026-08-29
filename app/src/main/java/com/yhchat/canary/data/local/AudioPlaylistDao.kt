package com.yhchat.canary.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioPlaylistDao {

    // ── 播放列表 ──────────────────────────────────────────────

    @Query("SELECT * FROM audio_playlists ORDER BY isAutoQueue DESC, createdAt ASC")
    fun getAllPlaylists(): Flow<List<AudioPlaylist>>

    @Query("SELECT * FROM audio_playlists WHERE isAutoQueue = 1 LIMIT 1")
    suspend fun getAutoQueue(): AudioPlaylist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: AudioPlaylist)

    @Query("UPDATE audio_playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: String, name: String)

    @Query("DELETE FROM audio_playlists WHERE id = :id AND isAutoQueue = 0")
    suspend fun deletePlaylist(id: String)

    // ── 播放列表项 ────────────────────────────────────────────

    @Query("SELECT * FROM audio_playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder ASC, addedAt ASC")
    fun getItemsForPlaylist(playlistId: String): Flow<List<AudioPlaylistItem>>

    @Query("SELECT * FROM audio_playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getItemsForPlaylistSync(playlistId: String): List<AudioPlaylistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: AudioPlaylistItem)

    @Update
    suspend fun updateItem(item: AudioPlaylistItem)

    @Query("DELETE FROM audio_playlist_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("DELETE FROM audio_playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: String)

    @Query("SELECT * FROM audio_playlist_items WHERE playlistId = :playlistId AND url = :url LIMIT 1")
    suspend fun findItemByUrl(playlistId: String, url: String): AudioPlaylistItem?

    @Query("SELECT MAX(sortOrder) FROM audio_playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxSortOrder(playlistId: String): Int?

    @Query("""
        SELECT * FROM audio_playlist_items 
        WHERE playlistId = :playlistId 
          AND (title LIKE '%' || :query || '%')
        ORDER BY sortOrder ASC, addedAt ASC
    """)
    suspend fun searchItems(playlistId: String, query: String): List<AudioPlaylistItem>
}
