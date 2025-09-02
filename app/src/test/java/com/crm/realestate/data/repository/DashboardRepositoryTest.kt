package com.crm.realestate.data.repository

import com.crm.realestate.data.api.AttendanceApiService
import com.crm.realestate.data.models.ApiResponse
import com.crm.realestate.data.models.AttendanceOverview
import com.crm.realestate.data.models.LeaveInfo
import com.crm.realestate.utils.Result
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Unit tests for DashboardRepository
 */
class DashboardRepositoryTest {

    private lateinit var repository: DashboardRepository
    private lateinit var mockApiService: AttendanceApiService

    @Before
    fun setup() {
        mockApiService = mockk()
        repository = DashboardRepository(mockApiService)
    }

    @Test
    fun `getAttendanceOverview should return success when API call succeeds`() = runTest {
        // Given
        val mockOverview = AttendanceOverview(
            daysPresent = 20,
            totalWorkingDays = 25,
            lastCheckIn = "2024-01-15T09:00:00",
            presentPercentage = 80.0f
        )
        val mockApiResponse = ApiResponse(
            success = true,
            message = "Success",
            data = mockOverview
        )
        val mockResponse = Response.success(mockApiResponse)
        coEvery { mockApiService.getAttendanceOverview() } returns mockResponse

        // When
        val result = repository.getAttendanceOverview()

        // Then
        assertTrue(result is Result.Success)
        assertEquals(mockOverview, (result as Result.Success).data)
    }

    @Test
    fun `getAttendanceOverview should return error when API call fails`() = runTest {
        // Given
        val mockResponse = Response.error<ApiResponse<AttendanceOverview>>(404, mockk(relaxed = true))
        coEvery { mockApiService.getAttendanceOverview() } returns mockResponse

        // When
        val result = repository.getAttendanceOverview()

        // Then
        assertTrue(result is Result.Error)
    }

    @Test
    fun `getLeaveInformation should return success when API call succeeds`() = runTest {
        // Given
        val mockLeaveInfo = LeaveInfo(
            sickLeaves = 2,
            otherLeaves = 3,
            totalLeaves = 5
        )
        val mockApiResponse = ApiResponse(
            success = true,
            message = "Success",
            data = mockLeaveInfo
        )
        val mockResponse = Response.success(mockApiResponse)
        coEvery { mockApiService.getLeaveInfo() } returns mockResponse

        // When
        val result = repository.getLeaveInformation()

        // Then
        assertTrue(result is Result.Success)
        assertEquals(mockLeaveInfo, (result as Result.Success).data)
    }

    @Test
    fun `getLeaveInformation should return error when API call fails`() = runTest {
        // Given
        val mockResponse = Response.error<ApiResponse<LeaveInfo>>(500, mockk(relaxed = true))
        coEvery { mockApiService.getLeaveInfo() } returns mockResponse

        // When
        val result = repository.getLeaveInformation()

        // Then
        assertTrue(result is Result.Error)
    }

    @Test
    fun `getAttendanceOverview should handle exception`() = runTest {
        // Given
        coEvery { mockApiService.getAttendanceOverview() } throws RuntimeException("Network error")

        // When
        val result = repository.getAttendanceOverview()

        // Then
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception is RuntimeException)
    }
}