package com.example.medlog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog

@Database(
    entities = [Medication::class, MedicationLog::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MedLogDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao

    companion object {
        @Volatile private var INSTANCE: MedLogDatabase? = null

        /**
         * Widget 和其他非-DI 场景下的单例访问器。
         * Hilt 应用内仍由 [AppModule] 提供注入版本。
         */
        fun getInstance(context: Context): MedLogDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(context.applicationContext, MedLogDatabase::class.java, "medlog.db")
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
