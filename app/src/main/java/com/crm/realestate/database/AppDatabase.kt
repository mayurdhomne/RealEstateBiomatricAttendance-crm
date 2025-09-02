package com.crm.realestate.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.crm.realestate.database.dao.AttendanceCacheDao
import com.crm.realestate.database.dao.OfflineAttendanceDao
import com.crm.realestate.database.entity.AttendanceCache
import com.crm.realestate.database.entity.OfflineAttendance

@Database(
    entities = [OfflineAttendance::class, AttendanceCache::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun offlineAttendanceDao(): OfflineAttendanceDao
    abstract fun attendanceCacheDao(): AttendanceCacheDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "employee_attendance_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}