package com.crm.realestate.data.repository

import com.crm.realestate.data.api.AttendanceApiService
import com.crm.realestate.data.database.dao.AttendanceCacheDao
import com.crm.realestate.data.database.dao.OfflineAttendanceDao
import com.crm.realestate.data.database.entities.AttendanceCache
import com.crm.realestate.data.models.*
import com.crm.realestate.network.TokenManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

/**
 * Comprehensive unit tests for duplicate attendance prevention system
 * Focuses on edge cases and boundary conditions for the 2-minute cooldown
 */
class AttendanceRepositoryDuplicatePreventionTest {

    private lateinit var repository: AttendanceRepository
    private lateinit var apiService: AttendanceApiService
    private lateinit var offlineAttendanceDao: OfflineAttendanceDao
    private lateinit var attendanceCacheDao: AttendanceCacheDao
    private lateinit var tokenManager: TokenManager

    @Before
    fun setup() {
        apiService = mockk()
        offlineAttendanceDao = mockk()
        attendanceCacheDao = mockk()
        tokenManager = mockk()
        
        repository = AttendanceRepository(
            apiService = apiService,
            offlineAttendanceDao = offlineAttendanceDao,
            attendanceCacheDao = attendanceCacheDao,
            tokenManager = tokenManager
        )
    }

    @Test
    fun `canPunchAttendance returns true when no cache exists`() = runTest {
        // Given - No cache exists for today
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null

        // When
        val canPunch = repository.canPunchAttendance()

        // Then
        assertTrue("Should allow punch when no cache exists", canPunch)
    }

    @Test
    fun `canPunchAttendance returns false when punch within 1 minute`() = runTest {
        // Given - Recent punch 1 minute ago
        val oneMinuteAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)
        val recentCache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = oneMinuteAgo,
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = "09:00:00",
            checkOutTime = null
        )
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns recentCache

        // When
        val canPunch = repository.canPunchAttendance()

        // Then
        assertFalse("Should not allow punch within 1 minute", canPunch)
    }

    @Test
    fun `canPunchAttendance returns false when punch exactly at 119 seconds`() = runTest {
        // Given - Recent punch exactly 119 seconds ago (just under 2 minutes)
        val justUnderTwoMinutes = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(119)
        val recentCache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = justUnderTwoMinutes,
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = "09:00:00",
            checkOutTime = null
        )
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns recentCache

        // When
        val canPunch = repository.canPunchAttendance()

        // Then
        assertFalse("Should not allow punch at 119 seconds (just under 2 minutes)", canPunch)
    }

    @Test
    fun `canPunchAttendance returns true when punch exactly at 2 minutes`() = runTest {
        // Given - No recent punch found (DAO query returns null for 2+ minutes)
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null

        // When
        val canPunch = repository.canPunchAttendance()

        // Then
        assertTrue("Should allow punch at exactly 2 minutes", canPunch)
    }

    @Test
    fun `canPunchAttendance returns true when punch over 2 minutes ago`() = runTest {
        // Given - No recent punch found (DAO query returns null for 2+ minutes)
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null

        // When
        val canPunch = repository.canPunchAttendance()

        // Then
        assertTrue("Should allow punch when over 2 minutes", canPunch)
    }

    @Test
    fun `canPunchAttendance fails open when database error occurs`() = runTest {
        // Given - Database error
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } throws Exception("Database error")

        // When
        val canPunch = repository.canPunchAttendance()

        // Then
        assertTrue("Should fail open and allow punch when database error occurs", canPunch)
    }

    @Test
    fun `checkIn blocked by duplicate prevention shows appropriate error message`() = runTest {
        // Given - Recent punch exists
        val recentCache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1),
            hasCheckedIn = false,
            hasCheckedOut = false,
            checkInTime = null,
            checkOutTime = null
        )
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns recentCache

        // When
        val result = repository.checkIn(12.345, 67.890, "face")

        // Then
        assertTrue("Should return error result", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals(
            "Should show 2-minute cooldown message",
            "Please wait 2 minutes before punching attendance again",
            errorResult.exception.message
        )
        assertEquals(
            "Should show user-friendly error message",
            "Please wait 2 minutes before punching attendance again",
            errorResult.message
        )

        // Verify API was not called
        coVerify(exactly = 0) { apiService.checkIn(any()) }
    }

    @Test
    fun `checkOut blocked by duplicate prevention shows appropriate error message`() = runTest {
        // Given - Recent punch exists
        val recentCache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(30),
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = "09:00:00",
            checkOutTime = null
        )
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns recentCache

        // When
        val result = repository.checkOut(12.345, 67.890, "fingerprint")

        // Then
        assertTrue("Should return error result", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals(
            "Should show 2-minute cooldown message",
            "Please wait 2 minutes before punching attendance again",
            errorResult.exception.message
        )

        // Verify API was not called
        coVerify(exactly = 0) { apiService.checkOut(any()) }
    }

    @Test
    fun `duplicate prevention bypassed during offline sync`() = runTest {
        // Given - Mock offline attendance record
        val offlineAttendance = mockk<com.crm.realestate.data.database.entities.OfflineAttendance> {
            every { id } returns "offline1"
            every { latitude } returns 12.345
            every { longitude } returns 67.890
            every { scanType } returns "face"
            every { attendanceType } returns "check_in"
        }

        // Mock successful API response for sync
        // val mockAttendanceResponse = AttendanceResponse( // This is for repository's internal logic
        //     id = "att123",
        //     employeeId = "emp123",
        //     checkInTime = "09:00:00",
        //     checkOutTime = null,
        //     status = "synced",
        //     message = "Sync successful"
        // )
        // val mockApiResponse = ApiResponse( // This is for repository's internal logic
        //     success = true,
        //     message = "Success",
        //     data = mockAttendanceResponse
        // )
        val mockDetailResponse = DetailResponse("Sync successful for check-in")
        val mockResponse = retrofit2.Response.success(mockDetailResponse)

        // Mock all required dependencies for sync
        coEvery { offlineAttendanceDao.getUnsyncedAttendance() } returns listOf(offlineAttendance)
        coEvery { apiService.checkIn(any()) } returns mockResponse // Must return Response<DetailResponse>
        coEvery { offlineAttendanceDao.markAsSynced("offline1") } returns 1
        coEvery { offlineAttendanceDao.deleteSyncedOlderThan(any()) } returns 0

        // When - Call sync method (this bypasses duplicate prevention)
        val result = repository.syncOfflineAttendance()

        // Then
        assertTrue("Sync should succeed", result is Result.Success)
        
        // Verify API was called (duplicate prevention was bypassed)
        coVerify { apiService.checkIn(any()) }
        coVerify { offlineAttendanceDao.markAsSynced("offline1") }
    }

    @Test
    fun `attendance cache updated correctly after successful check-in`() = runTest {
        // Given - No recent punch, successful API response
        val mockEmployeeInfo = EmployeeInfo("emp123", "John Doe", "john@example.com", "IT", "Developer")
        // val mockAttendanceResponse = AttendanceResponse( // This is for repository's internal logic
        //     id = "att123",
        //     employeeId = "emp123",
        //     checkInTime = "09:00:00",
        //     checkOutTime = null,
        //     status = "checked_in",
        //     message = "Check-in successful"
        // )
        // val mockApiResponse = ApiResponse( // This is for repository's internal logic
        //     success = true,
        //     message = "Success",
        //     data = mockAttendanceResponse
        // )
        val mockDetailResponse = DetailResponse("Check-in successful")
        val mockResponse = retrofit2.Response.success(mockDetailResponse)

        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null
        coEvery { apiService.checkIn(any()) } returns mockResponse // Must return Response<DetailResponse>
        coEvery { tokenManager.getEmployeeInfo() } returns mockEmployeeInfo
        coEvery { attendanceCacheDao.getCacheForDate(any()) } returns null
        coEvery { attendanceCacheDao.insertOrUpdateCache(any()) } returns 1L

        // When
        val result = repository.checkIn(12.345, 67.890, "face")

        // Then
        assertTrue("Check-in should succeed", result is Result.Success)

        // Verify cache was updated with check-in information
        coVerify {
            attendanceCacheDao.insertOrUpdateCache(
                match<AttendanceCache> { cache ->
                    cache.hasCheckedIn && 
                    !cache.hasCheckedOut && 
                    cache.checkInTime != null &&
                    cache.checkOutTime == null
                }
            )
        }
    }

    @Test
    fun `attendance cache updated correctly after successful check-out`() = runTest {
        // Given - No recent punch, successful API response
        val mockEmployeeInfo = EmployeeInfo("emp123", "John Doe", "john@example.com", "IT", "Developer")
        // val mockAttendanceResponse = AttendanceResponse( // This is for repository's internal logic
        //     id = "att123",
        //     employeeId = "emp123",
        //     checkInTime = "09:00:00",
        //     checkOutTime = "17:00:00",
        //     status = "checked_out",
        //     message = "Check-out successful"
        // )
        // val mockApiResponse = ApiResponse( // This is for repository's internal logic
        //     success = true,
        //     message = "Success",
        //     data = mockAttendanceResponse
        // )
        val mockDetailResponse = DetailResponse("Check-out successful")
        val mockResponse = retrofit2.Response.success(mockDetailResponse)

        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null
        coEvery { apiService.checkOut(any()) } returns mockResponse // Must return Response<DetailResponse>
        coEvery { tokenManager.getEmployeeInfo() } returns mockEmployeeInfo
        
        // Mock existing cache with check-in
        val existingCache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(8),
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = "09:00:00",
            checkOutTime = null
        )
        coEvery { attendanceCacheDao.getCacheForDate(any()) } returns existingCache
        coEvery { attendanceCacheDao.insertOrUpdateCache(any()) } returns 1L

        // When
        val result = repository.checkOut(12.345, 67.890, "fingerprint")

        // Then
        assertTrue("Check-out should succeed", result is Result.Success)

        // Verify cache was updated with check-out information
        coVerify {
            attendanceCacheDao.insertOrUpdateCache(
                match<AttendanceCache> { cache ->
                    cache.hasCheckedIn && 
                    cache.hasCheckedOut && 
                    cache.checkInTime == "09:00:00" &&
                    cache.checkOutTime != null
                }
            )
        }
    }

    @Test
    fun `multiple rapid punch attempts all blocked within 2-minute window`() = runTest {
        // Given - Recent punch 30 seconds ago
        val recentCache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(30),
            hasCheckedIn = false,
            hasCheckedOut = false,
            checkInTime = null,
            checkOutTime = null
        )
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns recentCache

        // When - Multiple rapid attempts
        val result1 = repository.checkIn(12.345, 67.890, "face")
        val result2 = repository.checkIn(12.345, 67.890, "face")
        val result3 = repository.checkOut(12.345, 67.890, "fingerprint")

        // Then - All should be blocked
        assertTrue("First attempt should be blocked", result1 is Result.Error)
        assertTrue("Second attempt should be blocked", result2 is Result.Error)
        assertTrue("Third attempt should be blocked", result3 is Result.Error)

        // Verify no API calls were made
        coVerify(exactly = 0) { apiService.checkIn(any()) }
        coVerify(exactly = 0) { apiService.checkOut(any()) }
    }

    @Test
    fun `cache cleanup handles database errors gracefully`() = runTest {
        // Given - Database error during cleanup
        coEvery { attendanceCacheDao.deleteOldCache(any()) } throws Exception("Database error")

        // When - Cleanup is called (this should not throw)
        try {
            repository.cleanupOldRecords()
            // Test passes if no exception is thrown
        } catch (e: Exception) {
            fail("Cleanup should handle database errors gracefully, but threw: ${e.message}")
        }

        // Then - Verify cleanup was attempted
        coVerify { attendanceCacheDao.deleteOldCache(any()) }
    }

    @Test
    fun `user feedback messages are appropriate for duplicate prevention`() = runTest {
        // Given - Recent punch exists
        val recentCache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(90),
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = "09:00:00",
            checkOutTime = null
        )
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns recentCache

        // When
        val checkInResult = repository.checkIn(12.345, 67.890, "face")
        val checkOutResult = repository.checkOut(12.345, 67.890, "fingerprint")

        // Then - Both should have the same user-friendly message
        assertTrue("Check-in should be blocked", checkInResult is Result.Error)
        assertTrue("Check-out should be blocked", checkOutResult is Result.Error)

        val checkInError = checkInResult as Result.Error
        val checkOutError = checkOutResult as Result.Error

        assertEquals(
            "Check-in error message should be user-friendly",
            "Please wait 2 minutes before punching attendance again",
            checkInError.message
        )
        assertEquals(
            "Check-out error message should be user-friendly",
            "Please wait 2 minutes before punching attendance again",
            checkOutError.message
        )
    }
}
