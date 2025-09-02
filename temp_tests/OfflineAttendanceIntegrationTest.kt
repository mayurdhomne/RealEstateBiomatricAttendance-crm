package com.crm.realestate.offline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.crm.realestate.data.database.AttendanceDatabase
import com.crm.realestate.data.database.dao.AttendanceCacheDao
import com.crm.realestate.data.database.dao.OfflineAttendanceDao
import com.crm.realestate.data.database.entities.AttendanceCache
import com.crm.realestate.data.database.entities.OfflineAttendance
import com.crm.realestate.utils.AttendanceConflictResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for offline attendance functionality
 */
@RunWith(RobolectricTestRunner::class)
class OfflineAttendanceIntegrationTest {
    
    private lateinit var database: AttendanceDatabase
    private lateinit var offlineAttendanceDao: OfflineAttendanceDao
    private lateinit var attendanceCacheDao: AttendanceCacheDao
    private lateinit var conflictResolver: AttendanceConflictResolver
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        offlineAttendanceDao = database.offlineAttendanceDao()
        attendanceCacheDao = database.attendanceCacheDao()
        conflictResolver = AttendanceConflictResolver()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun `test offline attendance storage and retrieval`() = runBlocking {
        // Given
        val offlineAttendance = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = System.currentTimeMillis(),
            synced = false
        )
        
        // When
        offlineAttendanceDao.insertOfflineAttendance(offlineAttendance)
        val unsyncedRecords = offlineAttendanceDao.getUnsyncedAttendance()
        
        // Then
        assertEquals(1, unsyncedRecords.size)
        assertEquals(offlineAttendance.id, unsyncedRecords[0].id)
        assertEquals(offlineAttendance.employeeId, unsyncedRecords[0].employeeId)
        assertEquals(false, unsyncedRecords[0].synced)
    }
    
    @Test
    fun `test attendance cache for duplicate prevention`() = runBlocking {
        // Given
        val today = "2024-01-15"
        val currentTime = System.currentTimeMillis()
        val attendanceCache = AttendanceCache(
            date = today,
            lastPunchTime = currentTime,
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = "09:00:00",
            checkOutTime = null
        )
        
        // When
        attendanceCacheDao.insertOrUpdateCache(attendanceCache)
        val retrievedCache = attendanceCacheDao.getCacheForDate(today)
        
        // Then
        assertNotNull(retrievedCache)
        assertEquals(today, retrievedCache.date)
        assertEquals(true, retrievedCache.hasCheckedIn)
        assertEquals(false, retrievedCache.hasCheckedOut)
        assertEquals("09:00:00", retrievedCache.checkInTime)
    }
    
    @Test
    fun `test duplicate prevention within 2 minutes`() = runBlocking {
        // Given
        val today = "2024-01-15"
        val currentTime = System.currentTimeMillis()
        val attendanceCache = AttendanceCache(
            date = today,
            lastPunchTime = currentTime,
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = "09:00:00",
            checkOutTime = null
        )
        
        // When
        attendanceCacheDao.insertOrUpdateCache(attendanceCache)
        val recentPunch = attendanceCacheDao.getRecentPunch(today, currentTime + 60000) // 1 minute later
        
        // Then
        assertNotNull(recentPunch) // Should find recent punch within 2 minutes
    }
    
    @Test
    fun `test marking attendance as synced`() = runBlocking {
        // Given
        val offlineAttendance = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "fingerprint",
            attendanceType = "check_out",
            timestamp = System.currentTimeMillis(),
            synced = false
        )
        
        // When
        offlineAttendanceDao.insertOfflineAttendance(offlineAttendance)
        val unsyncedCountBefore = offlineAttendanceDao.getUnsyncedCount()
        
        offlineAttendanceDao.markAsSynced(offlineAttendance.id)
        val unsyncedCountAfter = offlineAttendanceDao.getUnsyncedCount()
        
        // Then
        assertEquals(1, unsyncedCountBefore)
        assertEquals(0, unsyncedCountAfter)
    }
    
    @Test
    fun `test conflict resolution for duplicate records`() {
        // Given
        val timestamp1 = System.currentTimeMillis()
        val timestamp2 = timestamp1 + 30000 // 30 seconds later
        
        val attendance1 = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = timestamp1,
            synced = false
        )
        
        val attendance2 = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = timestamp2,
            synced = false
        )
        
        // When
        val isDuplicate = conflictResolver.isSameLogicalOperation(attendance1, attendance2)
        val deduplicatedRecords = conflictResolver.deduplicateOfflineRecords(listOf(attendance1, attendance2))
        
        // Then
        assertTrue(isDuplicate)
        assertEquals(1, deduplicatedRecords.size)
        assertEquals(attendance2.id, deduplicatedRecords[0].id) // Should keep the newer one
    }
    
    @Test
    fun `test attendance validation`() {
        // Given - Valid attendance
        val validAttendance = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = System.currentTimeMillis(),
            synced = false
        )
        
        // Given - Invalid attendance (invalid coordinates)
        val invalidAttendance = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 0.0,
            longitude = 0.0,
            scanType = "invalid_type",
            attendanceType = "check_in",
            timestamp = System.currentTimeMillis(),
            synced = false
        )
        
        // When
        val validResult = conflictResolver.validateOfflineAttendance(validAttendance)
        val invalidResult = conflictResolver.validateOfflineAttendance(invalidAttendance)
        
        // Then
        assertTrue(validResult is AttendanceConflictResolver.ValidationResult.Valid)
        assertTrue(invalidResult is AttendanceConflictResolver.ValidationResult.Invalid)
        
        if (invalidResult is AttendanceConflictResolver.ValidationResult.Invalid) {
            assertTrue(invalidResult.errors.isNotEmpty())
            assertTrue(invalidResult.errors.any { it.contains("coordinates") })
            assertTrue(invalidResult.errors.any { it.contains("scan type") })
        }
    }
    
    @Test
    fun `test cleanup of old synced records`() = runBlocking {
        // Given
        val oldTimestamp = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L) // 8 days ago
        val recentTimestamp = System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L) // 1 day ago
        
        val oldSyncedAttendance = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = oldTimestamp,
            synced = true
        )
        
        val recentSyncedAttendance = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_out",
            timestamp = recentTimestamp,
            synced = true
        )
        
        // When
        offlineAttendanceDao.insertOfflineAttendance(oldSyncedAttendance)
        offlineAttendanceDao.insertOfflineAttendance(recentSyncedAttendance)
        
        val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val deletedCount = offlineAttendanceDao.deleteSyncedOlderThan(weekAgo)
        
        // Then
        assertEquals(1, deletedCount) // Should delete only the old synced record
    }
    
    @Test
    fun `test attendance cache cleanup`() = runBlocking {
        // Given
        val oldDate = "2024-01-01"
        val recentDate = "2024-01-20"
        val cutoffDate = "2024-01-15"
        
        val oldCache = AttendanceCache(
            date = oldDate,
            lastPunchTime = System.currentTimeMillis(),
            hasCheckedIn = true,
            hasCheckedOut = true,
            checkInTime = "09:00:00",
            checkOutTime = "17:00:00"
        )
        
        val recentCache = AttendanceCache(
            date = recentDate,
            lastPunchTime = System.currentTimeMillis(),
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = "09:00:00",
            checkOutTime = null
        )
        
        // When
        attendanceCacheDao.insertOrUpdateCache(oldCache)
        attendanceCacheDao.insertOrUpdateCache(recentCache)
        
        val deletedCount = attendanceCacheDao.deleteOldCache(cutoffDate)
        val remainingCache = attendanceCacheDao.getCacheForDate(recentDate)
        val deletedCache = attendanceCacheDao.getCacheForDate(oldDate)
        
        // Then
        assertEquals(1, deletedCount)
        assertNotNull(remainingCache)
        assertEquals(null, deletedCache)
    }
}