package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.IntakeLog
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeLogDao {
    @Query("SELECT * FROM intake_logs ORDER BY takenTimestamp DESC")
    fun getAllLogs(): Flow<List<IntakeLog>>

    @Query("SELECT * FROM intake_logs WHERE dateString = :dateString")
    fun getLogsForDate(dateString: String): Flow<List<IntakeLog>>

    @Query("SELECT * FROM intake_logs WHERE dateString = :dateString")
    suspend fun getLogsForDateSync(dateString: String): List<IntakeLog>

    @Query("SELECT * FROM intake_logs WHERE medicationId = :medicationId AND scheduledTime = :scheduledTime AND dateString = :dateString LIMIT 1")
    suspend fun getSpecificLog(medicationId: Long, scheduledTime: String, dateString: String): IntakeLog?

    @Query("SELECT * FROM intake_logs WHERE medicationId = :medicationId AND scheduledTime = :scheduledTime AND dateString = :dateString LIMIT 1")
    fun getSpecificLogSync(medicationId: Long, scheduledTime: String, dateString: String): IntakeLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: IntakeLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLogSync(log: IntakeLog): Long

    @Query("DELETE FROM intake_logs WHERE medicationId = :medicationId AND scheduledTime = :scheduledTime AND dateString = :dateString")
    suspend fun deleteLog(medicationId: Long, scheduledTime: String, dateString: String)

    @Query("DELETE FROM intake_logs WHERE medicationId = :medicationId")
    suspend fun deleteLogsForMedication(medicationId: Long)
}
