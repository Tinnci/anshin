package com.example.medlog.data.local

import androidx.room.*
import com.example.medlog.data.model.Medication
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Query("""
        SELECT * FROM medications
        WHERE isArchived = 0
        ORDER BY isHighPriority DESC, reminderHour, reminderMinute
    """)
    fun getActiveMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications ORDER BY isHighPriority DESC, name")
    fun getAllMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE isArchived = 1 ORDER BY name")
    fun getArchivedMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: Long): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication): Long

    @Update
    suspend fun updateMedication(medication: Medication)

    @Delete
    suspend fun deleteMedication(medication: Medication)

    @Query("UPDATE medications SET isArchived = 1 WHERE id = :id")
    suspend fun archiveMedication(id: Long)

    @Query("UPDATE medications SET isArchived = 0 WHERE id = :id")
    suspend fun unarchiveMedication(id: Long)

    @Query("UPDATE medications SET stock = :newStock WHERE id = :id")
    suspend fun updateStock(id: Long, newStock: Double)
}
