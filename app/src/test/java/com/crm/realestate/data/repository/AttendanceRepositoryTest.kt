package com.crm.realestate.data.repository

import com.crm.realestate.data.api.AttendanceApiService
import com.crm.realestate.data.database.dao.AttendanceCacheDao
import com.crm.realestate.data.database.dao.OfflineAttendanceDao
import com.crm.realestate.data.database.entities.AttendanceCache
import com.crm.realestate.data.database.entities.OfflineAttendance
import com.crm.realestate.data.models.*
import com.crm.realestate.network.TokenManager
import com.crm.realestate.data.models.Result
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Response
import java.util.concurrent.TimeUnit
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class AttendanceRepositoryTest {

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
    fun `checkIn success returns success result with user feedback`() = runTest {
        // Arrange
        val latitude = 12.345
        val longitude = 67.890
        val scanType = "face"
        
        val mockEmployeeInfo = EmployeeInfo("emp123", "John Doe", "john@example.com", "IT", "Developer")
        // The repository crafts its own AttendanceResponse based on API success and other info
        // So the direct mock for apiService.checkIn should be Response<DetailResponse>
        val mockApiDetailResponse = DetailResponse("Check-in successful from API")
        val mockResponse = Response.success(mockApiDetailResponse)
        
        // Mock dependencies
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null
        coEvery { repository.getTodayAttendance() } returns Result.Success(
            TodayAttendance(false, false, null, null)
        )
        coEvery { apiService.checkIn(any()) } returns mockResponse
        coEvery { tokenManager.getEmployeeInfo() } returns mockEmployeeInfo
        coEvery { attendanceCacheDao.getCacheForDate(any()) } returns null
        coEvery { attendanceCacheDao.insertOrUpdateCache(any()) } returns 1L
        
        // Act
        val result = repository.checkIn(latitude, longitude, scanType)
        
        // Assert
        assertTrue(result is Result.Success)
        val successResult = result as Result.Success
        assertEquals("Check-in successful! Have a great day at work.", successResult.data.message)
        
        // Verify API call was made with correct parameters
        coVerify {
            apiService.checkIn(
                CheckInRequest(
                    checkInLatitude = latitude,
                    checkInLongitude = longitude,
                    scanType = scanType
                )
            )
        }
        
        // Verify cache was updated
        coVerify { attendanceCacheDao.insertOrUpdateCache(any()) }
    }

    @Test
    fun `checkIn with duplicate prevention returns error`() = runTest {
        // Arrange
        val latitude = 12.345
        val longitude = 67.890
        val scanType = "face"
        
        val recentCache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1), // 1 minute ago
            hasCheckedIn = false,
            hasCheckedOut = false,
            checkInTime = null,
            checkOutTime = null
        )
        
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns recentCache
        
        // Act
        val result = repository.checkIn(latitude, longitude, scanType)
        
        // Assert
        assertTrue(result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Please wait 2 minutes before punching attendance again", errorResult.exception.message)
        
        // Verify API was not called
        coVerify(exactly = 0) { apiService.checkIn(any()) }
    }

    @Test
    fun `checkIn with network failure saves offline attendance`() = runTest {
        // Arrange
        val latitude = 12.345
        val longitude = 67.890
        val scanType = "face"
        
        val mockEmployeeInfo = EmployeeInfo("emp123", "John Doe", "john@example.com", "IT", "Developer")
        
        // Mock dependencies
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null
        coEvery { repository.getTodayAttendance() } returns Result.Success(
            TodayAttendance(false, false, null, null)
        )
        coEvery { apiService.checkIn(any()) } throws Exception("Network error")
        coEvery { tokenManager.getEmployeeInfo() } returns mockEmployeeInfo
        coEvery { offlineAttendanceDao.insertOfflineAttendance(any()) } returns 1L
        
        // Act
        val result = repository.checkIn(latitude, longitude, scanType)
        
        // Assert
        assertTrue(result is Result.Error)
        val errorResult = result as Result.Error
        assertTrue(errorResult.exception.message!!.contains("Network unavailable"))
        
        // Verify offline attendance was saved
        coVerify { offlineAttendanceDao.insertOfflineAttendance(any()) }
    }

    @Test
    fun `checkOut success returns success result with user feedback`() = runTest {
        // Arrange
        val latitude = 12.345
        val longitude = 67.890
        val scanType = "fingerprint"
        
        val mockEmployeeInfo = EmployeeInfo("emp123", "John Doe", "john@example.com", "IT", "Developer")
        val mockApiDetailResponse = DetailResponse("Check-out successful from API")
        val mockResponse = Response.success(mockApiDetailResponse)
        
        // Mock dependencies
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null
        coEvery { repository.getTodayAttendance() } returns Result.Success(
            TodayAttendance(true, false, "09:00:00", null)
        )
        coEvery { apiService.checkOut(any()) } returns mockResponse
        coEvery { tokenManager.getEmployeeInfo() } returns mockEmployeeInfo
        coEvery { attendanceCacheDao.getCacheForDate(any()) } returns null
        coEvery { attendanceCacheDao.insertOrUpdateCache(any()) } returns 1L
        
        // Act
        val result = repository.checkOut(latitude, longitude, scanType)
        
        // Assert
        assertTrue(result is Result.Success)
        val successResult = result as Result.Success
        assertEquals("Check-out successful! Have a great evening.", successResult.data.message)
        
        // Verify API call was made with correct parameters
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
    fun `syncOfflineAttendance processes all unsynced records`() = runTest {
        // Arrange
        val unsyncedAttendance = listOf(
            OfflineAttendance(
                id = "offline1",
                employeeId = "emp123",
                latitude = 12.345,
                longitude = 67.890,
                scanType = "face",
                attendanceType = "check_in",
                timestamp = System.currentTimeMillis(),
                synced = false
            ),
            OfflineAttendance(
                id = "offline2",
                employeeId = "emp123",
                latitude = 12.345,
                longitude = 67.890,
                scanType = "fingerprint",
                attendanceType = "check_out",
                timestamp = System.currentTimeMillis(),
                synced = false
            )
        )
        
        val mockApiDetailResponse = DetailResponse("Synced successfully from API")
        val mockResponse = Response.success(mockApiDetailResponse)
        
        coEvery { offlineAttendanceDao.getUnsyncedAttendance() } returns unsyncedAttendance
        coEvery { apiService.checkIn(any()) } returns mockResponse // For the check-in record
        coEvery { apiService.checkOut(any()) } returns mockResponse // For the check-out record
        coEvery { offlineAttendanceDao.markAsSynced(any()) } returns 1
        coEvery { offlineAttendanceDao.deleteSyncedOlderThan(any()) } returns 2
        
        // Act
        val result = repository.syncOfflineAttendance()
        
        // Assert
        assertTrue(result is Result.Success)
        val successResult = result as Result.Success
        // The repository is expected to convert successful syncs (DetailResponse)
        // back into AttendanceResponse objects for the list.
        // We mainly verify the count and that sync operations happened.
        assertEquals(2, successResult.data.size) 
        
        // Verify all records were marked as synced
        coVerify { offlineAttendanceDao.markAsSynced("offline1") }
        coVerify { offlineAttendanceDao.markAsSynced("offline2") }
        coVerify { offlineAttendanceDao.deleteSyncedOlderThan(any()) }
    }

    @Test
    fun `canPunchAttendance returns false when recent punch exists`() = runTest {
        // Arrange
        val recentCache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1),
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = "09:00:00",
            checkOutTime = null
        )
        
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns recentCache
        
        // Act
        val canPunch = repository.canPunchAttendance()
        
        // Assert
        assertFalse(canPunch)
    }

    @Test
    fun `canPunchAttendance returns true when no recent punch exists`() = runTest {
        // Arrange
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null
        
        // Act
        val canPunch = repository.canPunchAttendance()
        
        // Assert
        assertTrue(canPunch)
    }

    @Test
    fun `getUnsyncedAttendanceCount returns correct count`() = runTest {
        // Arrange
        coEvery { offlineAttendanceDao.getUnsyncedCount() } returns 5
        
        // Act
        val count = repository.getUnsyncedAttendanceCount()
        
        // Assert
        assertEquals(5, count)
    }

    @Test
    fun `checkIn handles various HTTP error codes appropriately`() = runTest {
        // Arrange
        val latitude = 12.345
        val longitude = 67.890
        val scanType = "face"
        
        coEvery { attendanceCacheDao.getRecentPunch(any(), any()) } returns null
        coEvery { repository.getTodayAttendance() } returns Result.Success(
            TodayAttendance(false, false, null, null)
        )
        
        // Test 401 Unauthorized
        // The error body should ideally be a JSON string that can be parsed into DetailResponse
        // if the repository's error handling for HttpExceptions attempts to parse the body.
        // If it only uses the status code, then the content of the string is less critical.
        val errorBodyJson = """{"detail":"Unauthorized API access"}"""
        val unauthorizedResponse = Response.error<DetailResponse>(
            401, 
            errorBodyJson.toResponseBody("application/json".toMediaTypeOrNull())
        )
        coEvery { apiService.checkIn(any()) } returns unauthorizedResponse
        
        // Act
        val result = repository.checkIn(latitude, longitude, scanType)
        
        // Assert
        assertTrue(result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Authentication failed. Please login again.", errorResult.exception.message)
    }
}