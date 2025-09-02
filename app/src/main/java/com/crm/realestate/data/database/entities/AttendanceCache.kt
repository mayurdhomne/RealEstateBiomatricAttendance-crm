package com.crm.realestate.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for caching daily attendance status
 * Used for duplicate prevention and tracking daily punch status
 */
@Entity(tableName = "attendance_cache")
data class AttendanceCache(
    @PrimaryKey
    val date: String, // Format: "yyyy-MM-dd"
    
    @ColumnInfo(name = "last_punch_time")
    val lastPunchTime: Long,
    
    @ColumnInfo(name = "has_checked_in")
    val hasCheckedIn: Boolean,
    
    @ColumnInfo(name = "has_checked_out")
    val hasCheckedOut: Boolean,
    
    @ColumnInfo(name = "check_in_time")
    val checkInTime: String?,
    
    @ColumnInfo(name = "check_out_time")
    val checkOutTime: String?
)