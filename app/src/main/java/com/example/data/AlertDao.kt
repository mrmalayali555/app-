package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT 100")
    fun getRecentAlerts(): Flow<List<AlertEntity>>

    @Insert
    suspend fun insertAlert(alert: AlertEntity)

    @Query("DELETE FROM alerts")
    suspend fun clearAlerts()
}
