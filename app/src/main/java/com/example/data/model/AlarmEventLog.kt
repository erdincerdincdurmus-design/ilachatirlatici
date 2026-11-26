package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent audit log for every alarm lifecycle event:
 * triggering, next-alarm scheduling, notification display, skips, cancellations, and errors.
 */
@Entity(tableName = "alarm_event_logs")
data class AlarmEventLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val medicationId: Long = -1L,
    val medicationName: String = "",
    val scheduledTime: String = "",
    val details: String
)
