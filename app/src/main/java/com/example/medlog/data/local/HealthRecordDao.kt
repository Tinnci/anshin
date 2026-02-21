package com.example.medlog.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.medlog.data.model.HealthRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: HealthRecord): Long

    @Update
    suspend fun update(record: HealthRecord)

    @Delete
    suspend fun delete(record: HealthRecord)

    /** 所有记录，按时间倒序 */
    @Query("SELECT * FROM health_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<HealthRecord>>

    /** 指定类型的记录，按时间倒序 */
    @Query("SELECT * FROM health_records WHERE type = :type ORDER BY timestamp DESC")
    fun getRecordsByType(type: String): Flow<List<HealthRecord>>

    /** 指定时间范围内的记录（用于生成趋势图），按时间正序 */
    @Query("SELECT * FROM health_records WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp ASC")
    fun getRecordsInRange(from: Long, to: Long): Flow<List<HealthRecord>>

    /** 指定类型在指定时间范围内的记录，按时间正序 */
    @Query("SELECT * FROM health_records WHERE type = :type AND timestamp >= :from AND timestamp <= :to ORDER BY timestamp ASC")
    fun getRecordsByTypeInRange(type: String, from: Long, to: Long): Flow<List<HealthRecord>>

    /** 每种类型的最新一条记录（用于主页快速展示） */
    @Query("SELECT * FROM health_records WHERE id IN (SELECT MAX(id) FROM health_records GROUP BY type) ORDER BY type")
    fun getLatestRecordPerType(): Flow<List<HealthRecord>>

    @Query("SELECT * FROM health_records WHERE id = :id")
    suspend fun getById(id: Long): HealthRecord?
}
