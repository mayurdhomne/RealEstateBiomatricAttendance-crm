package com.crm.realestate.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.crm.realestate.data.database.dao.AttendanceCacheDao
import com.crm.realestate.data.database.dao.OfflineAttendanceDao
import com.crm.realestate.data.database.entities.AttendanceCache
import com.crm.realestate.data.database.entities.OfflineAttendance

/**
 * Room database for offline attendance storage and caching
 */
@Database(
    entities = [OfflineAttendance::class, AttendanceCache::class],
    version = 1,
    exportSchema = false
)
abstract class AttendanceDatabase : RoomDatabase() {
    
    abstract fun offlineAttendanceDao(): OfflineAttendanceDao
    abstract fun attendanceCacheDao(): AttendanceCacheDao
    
    companion object {
        @Volatile
        private var INSTANCE: AttendanceDatabase? = null
        
        private const val DATABASE_NAME = "attendance_database"
        
        /**
         * Get database instance using singleton pattern
         */
        fun getDatabase(context: Context): AttendanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AttendanceDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Clear database instance (for testing purposes)
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}