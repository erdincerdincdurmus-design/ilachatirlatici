package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AlarmEventLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmEventLogDao {
    @Query("SELECT * FROM alarm_event_logs ORDER BY timestamp DESC, id DESC LIMIT 200")
    fun getAllAlarmLogs(): Flow<List<AlarmEventLog>>

    @Query("SELECT * FROM alarm_event_logs ORDER BY timestamp DESC, id DESC LIMIT 200")
    fun getAllAlarmLogsSync(): List<AlarmEventLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AlarmEventLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(log: AlarmEventLog): Long

    @Query("DELETE FROM alarm_event_logs WHERE id NOT IN (SELECT id FROM alarm_event_logs ORDER BY timestamp DESC, id DESC LIMIT :keepCount)")
    fun trimOldLogsSync(keepCount: Int = 200)

    @Query("DELETE FROM alarm_event_logs")
    suspend fun clearAll()
}
