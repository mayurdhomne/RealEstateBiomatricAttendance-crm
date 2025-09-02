package com.crm.realestate.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing offline attendance data
 * Used when network is unavailable during attendance punching
 */
@Entity(tableName = "offline_attendance")
data class OfflineAttendance(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "employee_id")
    val employeeId: String,
    
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    
    @ColumnInfo(name = "scan_type")
    val scanType: String, // "face" or "fingerprint"
    
    @ColumnInfo(name = "attendance_type")
    val attendanceType: String, // "check_in" or "check_out"
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "synced")
    val synced: Boolean = false
)