package com.crm.realestate.offline

import com.crm.realestate.data.database.entities.OfflineAttendance
import com.crm.realestate.data.models.AttendanceResponse
import com.crm.realestate.utils.AttendanceConflictResolver
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for AttendanceConflictResolver
 */
class AttendanceConflictResolverTest {
    
    private lateinit var conflictResolver: AttendanceConflictResolver
    
    @Before
    fun setup() {
        conflictResolver = AttendanceConflictResolver()
    }
    
    @Test
    fun `test validate valid offline attendance`() {
        // Given
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
        
        // When
        val result = conflictResolver.validateOfflineAttendance(validAttendance)
        
        // Then
        assertTrue(result is AttendanceConflictResolver.ValidationResult.Valid)
    }
    
    @Test
    fun `test validate invalid offline attendance with missing employee ID`() {
        // Given
        val invalidAttendance = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = System.currentTimeMillis(),
            synced = false
        )
        
        // When
        val result = conflictResolver.validateOfflineAttendance(invalidAttendance)
        
        // Then
        assertTrue(result is AttendanceConflictResolver.ValidationResult.Invalid)
        if (result is AttendanceConflictResolver.ValidationResult.Invalid) {
            assertTrue(result.errors.any { it.contains("Employee ID") })
        }
    }
    
    @Test
    fun `test validate invalid offline attendance with invalid coordinates`() {
        // Given
        val invalidAttendance = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 0.0,
            longitude = 0.0,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = System.currentTimeMillis(),
            synced = false
        )
        
        // When
        val result = conflictResolver.validateOfflineAttendance(invalidAttendance)
        
        // Then
        assertTrue(result is AttendanceConflictResolver.ValidationResult.Invalid)
        if (result is AttendanceConflictResolver.ValidationResult.Invalid) {
            assertTrue(result.errors.any { it.contains("coordinates") })
        }
    }
    
    @Test
    fun `test validate invalid offline attendance with invalid scan type`() {
        // Given
        val invalidAttendance = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "invalid_scan",
            attendanceType = "check_in",
            timestamp = System.currentTimeMillis(),
            synced = false
        )
        
        // When
        val result = conflictResolver.validateOfflineAttendance(invalidAttendance)
        
        // Then
        assertTrue(result is AttendanceConflictResolver.ValidationResult.Invalid)
        if (result is AttendanceConflictResolver.ValidationResult.Invalid) {
            assertTrue(result.errors.any { it.contains("scan type") })
        }
    }
    
    @Test
    fun `test validate invalid offline attendance with future timestamp`() {
        // Given
        val futureTimestamp = System.currentTimeMillis() + (24 * 60 * 60 * 1000L) // 1 day in future
        val invalidAttendance = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = futureTimestamp,
            synced = false
        )
        
        // When
        val result = conflictResolver.validateOfflineAttendance(invalidAttendance)
        
        // Then
        assertTrue(result is AttendanceConflictResolver.ValidationResult.Invalid)
        if (result is AttendanceConflictResolver.ValidationResult.Invalid) {
            assertTrue(result.errors.any { it.contains("future") })
        }
    }
    
    @Test
    fun `test is same logical operation returns true for same day and type`() {
        // Given
        val baseTimestamp = System.currentTimeMillis()
        val attendance1 = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = baseTimestamp,
            synced = false
        )
        
        val attendance2 = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "fingerprint", // Different scan type
            attendanceType = "check_in", // Same attendance type
            timestamp = baseTimestamp + 30000, // 30 seconds later, same day
            synced = false
        )
        
        // When
        val result = conflictResolver.isSameLogicalOperation(attendance1, attendance2)
        
        // Then
        assertTrue(result)
    }
    
    @Test
    fun `test is same logical operation returns false for different types`() {
        // Given
        val baseTimestamp = System.currentTimeMillis()
        val attendance1 = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = baseTimestamp,
            synced = false
        )
        
        val attendance2 = OfflineAttendance(
            id = UUID.randomUUID().toString(),
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_out", // Different attendance type
            timestamp = baseTimestamp + 30000,
            synced = false
        )
        
        // When
        val result = conflictResolver.isSameLogicalOperation(attendance1, attendance2)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `test deduplicate offline records keeps newest`() {
        // Given
        val baseTimestamp = System.currentTimeMillis()
        val olderAttendance = OfflineAttendance(
            id = "older",
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = baseTimestamp,
            synced = false
        )
        
        val newerAttendance = OfflineAttendance(
            id = "newer",
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "fingerprint",
            attendanceType = "check_in",
            timestamp = baseTimestamp + 60000, // 1 minute later
            synced = false
        )
        
        val records = listOf(olderAttendance, newerAttendance)
        
        // When
        val deduplicatedRecords = conflictResolver.deduplicateOfflineRecords(records)
        
        // Then
        assertEquals(1, deduplicatedRecords.size)
        assertEquals("newer", deduplicatedRecords[0].id)
    }
    
    @Test
    fun `test deduplicate offline records preserves different operations`() {
        // Given
        val baseTimestamp = System.currentTimeMillis()
        val checkInAttendance = OfflineAttendance(
            id = "checkin",
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_in",
            timestamp = baseTimestamp,
            synced = false
        )
        
        val checkOutAttendance = OfflineAttendance(
            id = "checkout",
            employeeId = "EMP001",
            latitude = 40.7128,
            longitude = -74.0060,
            scanType = "face",
            attendanceType = "check_out",
            timestamp = baseTimestamp + 60000,
            synced = false
        )
        
        val records = listOf(checkInAttendance, checkOutAttendance)
        
        // When
        val deduplicatedRecords = conflictResolver.deduplicateOfflineRecords(records)
        
        // Then
        assertEquals(2, deduplicatedRecords.size)
        assertTrue(deduplicatedRecords.any { it.id == "checkin" })
        assertTrue(deduplicatedRecords.any { it.id == "checkout" })
    }
    
    @Test
    fun `test resolve conflict returns no conflict for different operations`() {
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
        
        val serverResponse = AttendanceResponse(
            id = "server_id",
            employeeId = "EMP001",
            checkInTime = null,
            checkOutTime = "17:00:00", // Different operation
            status = "success",
            message = "Check-out successful"
        )
        
        // When
        val resolution = conflictResolver.resolveConflict(offlineAttendance, serverResponse)
        
        // Then
        assertEquals(AttendanceConflictResolver.ConflictResolution.NO_CONFLICT, resolution)
    }
    
    @Test
    fun `test merge attendance data combines offline and server data`() {
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
        
        val serverResponse = AttendanceResponse(
            id = "server_id",
            employeeId = "EMP001",
            checkInTime = "09:00:00",
            checkOutTime = null,
            status = "success",
            message = "Check-in successful"
        )
        
        // When
        val mergedData = conflictResolver.mergeAttendanceData(offlineAttendance, serverResponse)
        
        // Then
        assertEquals(serverResponse.id, mergedData.id)
        assertEquals(serverResponse.employeeId, mergedData.employeeId)
        assertTrue(mergedData.message.contains("synced from offline"))
    }
}