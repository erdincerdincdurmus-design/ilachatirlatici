package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity recording when an elderly user confirmed taking a medication.
 * Prevents accidental double-taking and provides adherence tracking.
 */
@Entity(tableName = "intake_logs")
data class IntakeLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val medicationName: String,
    val scheduledTime: String, // e.g. "08:00"
    val dateString: String, // Format "yyyy-MM-dd", e.g. "2026-10-27"
    val takenTimestamp: Long = System.currentTimeMillis(),
    val isTaken: Boolean = true
)
