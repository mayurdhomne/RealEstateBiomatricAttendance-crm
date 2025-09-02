package com.crm.realestate.performance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.crm.realestate.data.database.AttendanceDatabase
import com.crm.realestate.data.database.dao.AttendanceCacheDao
import com.crm.realestate.data.database.dao.OfflineAttendanceDao
import com.crm.realestate.data.database.entities.AttendanceCache
import com.crm.realestate.data.database.entities.OfflineAttendance
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Performance tests for database operations
 * Tests query performance, bulk operations, and resource usage
 */
@RunWith(RobolectricTestRunner::class)
class DatabasePerformanceTest {

    private lateinit var database: AttendanceDatabase
    private lateinit var offlineAttendanceDao: OfflineAttendanceDao
    private lateinit var attendanceCacheDao: AttendanceCacheDao
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Use in-memory database for consistent performance testing
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
    fun `single insert performance test`() = runTest {
        // Given
        val iterations = 1000
        val maxAcceptableTimeMs = 5L // 5ms max per insert

        val testRecord = OfflineAttendance(
            id = "test-1",
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = System.currentTimeMillis(),
            synced = false
        )

        // When
        val totalTime = measureTimeMillis {
            repeat(iterations) { index ->
                val record = testRecord.copy(id = "test-$index")
                offlineAttendanceDao.insertOfflineAttendance(record)
            }
        }

        // Then
        val averageTime = totalTime / iterations
        assertTrue(
            "Average insert time ($averageTime ms) exceeds maximum acceptable time ($maxAcceptableTimeMs ms)",
            averageTime <= maxAcceptableTimeMs
        )
        
        println("Single insert performance - Average time: ${averageTime}ms over $iterations iterations")
    }

    @Test
    fun `bulk insert performance test`() = runTest {
        // Given
        val recordCount = 10000
        val maxAcceptableTimeMs = 1000L // 1 second max for 10k records

        val records = (1..recordCount).map { index ->
            OfflineAttendance(
                id = "bulk-$index",
                employeeId = "EMP001",
                latitude = 40.7128 + (index * 0.0001),
                longitude = -74.0060 + (index * 0.0001),
                scanType = if (index % 2 == 0) "face" else "fingerprint",
                attendanceType = if (index % 2 == 0) "check_in" else "check_out",
                timestamp = System.currentTimeMillis() + index,
                synced = false
            )
        }

        // When
        val insertTime = measureTimeMillis {
            records.forEach { record ->
                offlineAttendanceDao.insertOfflineAttendance(record)
            }
        }

        // Then
        assertTrue(
            "Bulk insert time ($insertTime ms) exceeds maximum acceptable time ($maxAcceptableTimeMs ms)",
            insertTime <= maxAcceptableTimeMs
        )
        
        val recordsPerSecond = (recordCount * 1000) / insertTime
        println("Bulk insert performance - $recordCount records in ${insertTime}ms ($recordsPerSecond records/sec)")
    }

    @Test
    fun `query performance with large dataset`() = runTest {
        // Given - Insert large dataset
        val recordCount = 5000
        val records = (1..recordCount).map { index ->
            OfflineAttendance(
                id = "query-test-$index",
                employeeId = "EMP${index % 100}", // 100 different employees
                latitude = 40.7128,
                longitude = -74.0060,
                scanType = "face",
                attendanceType = "check_in",
                timestamp = System.currentTimeMillis() + index,
                synced = index % 10 == 0 // 10% synced
            )
        }

        records.forEach { record ->
            offlineAttendanceDao.insertOfflineAttendance(record)
        }

        val maxQueryTimeMs = 100L // 100ms max per query

        // When & Then - Test different query types
        
        // Test 1: Get all unsynced records
        val unsyncedQueryTime = measureTimeMillis {
            val unsyncedRecords = offlineAttendanceDao.getUnsyncedAttendance()
            assertTrue("Should find unsynced records", unsyncedRecords.isNotEmpty())
        }
        assertTrue("Unsynced query too slow: ${unsyncedQueryTime}ms", unsyncedQueryTime <= maxQueryTimeMs)

        // Test 2: Get unsynced count
        val countQueryTime = measureTimeMillis {
            val count = offlineAttendanceDao.getUnsyncedCount()
            assertTrue("Should have unsynced count > 0", count > 0)
        }
        assertTrue("Count query too slow: ${countQueryTime}ms", countQueryTime <= maxQueryTimeMs)

        // Test 3: Get specific record by ID
        val singleRecordQueryTime = measureTimeMillis {
            val record = offlineAttendanceDao.getOfflineAttendanceById("query-test-1000")
            assertNotNull("Should find specific record", record)
        }
        assertTrue("Single record query too slow: ${singleRecordQueryTime}ms", singleRecordQueryTime <= maxQueryTimeMs)

        println("Query performance with $recordCount records:")
        println("  Unsynced query: ${unsyncedQueryTime}ms")
        println("  Count query: ${countQueryTime}ms")
        println("  Single record query: ${singleRecordQueryTime}ms")
    }

    @Test
    fun `update operations performance test`() = runTest {
        // Given - Insert test records
        val recordCount = 1000
        val records = (1..recordCount).map { index ->
            OfflineAttendance(
                id = "update-test-$index",
                employeeId = "EMP001",
                latitude = 40.7128,
                longitude = -74.0060,
                scanType = "face",
                attendanceType = "check_in",
                timestamp = System.currentTimeMillis() + index,
                synced = false
            )
        }

        records.forEach { record ->
            offlineAttendanceDao.insertOfflineAttendance(record)
        }

        val maxUpdateTimeMs = 500L // 500ms max for all updates

        // When
        val updateTime = measureTimeMillis {
            records.forEach { record ->
                offlineAttendanceDao.markAsSynced(record.id)
            }
        }

        // Then
        assertTrue(
            "Update operations too slow: ${updateTime}ms for $recordCount records",
            updateTime <= maxUpdateTimeMs
        )

        // Verify updates were successful
        val unsyncedCount = offlineAttendanceDao.getUnsyncedCount()
        assertEquals("All records should be marked as synced", 0, unsyncedCount)

        println("Update performance - $recordCount records marked as synced in ${updateTime}ms")
    }

    @Test
    fun `delete operations performance test`() = runTest {
        // Given - Insert test records with different timestamps
        val recordCount = 2000
        val currentTime = System.currentTimeMillis()
        val records = (1..recordCount).map { index ->
            OfflineAttendance(
                id = "delete-test-$index",
                employeeId = "EMP001",
                latitude = 40.7128,
                longitude = -74.0060,
                scanType = "face",
                attendanceType = "check_in",
                timestamp = currentTime - TimeUnit.DAYS.toMillis(index.toLong()), // Spread over many days
                synced = index % 2 == 0 // Half are synced
            )
        }

        records.forEach { record ->
            offlineAttendanceDao.insertOfflineAttendance(record)
        }

        val maxDeleteTimeMs = 200L // 200ms max for delete operation

        // When - Delete old synced records (older than 7 days)
        val cutoffTime = currentTime - TimeUnit.DAYS.toMillis(7)
        val deleteTime = measureTimeMillis {
            offlineAttendanceDao.deleteSyncedOlderThan(cutoffTime)
        }

        // Then
        assertTrue(
            "Delete operation too slow: ${deleteTime}ms",
            deleteTime <= maxDeleteTimeMs
        )

        // Verify correct records were deleted
        val remainingCount = offlineAttendanceDao.getUnsyncedCount()
        assertTrue("Some records should remain", remainingCount > 0)

        println("Delete performance - Old synced records deleted in ${deleteTime}ms")
    }

    @Test
    fun `attendance cache performance test`() = runTest {
        // Given
        val cacheCount = 1000
        val maxOperationTimeMs = 50L // 50ms max per operation type

        val caches = (1..cacheCount).map { index ->
            AttendanceCache(
                date = getDateString(-index),
                lastPunchTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(index.toLong()),
                hasCheckedIn = true,
                hasCheckedOut = index % 2 == 0,
                checkInTime = "09:00:00",
                checkOutTime = if (index % 2 == 0) "17:00:00" else null
            )
        }

        // When & Then - Test insert performance
        val insertTime = measureTimeMillis {
            caches.forEach { cache ->
                attendanceCacheDao.insertOrUpdateCache(cache)
            }
        }
        assertTrue("Cache insert too slow: ${insertTime}ms", insertTime <= maxOperationTimeMs * cacheCount / 10)

        // Test query performance
        val queryTime = measureTimeMillis {
            val todayCache = attendanceCacheDao.getCacheForDate(getCurrentDateString())
            val recentPunch = attendanceCacheDao.getRecentPunch(
                getCurrentDateString(),
                System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2)
            )
        }
        assertTrue("Cache query too slow: ${queryTime}ms", queryTime <= maxOperationTimeMs)

        // Test delete performance
        val deleteTime = measureTimeMillis {
            val cutoffDate = getDateString(-30)
            attendanceCacheDao.deleteOldCache(cutoffDate)
        }
        assertTrue("Cache delete too slow: ${deleteTime}ms", deleteTime <= maxOperationTimeMs)

        println("Attendance cache performance:")
        println("  Insert $cacheCount records: ${insertTime}ms")
        println("  Query operations: ${queryTime}ms")
        println("  Delete old records: ${deleteTime}ms")
    }

    @Test
    fun `concurrent database operations performance`() = runTest {
        // Given
        val concurrentOperations = 20
        val operationsPerThread = 50
        val maxTotalTimeMs = 2000L // 2 seconds max for all concurrent operations

        // When
        val totalTime = measureTimeMillis {
            val jobs = coroutineScope {
                (1..concurrentOperations).map { threadIndex ->
                    async {
                    repeat(operationsPerThread) { opIndex ->
                        val record = OfflineAttendance(
                            id = "concurrent-$threadIndex-$opIndex",
                            employeeId = "EMP$threadIndex",
                            latitude = 40.7128,
                            longitude = -74.0060,
                            scanType = "face",
                            attendanceType = "check_in",
                            timestamp = System.currentTimeMillis() + opIndex,
                            synced = false
                        )
                        
                        // Mix of operations
                        when (opIndex % 3) {
                            0 -> offlineAttendanceDao.insertOfflineAttendance(record)
                            1 -> offlineAttendanceDao.getUnsyncedCount()
                            2 -> offlineAttendanceDao.getUnsyncedAttendance()
                        }
                    }
                }
            }
            }
            jobs.forEach { it.await() }
        }

        // Then
        assertTrue(
            "Concurrent operations too slow: ${totalTime}ms for ${concurrentOperations * operationsPerThread} operations",
            totalTime <= maxTotalTimeMs
        )

        // Verify data integrity
        val finalCount = offlineAttendanceDao.getUnsyncedCount()
        val expectedInserts = concurrentOperations * (operationsPerThread / 3 + if (operationsPerThread % 3 > 0) 1 else 0)
        assertTrue("Data integrity check failed", finalCount >= expectedInserts * 0.8) // Allow some variance

        println("Concurrent operations - $concurrentOperations threads, $operationsPerThread ops each: ${totalTime}ms")
    }

    @Test
    fun `database memory usage under load`() = runTest {
        // Given
        val recordCount = 5000
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // When - Perform memory-intensive operations
        repeat(recordCount) { index ->
            val record = OfflineAttendance(
                id = "memory-test-$index",
                employeeId = "EMP001",
                latitude = 40.7128,
                longitude = -74.0060,
                scanType = "face",
                attendanceType = "check_in",
                timestamp = System.currentTimeMillis() + index,
                synced = false
            )
            offlineAttendanceDao.insertOfflineAttendance(record)
            
            // Perform queries to stress memory
            if (index % 100 == 0) {
                offlineAttendanceDao.getUnsyncedAttendance()
                offlineAttendanceDao.getUnsyncedCount()
            }
        }

        // Force garbage collection
        System.gc()
        Thread.sleep(100)

        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory

        // Then - Memory increase should be reasonable (less than 10MB)
        val maxAcceptableMemoryIncrease = 10 * 1024 * 1024 // 10MB
        assertTrue(
            "Memory increase (${memoryIncrease / 1024 / 1024}MB) exceeds acceptable limit (${maxAcceptableMemoryIncrease / 1024 / 1024}MB)",
            memoryIncrease <= maxAcceptableMemoryIncrease
        )

        println("Memory usage - Increase: ${memoryIncrease / 1024 / 1024}MB after $recordCount operations")
    }

    @Test
    fun `database performance regression test`() = runTest {
        // Given - Baseline performance expectations
        val baselineInsertMs = 5L
        val baselineQueryMs = 50L
        val baselineUpdateMs = 3L
        val testRecordCount = 1000

        // Setup test data
        val records = (1..testRecordCount).map { index ->
            OfflineAttendance(
                id = "regression-$index",
                employeeId = "EMP001",
                latitude = 40.7128,
                longitude = -74.0060,
                scanType = "face",
                attendanceType = "check_in",
                timestamp = System.currentTimeMillis() + index,
                synced = false
            )
        }

        // Test insert performance
        val insertTime = measureTimeMillis {
            records.forEach { record ->
                offlineAttendanceDao.insertOfflineAttendance(record)
            }
        }
        val avgInsertTime = insertTime / testRecordCount

        // Test query performance
        val queryTime = measureTimeMillis {
            repeat(100) {
                offlineAttendanceDao.getUnsyncedAttendance()
                offlineAttendanceDao.getUnsyncedCount()
            }
        }
        val avgQueryTime = queryTime / 100

        // Test update performance
        val updateTime = measureTimeMillis {
            records.forEach { record ->
                offlineAttendanceDao.markAsSynced(record.id)
            }
        }
        val avgUpdateTime = updateTime / testRecordCount

        // Then - Check against baselines
        assertTrue(
            "Insert performance regression: ${avgInsertTime}ms > ${baselineInsertMs}ms",
            avgInsertTime <= baselineInsertMs
        )
        
        assertTrue(
            "Query performance regression: ${avgQueryTime}ms > ${baselineQueryMs}ms",
            avgQueryTime <= baselineQueryMs
        )
        
        assertTrue(
            "Update performance regression: ${avgUpdateTime}ms > ${baselineUpdateMs}ms",
            avgUpdateTime <= baselineUpdateMs
        )

        println("Performance regression test passed:")
        println("  Insert: ${avgInsertTime}ms (baseline: ${baselineInsertMs}ms)")
        println("  Query: ${avgQueryTime}ms (baseline: ${baselineQueryMs}ms)")
        println("  Update: ${avgUpdateTime}ms (baseline: ${baselineUpdateMs}ms)")
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