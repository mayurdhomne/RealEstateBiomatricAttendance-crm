package com.crm.realestate.data.database.dao

import androidx.room.*
import com.crm.realestate.data.database.entities.AttendanceCache
import kotlinx.coroutines.flow.Flow

/**
 * DAO interface for attendance cache operations
 */
@Dao
interface AttendanceCacheDao {
    
    /**
     * Insert or update attendance cache for a date
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateCache(cache: AttendanceCache): Long
    
    /**
     * Get attendance cache for specific date
     */
    @Query("SELECT * FROM attendance_cache WHERE date = :date")
    fun getCacheForDate(date: String): AttendanceCache?
    
    /**
     * Get attendance cache for today as Flow
     */
    @Query("SELECT * FROM attendance_cache WHERE date = :date")
    fun getCacheForDateFlow(date: String): Flow<AttendanceCache?>
    
    /**
     * Check if can punch attendance (2-minute cooldown)
     */
    @Query("SELECT * FROM attendance_cache WHERE date = :date AND ((:currentTime - last_punch_time) < 120000)")
    fun getRecentPunch(date: String, currentTime: Long): AttendanceCache?
    
    /**
     * Update last punch time for duplicate prevention
     */
    @Query("UPDATE attendance_cache SET last_punch_time = :timestamp WHERE date = :date")
    fun updateLastPunchTime(timestamp: Long, date: String): Int
    
    /**
     * Update check-in status
     */
    @Query("UPDATE attendance_cache SET has_checked_in = :hasCheckedIn, check_in_time = :checkInTime WHERE date = :date")
    fun updateCheckInStatus(hasCheckedIn: Boolean, checkInTime: String?, date: String): Int
    
    /**
     * Update check-out status
     */
    @Query("UPDATE attendance_cache SET has_checked_out = :hasCheckedOut, check_out_time = :checkOutTime WHERE date = :date")
    fun updateCheckOutStatus(hasCheckedOut: Boolean, checkOutTime: String?, date: String): Int
    
    /**
     * Delete cache records older than specified days
     */
    @Query("DELETE FROM attendance_cache WHERE date < :cutoffDate")
    fun deleteOldCache(cutoffDate: String): Int
    
    /**
     * Delete all cache records
     */
    @Query("DELETE FROM attendance_cache")
    fun deleteAll(): Int
}