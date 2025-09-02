package com.crm.realestate.database.dao

import androidx.room.*
import com.crm.realestate.database.entity.OfflineAttendance

@Dao
interface OfflineAttendanceDao {
    
    @Query("SELECT * FROM offline_attendance WHERE synced = 0")
    fun getUnsyncedAttendance(): List<OfflineAttendance>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAttendance(attendance: OfflineAttendance): Long
    
    @Update
    fun updateAttendance(attendance: OfflineAttendance): Int
    
    @Query("UPDATE offline_attendance SET synced = 1 WHERE id = :id")
    fun markAsSynced(id: String): Int
    
    @Query("DELETE FROM offline_attendance WHERE synced = 1")
    fun deleteSyncedAttendance(): Int
    
    @Query("SELECT COUNT(*) FROM offline_attendance WHERE synced = 0")
    fun getUnsyncedCount(): Int
    
    @Query("SELECT * FROM offline_attendance WHERE id = :id")
    fun getOfflineAttendanceById(id: String): OfflineAttendance?
}