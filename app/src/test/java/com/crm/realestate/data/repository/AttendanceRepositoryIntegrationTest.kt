package com.crm.realestate.data.repository

import com.crm.realestate.data.api.AttendanceApiService
import com.crm.realestate.data.database.dao.AttendanceCacheDao
import com.crm.realestate.data.database.dao.OfflineAttendanceDao
import com.crm.realestate.data.models.*
import com.crm.realestate.network.TokenManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Response

/**
 * Integration test for AttendanceRepository focusing on API integration
 * and comprehensive error handling
 */
class AttendanceRepositoryIntegrationTest {

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
    fun `checkIn with successful API response returns success with user feedback`() = runTest {
        // Arrange
        val latitude = 12.345
        val longitude = 67.890
        val scanType = "face"
        
        val mockDetailResponse = DetailResponse(
            detail = "Check-in successful"
        )
        val mockResponse = Response.success(mockDetailResponse)
        
        // Mock all dependencies to allow check-in
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null
        coEvery { attendanceCacheDao.getCacheForDate(any()) } returns null
        coEvery { apiService.checkIn(any()) } returns mockResponse
        coEvery { attendanceCacheDao.insertOrUpdateCache(any()) } returns 1L
        
        // Act
        val result = repository.checkIn(latitude, longitude, scanType)
        
        // Assert
        assertTrue("Result should be success", result is Result.Success)
        val successResult = result as Result.Success<AttendanceResponse>
        assertEquals("Check-in successful! Have a great day at work.", successResult.data.message)
        
        // Verify API was called with correct parameters
        coVerify {
            apiService.checkIn(
                CheckInRequest(
                    checkInLatitude = latitude,
                    checkInLongitude = longitude,
                    scanType = scanType
                )
            )
        }
    }

    @Test
    fun `checkOut with successful API response returns success with user feedback`() = runTest {
        // Arrange
        val latitude = 12.345
        val longitude = 67.890
        val scanType = "fingerprint"
        
        val mockDetailResponse = DetailResponse(
            detail = "Check-out successful"
        )
        val mockResponse = Response.success(mockDetailResponse)
        
        // Mock dependencies to simulate already checked in
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null
        // Mock existing cache to indicate user has already checked in
        coEvery { attendanceCacheDao.getCacheForDate(any()) } returns com.crm.realestate.data.database.entities.AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = System.currentTimeMillis() - 3600000, // 1 hour ago
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = "09:00:00",
            checkOutTime = null
        )
        coEvery { apiService.checkOut(any()) } returns mockResponse
        coEvery { attendanceCacheDao.insertOrUpdateCache(any()) } returns 1L
        
        // Act
        val result = repository.checkOut(latitude, longitude, scanType)
        
        // Assert
        assertTrue("Result should be success", result is Result.Success)
        val successResult = result as Result.Success<AttendanceResponse>
        assertEquals("Check-out successful! Have a great evening.", successResult.data.message)
        
        // Verify API was called with correct parameters
        coVerify {
            apiService.checkOut(
                CheckOutRequest(
                    checkOutLatitude = latitude,
                    checkOutLongitude = longitude,
                    scanType = scanType
                )
            )
        }
    }

    @Test
    fun `checkIn with 401 error returns authentication error`() = runTest {
        // Arrange
        val latitude = 12.345
        val longitude = 67.890
        val scanType = "face"
        
        // Mock dependencies to allow check-in attempt
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null
        coEvery { attendanceCacheDao.getCacheForDate(any()) } returns null
        
        val unauthorizedResponse = Response.error<DetailResponse>(
            401, 
            okhttp3.ResponseBody.create(null, "Unauthorized")
        )
        coEvery { apiService.checkIn(any()) } returns unauthorizedResponse
        
        // Act
        val result = repository.checkIn(latitude, longitude, scanType)
        
        // Assert
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Authentication failed. Please login again.", errorResult.message)
    }

    @Test
    fun `canPunchAttendance returns false when recent punch exists`() = runTest {
        // Arrange - mock recent punch within 2 minutes
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns mockk()
        
        // Act
        val canPunch = repository.canPunchAttendance()
        
        // Assert
        assertFalse("Should not allow punch when recent punch exists", canPunch)
    }

    @Test
    fun `canPunchAttendance returns true when no recent punch exists`() = runTest {
        // Arrange
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null
        
        // Act
        val canPunch = repository.canPunchAttendance()
        
        // Assert
        assertTrue("Should allow punch when no recent punch exists", canPunch)
    }

    @Test
    fun `getUnsyncedAttendanceCount returns correct count`() = runTest {
        // Arrange
        coEvery { offlineAttendanceDao.getUnsyncedCount() } returns 3
        
        // Act
        val count = repository.getUnsyncedAttendanceCount()
        
        // Assert
        assertEquals("Should return correct unsynced count", 3, count)
    }

    @Test
    fun `saveOfflineAttendance stores attendance when network fails`() = runTest {
        // Arrange
        val latitude = 12.345
        val longitude = 67.890
        val scanType = "face"
        val attendanceType = "check_in"
        
        val mockEmployeeInfo = EmployeeInfo("emp123", "John Doe", "john@example.com", "IT", "Developer")
        coEvery { tokenManager.getEmployeeInfo() } returns mockEmployeeInfo
        coEvery { offlineAttendanceDao.insertOfflineAttendance(any()) } returns 1L
        
        // Act
        repository.saveOfflineAttendance(latitude, longitude, scanType, attendanceType)
        
        // Assert
        coVerify { offlineAttendanceDao.insertOfflineAttendance(any()) }
    }
}