package com.crm.realestate.data.models

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for attendance-related data models
 * Tests JSON serialization/deserialization and model behavior
 */
class AttendanceModelsTest {
    
    private val gson = Gson()
    
    @Test
    fun `test CheckInRequest serialization`() {
        val request = CheckInRequest(
            checkInLatitude = 19.0760,
            checkInLongitude = 72.8777,
            scanType = "face"
        )
        
        val json = gson.toJson(request)
        assertNotNull(json)
        assertTrue(json.contains("\"check_in_latitude\""))
        assertTrue(json.contains("\"check_in_longitude\""))
        assertTrue(json.contains("\"scan_type\""))
        assertTrue(json.contains("19.076"))
        assertTrue(json.contains("72.8777"))
        assertTrue(json.contains("face"))
    }
    
    @Test
    fun `test CheckInRequest deserialization`() {
        val json = """
            {
                "check_in_latitude": 19.0760,
                "check_in_longitude": 72.8777,
                "scan_type": "fingerprint"
            }
        """.trimIndent()
        
        val request = gson.fromJson(json, CheckInRequest::class.java)
        
        assertNotNull(request)
        assertEquals(19.0760, request.checkInLatitude, 0.0001)
        assertEquals(72.8777, request.checkInLongitude, 0.0001)
        assertEquals("fingerprint", request.scanType)
    }
    
    @Test
    fun `test CheckOutRequest serialization`() {
        val request = CheckOutRequest(
            checkOutLatitude = 19.0760,
            checkOutLongitude = 72.8777,
            scanType = "face"
        )
        
        val json = gson.toJson(request)
        assertNotNull(json)
        assertTrue(json.contains("\"check_out_latitude\""))
        assertTrue(json.contains("\"check_out_longitude\""))
        assertTrue(json.contains("\"scan_type\""))
    }
    
    @Test
    fun `test CheckOutRequest deserialization`() {
        val json = """
            {
                "check_out_latitude": 19.0760,
                "check_out_longitude": 72.8777,
                "scan_type": "fingerprint"
            }
        """.trimIndent()
        
        val request = gson.fromJson(json, CheckOutRequest::class.java)
        
        assertNotNull(request)
        assertEquals(19.0760, request.checkOutLatitude, 0.0001)
        assertEquals(72.8777, request.checkOutLongitude, 0.0001)
        assertEquals("fingerprint", request.scanType)
    }
    
    @Test
    fun `test AttendanceResponse serialization`() {
        val response = AttendanceResponse(
            id = "ATT001",
            employeeId = "EMP001",
            checkInTime = "2024-01-15T09:00:00Z",
            checkOutTime = null,
            status = "checked_in",
            message = "Check-in successful"
        )
        
        val json = gson.toJson(response)
        assertNotNull(json)
        assertTrue(json.contains("\"id\""))
        assertTrue(json.contains("\"employee_id\""))
        assertTrue(json.contains("\"check_in_time\""))
        assertTrue(json.contains("\"status\""))
        assertTrue(json.contains("\"message\""))
    }
    
    @Test
    fun `test AttendanceResponse deserialization`() {
        val json = """
            {
                "id": "ATT001",
                "employee_id": "EMP001",
                "check_in_time": "2024-01-15T09:00:00Z",
                "check_out_time": "2024-01-15T18:00:00Z",
                "status": "completed",
                "message": "Attendance completed successfully"
            }
        """.trimIndent()
        
        val response = gson.fromJson(json, AttendanceResponse::class.java)
        
        assertNotNull(response)
        assertEquals("ATT001", response.id)
        assertEquals("EMP001", response.employeeId)
        assertEquals("2024-01-15T09:00:00Z", response.checkInTime)
        assertEquals("2024-01-15T18:00:00Z", response.checkOutTime)
        assertEquals("completed", response.status)
        assertEquals("Attendance completed successfully", response.message)
    }
    
    @Test
    fun `test TodayAttendance model`() {
        val todayAttendance = TodayAttendance(
            hasCheckedIn = true,
            hasCheckedOut = false,
            checkInTime = "09:00:00",
            checkOutTime = null
        )
        
        assertTrue(todayAttendance.hasCheckedIn)
        assertFalse(todayAttendance.hasCheckedOut)
        assertEquals("09:00:00", todayAttendance.checkInTime)
        assertNull(todayAttendance.checkOutTime)
    }
    
    @Test
    fun `test AttendanceOverview serialization`() {
        val overview = AttendanceOverview(
            daysPresent = 20,
            totalWorkingDays = 22,
            lastCheckIn = "2024-01-15T09:00:00Z",
            presentPercentage = 90.9f
        )
        
        val json = gson.toJson(overview)
        assertNotNull(json)
        assertTrue(json.contains("\"days_present\""))
        assertTrue(json.contains("\"total_working_days\""))
        assertTrue(json.contains("\"last_check_in\""))
        assertTrue(json.contains("\"present_percentage\""))
    }
    
    @Test
    fun `test AttendanceOverview deserialization`() {
        val json = """
            {
                "days_present": 18,
                "total_working_days": 20,
                "last_check_in": "2024-01-14T09:15:00Z",
                "present_percentage": 90.0
            }
        """.trimIndent()
        
        val overview = gson.fromJson(json, AttendanceOverview::class.java)
        
        assertNotNull(overview)
        assertEquals(18, overview.daysPresent)
        assertEquals(20, overview.totalWorkingDays)
        assertEquals("2024-01-14T09:15:00Z", overview.lastCheckIn)
        assertEquals(90.0f, overview.presentPercentage, 0.01f)
    }
    
    @Test
    fun `test LeaveInfo serialization`() {
        val leaveInfo = LeaveInfo(
            sickLeaves = 2,
            otherLeaves = 3,
            totalLeaves = 5
        )
        
        val json = gson.toJson(leaveInfo)
        assertNotNull(json)
        assertTrue(json.contains("\"sick_leaves\""))
        assertTrue(json.contains("\"other_leaves\""))
        assertTrue(json.contains("\"total_leaves\""))
    }
    
    @Test
    fun `test LeaveInfo deserialization`() {
        val json = """
            {
                "sick_leaves": 1,
                "other_leaves": 2,
                "total_leaves": 3
            }
        """.trimIndent()
        
        val leaveInfo = gson.fromJson(json, LeaveInfo::class.java)
        
        assertNotNull(leaveInfo)
        assertEquals(1, leaveInfo.sickLeaves)
        assertEquals(2, leaveInfo.otherLeaves)
        assertEquals(3, leaveInfo.totalLeaves)
    }
    
    @Test
    fun `test AttendanceResponse with null check times`() {
        val json = """
            {
                "id": "ATT002",
                "employee_id": "EMP002",
                "check_in_time": null,
                "check_out_time": null,
                "status": "pending",
                "message": "Attendance pending"
            }
        """.trimIndent()
        
        val response = gson.fromJson(json, AttendanceResponse::class.java)
        
        assertNotNull(response)
        assertEquals("ATT002", response.id)
        assertEquals("EMP002", response.employeeId)
        assertNull(response.checkInTime)
        assertNull(response.checkOutTime)
        assertEquals("pending", response.status)
        assertEquals("Attendance pending", response.message)
    }
}