package com.yhchat.canary.ui.components

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 音频文件仓库
 */
class AudioRepository(private val context: Context) {
    
    /**
     * 加载保存的音频文件列表
     */
    suspend fun loadSavedAudios(): List<SavedAudioItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<SavedAudioItem>()
        
        try {
            // 从MediaStore查询音频文件
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATA
            )
            
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 0" // 排除音乐文件
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    val path = cursor.getString(dataColumn) ?: ""
                    
                    val uri = ContentUris.withAppendedId(collection, id)
                    
                    // 只添加音频文件
                    if (path.contains("yhchat", ignoreCase = true) || 
                        path.contains("voice", ignoreCase = true)) {
                        audioList.add(
                            SavedAudioItem(
                                uri = uri,
                                displayName = name,
                                duration = duration,
                                size = size,
                                dateAdded = dateAdded,
                                path = path
                            )
                        )
                    }
                }
            }
            
            Log.d("AudioRepository", "加载了 ${audioList.size} 个音频文件")
        } catch (e: Exception) {
            Log.e("AudioRepository", "加载音频文件失败", e)
        }
        
        audioList
    }
    
    /**
     * 获取音频文件URI
     */
    fun getAudioUri(audioId: Long): Uri {
        return ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            audioId
        )
    }
    
    /**
     * 删除音频文件
     */
    suspend fun deleteAudio(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = context.contentResolver.delete(uri, null, null)
            deleted > 0
        } catch (e: Exception) {
            Log.e("AudioRepository", "删除音频文件失败", e)
            false
        }
    }
    
    /**
     * 创建音频文件保存路径
     */
    fun createAudioFile(fileName: String): File {
        val audioDir = File(context.getExternalFilesDir(null), "Audio")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
        return File(audioDir, fileName)
    }
}
