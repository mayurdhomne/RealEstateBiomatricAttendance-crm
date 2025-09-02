package com.crm.realestate.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.crm.realestate.data.database.AttendanceDatabase
import com.crm.realestate.data.database.dao.AttendanceCacheDao
import com.crm.realestate.data.database.dao.OfflineAttendanceDao
import com.crm.realestate.data.database.entities.AttendanceCache
import com.crm.realestate.data.database.entities.OfflineAttendance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Integration tests for database operations
 * Tests Room database functionality with real database operations
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseIntegrationTest {

    private lateinit var database: AttendanceDatabase
    private lateinit var offlineAttendanceDao: OfflineAttendanceDao
    private lateinit var attendanceCacheDao: AttendanceCacheDao
    private lateinit var context: Context

    private val testOfflineAttendance = OfflineAttendance(
        id = "test-1",
        employeeId = "EMP001",
        latitude = 40.7128,
        longitude = -74.0060,
        scanType = "face",
        attendanceType = "check_in",
        timestamp = System.currentTimeMillis(),
        synced = false
    )

    private val testAttendanceCache = AttendanceCache(
        date = getCurrentDateString(),
        lastPunchTime = System.currentTimeMillis(),
        hasCheckedIn = true,
        hasCheckedOut = false,
        checkInTime = "09:00:00",
        checkOutTime = null
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            AttendanceDatabase::class.java
        ).allowMainThreadQueries().build()

        offlineAttendanceDao = database.offlineAttendanceDao()
        attendanceCacheDao = database.attendanceCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and retrieve offline attendance`() = runTest {
        // When
        offlineAttendanceDao.insertOfflineAttendance(testOfflineAttendance)

        // Then
        val retrieved = offlineAttendanceDao.getOfflineAttendanceById(testOfflineAttendance.id)
        assertNotNull(retrieved)
        assertEquals(testOfflineAttendance.id, retrieved?.id)
        assertEquals(testOfflineAttendance.employeeId, retrieved?.employeeId)
        assertEquals(testOfflineAttendance.latitude, retrieved!!.latitude, 0.0001)
        assertEquals(testOfflineAttendance.longitude, retrieved.longitude, 0.0001)
        assertEquals(testOfflineAttendance.scanType, retrieved.scanType)
        assertEquals(testOfflineAttendance.attendanceType, retrieved.attendanceType)
        assertEquals(testOfflineAttendance.synced, retrieved.synced)
    }

    @Test
    fun `get unsynced attendance returns only unsynced records`() = runTest {
        // Given
        val syncedAttendance = testOfflineAttendance.copy(id = "synced-1", synced = true)
        val unsyncedAttendance1 = testOfflineAttendance.copy(id = "unsynced-1", synced = false)
        val unsyncedAttendance2 = testOfflineAttendance.copy(id = "unsynced-2", synced = false)

        offlineAttendanceDao.insertOfflineAttendance(syncedAttendance)
        offlineAttendanceDao.insertOfflineAttendance(unsyncedAttendance1)
        offlineAttendanceDao.insertOfflineAttendance(unsyncedAttendance2)

        // When
        val unsyncedRecords = offlineAttendanceDao.getUnsyncedAttendance()

        // Then
        assertEquals(2, unsyncedRecords.size)
        assertTrue(unsyncedRecords.none { it.synced })
        assertTrue(unsyncedRecords.any { it.id == "unsynced-1" })
        assertTrue(unsyncedRecords.any { it.id == "unsynced-2" })
    }

    @Test
    fun `mark as synced updates sync status`() = runTest {
        // Given
        offlineAttendanceDao.insertOfflineAttendance(testOfflineAttendance)

        // When
        offlineAttendanceDao.markAsSynced(testOfflineAttendance.id)

        // Then
        val updated = offlineAttendanceDao.getOfflineAttendanceById(testOfflineAttendance.id)
        assertTrue(updated?.synced == true)
    }

    @Test
    fun `get unsynced count returns correct count`() = runTest {
        // Given
        val unsynced1 = testOfflineAttendance.copy(id = "unsynced-1", synced = false)
        val unsynced2 = testOfflineAttendance.copy(id = "unsynced-2", synced = false)
        val synced = testOfflineAttendance.copy(id = "synced-1", synced = true)

        offlineAttendanceDao.insertOfflineAttendance(unsynced1)
        offlineAttendanceDao.insertOfflineAttendance(unsynced2)
        offlineAttendanceDao.insertOfflineAttendance(synced)

        // When
        val count = offlineAttendanceDao.getUnsyncedCount()

        // Then
        assertEquals(2, count)
    }

    @Test
    fun `delete synced older than removes old synced records`() = runTest {
        // Given
        val currentTime = System.currentTimeMillis()
        val oldSynced = testOfflineAttendance.copy(
            id = "old-synced",
            timestamp = currentTime - TimeUnit.DAYS.toMillis(8),
            synced = true
        )
        val recentSynced = testOfflineAttendance.copy(
            id = "recent-synced",
            timestamp = currentTime - TimeUnit.DAYS.toMillis(3),
            synced = true
        )
        val oldUnsynced = testOfflineAttendance.copy(
            id = "old-unsynced",
            timestamp = currentTime - TimeUnit.DAYS.toMillis(8),
            synced = false
        )

        offlineAttendanceDao.insertOfflineAttendance(oldSynced)
        offlineAttendanceDao.insertOfflineAttendance(recentSynced)
        offlineAttendanceDao.insertOfflineAttendance(oldUnsynced)

        // When
        val cutoffTime = currentTime - TimeUnit.DAYS.toMillis(7)
        offlineAttendanceDao.deleteSyncedOlderThan(cutoffTime)

        // Then
        assertNull(offlineAttendanceDao.getOfflineAttendanceById("old-synced"))
        assertNotNull(offlineAttendanceDao.getOfflineAttendanceById("recent-synced"))
        assertNotNull(offlineAttendanceDao.getOfflineAttendanceById("old-unsynced")) // Unsynced should remain
    }

    @Test
    fun `insert and retrieve attendance cache`() = runTest {
        // When
        attendanceCacheDao.insertOrUpdateCache(testAttendanceCache)

        // Then
        val retrieved = attendanceCacheDao.getCacheForDate(testAttendanceCache.date)
        assertNotNull(retrieved)
        assertEquals(testAttendanceCache.date, retrieved?.date)
        assertEquals(testAttendanceCache.hasCheckedIn, retrieved?.hasCheckedIn)
        assertEquals(testAttendanceCache.hasCheckedOut, retrieved?.hasCheckedOut)
        assertEquals(testAttendanceCache.checkInTime, retrieved?.checkInTime)
        assertEquals(testAttendanceCache.checkOutTime, retrieved?.checkOutTime)
    }

    @Test
    fun `update existing attendance cache`() = runTest {
        // Given
        attendanceCacheDao.insertOrUpdateCache(testAttendanceCache)

        // When
        val updatedCache = testAttendanceCache.copy(
            hasCheckedOut = true,
            checkOutTime = "17:00:00"
        )
        attendanceCacheDao.insertOrUpdateCache(updatedCache)

        // Then
        val retrieved = attendanceCacheDao.getCacheForDate(testAttendanceCache.date)
        assertNotNull(retrieved)
        assertTrue(retrieved?.hasCheckedOut == true)
        assertEquals("17:00:00", retrieved?.checkOutTime)
    }

    @Test
    fun `get recent punch within time window`() = runTest {
        // Given
        val currentTime = System.currentTimeMillis()
        val recentCache = testAttendanceCache.copy(
            lastPunchTime = currentTime - TimeUnit.MINUTES.toMillis(1) // 1 minute ago
        )
        attendanceCacheDao.insertOrUpdateCache(recentCache)

        // When
        val twoMinutesAgo = currentTime - TimeUnit.MINUTES.toMillis(2)
        val recentPunch = attendanceCacheDao.getRecentPunch(testAttendanceCache.date, twoMinutesAgo)

        // Then
        assertNotNull(recentPunch)
        assertEquals(testAttendanceCache.date, recentPunch?.date)
    }

    @Test
    fun `get recent punch outside time window returns null`() = runTest {
        // Given
        val currentTime = System.currentTimeMillis()
        val oldCache = testAttendanceCache.copy(
            lastPunchTime = currentTime - TimeUnit.MINUTES.toMillis(5) // 5 minutes ago
        )
        attendanceCacheDao.insertOrUpdateCache(oldCache)

        // When
        val twoMinutesAgo = currentTime - TimeUnit.MINUTES.toMillis(2)
        val recentPunch = attendanceCacheDao.getRecentPunch(testAttendanceCache.date, twoMinutesAgo)

        // Then
        assertNull(recentPunch)
    }

    @Test
    fun `delete old cache removes old records`() = runTest {
        // Given
        val today = getCurrentDateString()
        val oldDate = getDateString(-35) // 35 days ago
        val recentDate = getDateString(-15) // 15 days ago

        val oldCache = testAttendanceCache.copy(date = oldDate)
        val recentCache = testAttendanceCache.copy(date = recentDate)
        val todayCache = testAttendanceCache.copy(date = today)

        attendanceCacheDao.insertOrUpdateCache(oldCache)
        attendanceCacheDao.insertOrUpdateCache(recentCache)
        attendanceCacheDao.insertOrUpdateCache(todayCache)

        // When
        val cutoffDate = getDateString(-30) // 30 days ago
        attendanceCacheDao.deleteOldCache(cutoffDate)

        // Then
        assertNull(attendanceCacheDao.getCacheForDate(oldDate))
        assertNotNull(attendanceCacheDao.getCacheForDate(recentDate))
        assertNotNull(attendanceCacheDao.getCacheForDate(today))
    }

    @Test
    fun `database handles concurrent operations`() = runTest {
        // Given
        val attendance1 = testOfflineAttendance.copy(id = "concurrent-1")
        val attendance2 = testOfflineAttendance.copy(id = "concurrent-2")
        val attendance3 = testOfflineAttendance.copy(id = "concurrent-3")

        // When - simulate concurrent inserts
        offlineAttendanceDao.insertOfflineAttendance(attendance1)
        offlineAttendanceDao.insertOfflineAttendance(attendance2)
        offlineAttendanceDao.insertOfflineAttendance(attendance3)

        // Then
        val allRecords = offlineAttendanceDao.getUnsyncedAttendance()
        assertEquals(3, allRecords.size)
        assertTrue(allRecords.any { it.id == "concurrent-1" })
        assertTrue(allRecords.any { it.id == "concurrent-2" })
        assertTrue(allRecords.any { it.id == "concurrent-3" })
    }

    @Test
    fun `database handles large dataset operations`() = runTest {
        // Given - insert 100 records
        val records = (1..100).map { index ->
            testOfflineAttendance.copy(
                id = "bulk-$index",
                timestamp = System.currentTimeMillis() + index
            )
        }

        // When
        records.forEach { record ->
            offlineAttendanceDao.insertOfflineAttendance(record)
        }

        // Then
        val retrievedRecords = offlineAttendanceDao.getUnsyncedAttendance()
        assertEquals(100, retrievedRecords.size)
        assertEquals(100, offlineAttendanceDao.getUnsyncedCount())
    }

    @Test
    fun `database maintains data integrity with invalid data`() = runTest {
        // Given - record with extreme values
        val extremeRecord = testOfflineAttendance.copy(
            id = "extreme-test",
            latitude = 90.0, // Max valid latitude
            longitude = -180.0, // Min valid longitude
            timestamp = 0L // Epoch time
        )

        // When
        offlineAttendanceDao.insertOfflineAttendance(extremeRecord)

        // Then
        val retrieved = offlineAttendanceDao.getOfflineAttendanceById("extreme-test")
        assertNotNull(retrieved)
        assertEquals(90.0, retrieved!!.latitude, 0.0001)
        assertEquals(-180.0, retrieved.longitude, 0.0001)
        assertEquals(0L, retrieved.timestamp)
    }

    @Test
    fun `database handles null and empty string values correctly`() = runTest {
        // Given
        val cacheWithNulls = AttendanceCache(
            date = getCurrentDateString(),
            lastPunchTime = System.currentTimeMillis(),
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = null, // Null value
            checkOutTime = null  // Null value
        )

        // When
        attendanceCacheDao.insertOrUpdateCache(cacheWithNulls)

        // Then
        val retrieved = attendanceCacheDao.getCacheForDate(cacheWithNulls.date)
        assertNotNull(retrieved)
        assertNull(retrieved?.checkInTime)
        assertNull(retrieved?.checkOutTime)
    }

    private fun getCurrentDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun getDateString(daysOffset: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, daysOffset)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }
}