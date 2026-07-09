package com.example.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.chat.data.local.entity.StatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: StatusEntity)

    @Query("SELECT * FROM statuses WHERE timestamp > :expiryTimestamp ORDER BY timestamp DESC")
    fun getActiveStatusesFlow(expiryTimestamp: Long): Flow<List<StatusEntity>>

    @Query("DELETE FROM statuses WHERE timestamp <= :expiryTimestamp")
    suspend fun deleteExpiredStatuses(expiryTimestamp: Long)
}
