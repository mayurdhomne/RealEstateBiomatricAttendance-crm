package com.crm.realestate.database.dao

import androidx.room.*
import com.crm.realestate.database.entity.AttendanceCache

@Dao
interface AttendanceCacheDao {
    
    @Query("SELECT * FROM attendance_cache WHERE date = :date")
    fun getAttendanceForDate(date: String): AttendanceCache?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateAttendance(attendanceCache: AttendanceCache): Long
    
    @Query("SELECT * FROM attendance_cache WHERE date = :date AND lastPunchTime > :timeThreshold")
    fun getRecentAttendance(date: String, timeThreshold: Long): AttendanceCache?
    
    @Query("DELETE FROM attendance_cache WHERE date < :cutoffDate")
    fun deleteOldRecords(cutoffDate: String): Int
}