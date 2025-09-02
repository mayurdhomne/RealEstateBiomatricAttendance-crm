package com.crm.realestate.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_attendance")
data class OfflineAttendance(
    @PrimaryKey val id: String,
    val employeeId: String,
    val latitude: Double,
    val longitude: Double,
    val scanType: String,
    val attendanceType: String, // "check_in" or "check_out"
    val timestamp: Long,
    val synced: Boolean = false
)