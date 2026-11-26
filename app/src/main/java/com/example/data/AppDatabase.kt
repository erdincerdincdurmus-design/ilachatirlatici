package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AlarmEventLogDao
import com.example.data.dao.IntakeLogDao
import com.example.data.dao.MedicationDao
import com.example.data.model.AlarmEventLog
import com.example.data.model.IntakeLog
import com.example.data.model.Medication

@Database(
    entities = [Medication::class, IntakeLog::class, AlarmEventLog::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun intakeLogDao(): IntakeLogDao
    abstract fun alarmEventLogDao(): AlarmEventLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `alarm_event_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `medicationId` INTEGER NOT NULL,
                        `medicationName` TEXT NOT NULL,
                        `scheduledTime` TEXT NOT NULL,
                        `details` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medication_reminder_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
