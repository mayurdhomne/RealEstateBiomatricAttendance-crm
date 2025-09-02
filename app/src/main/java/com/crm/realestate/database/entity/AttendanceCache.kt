package com.crm.realestate.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_cache")
data class AttendanceCache(
    @PrimaryKey val date: String,
    val lastPunchTime: Long,
    val hasCheckedIn: Boolean,
    val hasCheckedOut: Boolean
)