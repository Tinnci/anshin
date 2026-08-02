package com.driezy.medlog.data.local

import androidx.room.*
import com.driezy.medlog.data.model.Medication
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Query(
        """
        SELECT * FROM medications
        WHERE isArchived = 0
        ORDER BY isHighPriority DESC, reminderHour, reminderMinute
    """,
    )
    fun getActiveMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications ORDER BY isHighPriority DESC, name")
    fun getAllMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE isArchived = 1 ORDER BY name")
    fun getArchivedMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: Long): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication): Long

    @Query("SELECT LOWER(TRIM(name)) FROM medications WHERE isArchived = 0")
    suspend fun getNormalizedActiveNames(): List<String>

    @Query("DELETE FROM medications WHERE isArchived = 0")
    suspend fun deleteActiveMedications()

    @Transaction
    suspend fun mergeMedicationsByName(medications: List<Medication>) {
        val names = getNormalizedActiveNames().toMutableSet()
        medications.forEach { medication ->
            val normalizedName = medication.name.trim().lowercase()
            if (names.add(normalizedName)) insertMedication(medication.copy(id = 0))
        }
    }

    @Transaction
    suspend fun replaceActiveMedications(medications: List<Medication>) {
        deleteActiveMedications()
        medications.forEach { insertMedication(it.copy(id = 0)) }
    }

    @Update
    suspend fun updateMedication(medication: Medication)

    /** Rebuilds a group of derived medication plans as one Room transaction. */
    @Transaction
    suspend fun updateMedications(medications: List<Medication>) {
        medications.forEach { updateMedication(it) }
    }

    @Delete
    suspend fun deleteMedication(medication: Medication)

    @Query("UPDATE medications SET isArchived = 1 WHERE id = :id")
    suspend fun archiveMedication(id: Long)

    @Query("UPDATE medications SET isArchived = 0 WHERE id = :id")
    suspend fun unarchiveMedication(id: Long)

    @Query("UPDATE medications SET stock = :newStock WHERE id = :id")
    suspend fun updateStock(id: Long, newStock: Double)

    /** Widget 专用：一次性查询全部药品（不返回 Flow） */
    @Query("SELECT * FROM medications WHERE isArchived = 0 ORDER BY isHighPriority DESC, name")
    suspend fun getAllMedicationsOnce(): List<Medication>
}
