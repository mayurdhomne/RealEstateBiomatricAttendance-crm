package com.crm.realestate.data.database.dao

import androidx.room.*
import com.crm.realestate.data.database.entities.OfflineAttendance
import kotlinx.coroutines.flow.Flow

/**
 * DAO interface for offline attendance operations
 */
@Dao
interface OfflineAttendanceDao {
    
    /**
     * Insert offline attendance record
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOfflineAttendance(attendance: OfflineAttendance): Long
    
    /**
     * Get all unsynced offline attendance records
     */
    @Query("SELECT * FROM offline_attendance WHERE synced = 0 ORDER BY timestamp ASC")
    fun getUnsyncedAttendance(): List<OfflineAttendance>
    
    /**
     * Get all unsynced attendance as Flow for observing changes
     */
    @Query("SELECT * FROM offline_attendance WHERE synced = 0 ORDER BY timestamp ASC")
    fun getUnsyncedAttendanceFlow(): Flow<List<OfflineAttendance>>
    
    /**
     * Mark attendance record as synced
     */
    @Query("UPDATE offline_attendance SET synced = 1 WHERE id = :attendanceId")
    fun markAsSynced(attendanceId: String): Int
    
    /**
     * Delete synced attendance records older than specified timestamp
     */
    @Query("DELETE FROM offline_attendance WHERE synced = 1 AND timestamp < :timestamp")
    fun deleteSyncedOlderThan(timestamp: Long): Int
    
    /**
     * Get count of unsynced records
     */
    @Query("SELECT COUNT(*) FROM offline_attendance WHERE synced = 0")
    fun getUnsyncedCount(): Int
    
    /**
     * Get count of unsynced records as Flow
     */
    @Query("SELECT COUNT(*) FROM offline_attendance WHERE synced = 0")
    fun getUnsyncedCountFlow(): Flow<Int>
    
    /**
     * Get offline attendance record by ID
     */
    @Query("SELECT * FROM offline_attendance WHERE id = :id")
    fun getOfflineAttendanceById(id: String): OfflineAttendance?
    
    /**
     * Delete all offline attendance records
     */
    @Query("DELETE FROM offline_attendance")
    fun deleteAll(): Int
}