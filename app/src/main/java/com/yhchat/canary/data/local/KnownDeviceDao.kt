package com.yhchat.canary.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 已知设备表的读写。
 */
@Dao
interface KnownDeviceDao {

    /** 某个账号的全部已知设备，最近见过的排前面 */
    @Query("SELECT * FROM known_devices WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    fun observeByUser(userId: String): Flow<List<KnownDevice>>

    @Query("SELECT * FROM known_devices WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    suspend fun getByUser(userId: String): List<KnownDevice>

    @Query("SELECT * FROM known_devices WHERE userId = :userId AND deviceId = :deviceId LIMIT 1")
    suspend fun find(userId: String, deviceId: String): KnownDevice?

    @Upsert
    suspend fun upsert(device: KnownDevice)

    @Upsert
    suspend fun upsertAll(devices: List<KnownDevice>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<KnownDevice>)

    @Query("DELETE FROM known_devices WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
