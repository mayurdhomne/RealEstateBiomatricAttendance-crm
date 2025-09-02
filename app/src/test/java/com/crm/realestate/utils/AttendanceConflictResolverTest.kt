package com.crm.realestate.utils

import com.crm.realestate.data.database.entities.OfflineAttendance
import com.crm.realestate.data.models.AttendanceResponse
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

/**
 * Unit tests for AttendanceConflictResolver
 * Tests conflict resolution logic, deduplication, and validation
 */
class AttendanceConflictResolverTest {

    private lateinit var conflictResolver: AttendanceConflictResolver

    private val baseOfflineAttendance = OfflineAttendance(
        id = "offline-1",
        employeeId = "EMP001",
        latitude = 40.7128,
        longitude = -74.0060,
        scanType = "face",
        attendanceType = "check_in",
        timestamp = System.currentTimeMillis(),
        synced = false
    )

    private val baseAttendanceResponse = AttendanceResponse(
        id = "server-1",
        employeeId = "EMP001",
        checkInTime = "09:00:00",
        checkOutTime = null,
        status = "checked_in",
        message = "Check-in successful"
    )

    @Before
    fun setup() {
        conflictResolver = AttendanceConflictResolver()
    }

    @Test
    fun `deduplicateOfflineRecords removes exact duplicates`() {
        // Given
        val duplicate1 = baseOfflineAttendance.copy(id = "1")
        val duplicate2 = baseOfflineAttendance.copy(id = "2") // Same data, different ID
        val unique = baseOfflineAttendance.copy(
            id = "3",
            attendanceType = "check_out",
            timestamp = System.currentTimeMillis() + 1000
        )
        val records = listOf(duplicate1, duplicate2, unique)

        // When
        val result = conflictResolver.deduplicateOfflineRecords(records)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.contains(duplicate1)) // First occurrence kept
        assertFalse(result.contains(duplicate2)) // Duplicate removed
        assertTrue(result.contains(unique))
    }

    @Test
    fun `deduplicateOfflineRecords keeps records with different timestamps`() {
        // Given
        val record1 = baseOfflineAttendance.copy(id = "1", timestamp = 1000L)
        val record2 = baseOfflineAttendance.copy(id = "2", timestamp = 2000L)
        val records = listOf(record1, record2)

        // When
        val result = conflictResolver.deduplicateOfflineRecords(records)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.contains(record1))
        assertTrue(result.contains(record2))
    }

    @Test
    fun `deduplicateOfflineRecords keeps records with different attendance types`() {
        // Given
        val checkIn = baseOfflineAttendance.copy(id = "1", attendanceType = "check_in")
        val checkOut = baseOfflineAttendance.copy(id = "2", attendanceType = "check_out")
        val records = listOf(checkIn, checkOut)

        // When
        val result = conflictResolver.deduplicateOfflineRecords(records)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.contains(checkIn))
        assertTrue(result.contains(checkOut))
    }

    @Test
    fun `deduplicateOfflineRecords removes duplicates within time window`() {
        // Given
        val baseTime = System.currentTimeMillis()
        val record1 = baseOfflineAttendance.copy(id = "1", timestamp = baseTime)
        val record2 = baseOfflineAttendance.copy(id = "2", timestamp = baseTime + 30000) // 30 seconds later
        val record3 = baseOfflineAttendance.copy(id = "3", timestamp = baseTime + 300000) // 5 minutes later
        val records = listOf(record1, record2, record3)

        // When
        val result = conflictResolver.deduplicateOfflineRecords(records)

        // Then
        assertEquals(2, result.size) // record2 should be removed as duplicate within 2-minute window
        assertTrue(result.contains(record1))
        assertFalse(result.contains(record2))
        assertTrue(result.contains(record3))
    }

    @Test
    fun `validateOfflineAttendance returns valid for normal record`() {
        // Given
        val validRecord = baseOfflineAttendance

        // When
        val result = conflictResolver.validateOfflineAttendance(validRecord)

        // Then
        assertTrue(result is AttendanceConflictResolver.ValidationResult.Valid)
    }

    @Test
    fun `validateOfflineAttendance returns invalid for old record`() {
        // Given
        val oldRecord = baseOfflineAttendance.copy(
            timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8) // 8 days old
        )

        // When
        val result = conflictResolver.validateOfflineAttendance(oldRecord)

        // Then
        assertTrue(result is AttendanceConflictResolver.ValidationResult.Invalid)
        assertEquals("Record is too old (older than 7 days)", (result as AttendanceConflictResolver.ValidationResult.Invalid).reason)
    }

    @Test
    fun `validateOfflineAttendance returns invalid for future record`() {
        // Given
        val futureRecord = baseOfflineAttendance.copy(
            timestamp = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2) // 2 hours in future
        )

        // When
        val result = conflictResolver.validateOfflineAttendance(futureRecord)

        // Then
        assertTrue(result is AttendanceConflictResolver.ValidationResult.Invalid)
        assertEquals("Record timestamp is in the future", (result as AttendanceConflictResolver.ValidationResult.Invalid).reason)
    }

    @Test
    fun `validateOfflineAttendance returns invalid for invalid coordinates`() {
        // Given
        val invalidLatRecord = baseOfflineAttendance.copy(latitude = 91.0) // Invalid latitude
        val invalidLonRecord = baseOfflineAttendance.copy(longitude = 181.0) // Invalid longitude

        // When
        val latResult = conflictResolver.validateOfflineAttendance(invalidLatRecord)
        val lonResult = conflictResolver.validateOfflineAttendance(invalidLonRecord)

        // Then
        assertTrue(latResult is AttendanceConflictResolver.ValidationResult.Invalid)
        assertEquals("Invalid coordinates", (latResult as AttendanceConflictResolver.ValidationResult.Invalid).reason)
        
        assertTrue(lonResult is AttendanceConflictResolver.ValidationResult.Invalid)
        assertEquals("Invalid coordinates", (lonResult as AttendanceConflictResolver.ValidationResult.Invalid).reason)
    }

    @Test
    fun `validateOfflineAttendance returns invalid for blank employee ID`() {
        // Given
        val blankEmployeeRecord = baseOfflineAttendance.copy(employeeId = "")

        // When
        val result = conflictResolver.validateOfflineAttendance(blankEmployeeRecord)

        // Then
        assertTrue(result is AttendanceConflictResolver.ValidationResult.Invalid)
        assertEquals("Employee ID is blank", (result as AttendanceConflictResolver.ValidationResult.Invalid).reason)
    }

    @Test
    fun `validateOfflineAttendance returns invalid for invalid scan type`() {
        // Given
        val invalidScanRecord = baseOfflineAttendance.copy(scanType = "invalid")

        // When
        val result = conflictResolver.validateOfflineAttendance(invalidScanRecord)

        // Then
        assertTrue(result is AttendanceConflictResolver.ValidationResult.Invalid)
        assertEquals("Invalid scan type", (result as AttendanceConflictResolver.ValidationResult.Invalid).reason)
    }

    @Test
    fun `validateOfflineAttendance returns invalid for invalid attendance type`() {
        // Given
        val invalidTypeRecord = baseOfflineAttendance.copy(attendanceType = "invalid")

        // When
        val result = conflictResolver.validateOfflineAttendance(invalidTypeRecord)

        // Then
        assertTrue(result is AttendanceConflictResolver.ValidationResult.Invalid)
        assertEquals("Invalid attendance type", (result as AttendanceConflictResolver.ValidationResult.Invalid).reason)
    }

    @Test
    fun `resolveConflict returns no conflict for matching data`() {
        // Given
        val offlineRecord = baseOfflineAttendance.copy(attendanceType = "check_in")
        val serverResponse = baseAttendanceResponse.copy(status = "checked_in")

        // When
        val result = conflictResolver.resolveConflict(offlineRecord, serverResponse)

        // Then
        assertEquals(AttendanceConflictResolver.ConflictResolution.NO_CONFLICT, result)
    }

    @Test
    fun `resolveConflict returns prefer server for status mismatch`() {
        // Given
        val offlineRecord = baseOfflineAttendance.copy(attendanceType = "check_in")
        val serverResponse = baseAttendanceResponse.copy(status = "checked_out")

        // When
        val result = conflictResolver.resolveConflict(offlineRecord, serverResponse)

        // Then
        assertEquals(AttendanceConflictResolver.ConflictResolution.PREFER_SERVER, result)
    }

    @Test
    fun `resolveConflict returns merge data for employee ID mismatch`() {
        // Given
        val offlineRecord = baseOfflineAttendance.copy(employeeId = "EMP001")
        val serverResponse = baseAttendanceResponse.copy(employeeId = "EMP002")

        // When
        val result = conflictResolver.resolveConflict(offlineRecord, serverResponse)

        // Then
        assertEquals(AttendanceConflictResolver.ConflictResolution.MERGE_DATA, result)
    }

    @Test
    fun `resolveConflict returns prefer offline for recent offline data`() {
        // Given
        val recentTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5) // 5 minutes ago
        val offlineRecord = baseOfflineAttendance.copy(timestamp = recentTime)
        val serverResponse = baseAttendanceResponse.copy(status = "different_status")

        // When
        val result = conflictResolver.resolveConflict(offlineRecord, serverResponse)

        // Then
        assertEquals(AttendanceConflictResolver.ConflictResolution.PREFER_OFFLINE, result)
    }

    @Test
    fun `mergeAttendanceData combines offline and server data correctly`() {
        // Given
        val offlineRecord = baseOfflineAttendance.copy(
            employeeId = "EMP001",
            scanType = "face",
            attendanceType = "check_in"
        )
        val serverResponse = baseAttendanceResponse.copy(
            id = "server-123",
            status = "checked_in",
            message = "Server message"
        )

        // When
        val result = conflictResolver.mergeAttendanceData(offlineRecord, serverResponse)

        // Then
        assertEquals("server-123", result.id)
        assertEquals("EMP001", result.employeeId)
        assertEquals("checked_in", result.status)
        assertTrue(result.message.contains("merged"))
        assertTrue(result.message.contains("face scan"))
    }

    @Test
    fun `mergeAttendanceData handles null server data gracefully`() {
        // Given
        val offlineRecord = baseOfflineAttendance
        val serverResponse = baseAttendanceResponse.copy(
            checkInTime = null,
            checkOutTime = null,
            message = ""
        )

        // When
        val result = conflictResolver.mergeAttendanceData(offlineRecord, serverResponse)

        // Then
        assertNotNull(result.message)
        assertTrue(result.message.contains("merged"))
    }

    @Test
    fun `deduplicateOfflineRecords handles empty list`() {
        // Given
        val emptyList = emptyList<OfflineAttendance>()

        // When
        val result = conflictResolver.deduplicateOfflineRecords(emptyList)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deduplicateOfflineRecords handles single record`() {
        // Given
        val singleRecord = listOf(baseOfflineAttendance)

        // When
        val result = conflictResolver.deduplicateOfflineRecords(singleRecord)

        // Then
        assertEquals(1, result.size)
        assertEquals(baseOfflineAttendance, result.first())
    }

    @Test
    fun `validateOfflineAttendance accepts valid scan types`() {
        val validScanTypes = listOf("face", "fingerprint", "manual")
        
        validScanTypes.forEach { scanType ->
            // Given
            val record = baseOfflineAttendance.copy(scanType = scanType)

            // When
            val result = conflictResolver.validateOfflineAttendance(record)

            // Then
            assertTrue("Failed for scan type: $scanType", result is AttendanceConflictResolver.ValidationResult.Valid)
        }
    }

    @Test
    fun `validateOfflineAttendance accepts valid attendance types`() {
        val validAttendanceTypes = listOf("check_in", "check_out")
        
        validAttendanceTypes.forEach { attendanceType ->
            // Given
            val record = baseOfflineAttendance.copy(attendanceType = attendanceType)

            // When
            val result = conflictResolver.validateOfflineAttendance(record)

            // Then
            assertTrue("Failed for attendance type: $attendanceType", result is AttendanceConflictResolver.ValidationResult.Valid)
        }
    }

    @Test
    fun `resolveConflict handles edge cases correctly`() {
        // Test with null values in server response
        val offlineRecord = baseOfflineAttendance
        val serverResponseWithNulls = AttendanceResponse(
            id = "server-1",
            employeeId = "EMP001",
            checkInTime = null,
            checkOutTime = null,
            status = "unknown",
            message = ""
        )

        // When
        val result = conflictResolver.resolveConflict(offlineRecord, serverResponseWithNulls)

        // Then
        // Should not throw exception and return a valid resolution
        assertNotNull(result)
        assertTrue(result in AttendanceConflictResolver.ConflictResolution.values())
    }
}