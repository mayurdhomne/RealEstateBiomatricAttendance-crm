package com.crm.realestate.offline

import android.content.Context
import android.content.Intent
import com.crm.realestate.data.repository.AttendanceRepository
import com.crm.realestate.service.AttendanceSyncService
import com.crm.realestate.utils.SyncStatusManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for SyncStatusManager
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class SyncStatusManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockAttendanceRepository: AttendanceRepository
    private lateinit var syncStatusManager: SyncStatusManager
    
    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockAttendanceRepository = mockk(relaxed = true)
        
        // Mock context methods
        every { mockContext.registerReceiver(any(), any()) } returns Intent()
        every { mockContext.unregisterReceiver(any()) } just Runs
        every { mockContext.sendBroadcast(any()) } just Runs
        every { mockContext.startService(any()) } returns mockk()
        
        syncStatusManager = SyncStatusManager(mockContext, mockAttendanceRepository)
    }
    
    @After
    fun teardown() {
        syncStatusManager.cleanup()
    }
    
    @Test
    fun `test initial sync status is idle`() = runTest {
        // When
        val initialStatus = syncStatusManager.syncStatus.first()
        
        // Then
        assertEquals(SyncStatusManager.SyncStatus.IDLE, initialStatus)
    }
    
    @Test
    fun `test trigger sync updates status to syncing`() = runTest {
        // When
        syncStatusManager.triggerSync()
        val status = syncStatusManager.syncStatus.first()
        
        // Then
        assertEquals(SyncStatusManager.SyncStatus.SYNCING, status)
        verify { mockContext.startService(any()) }
    }
    
    @Test
    fun `test sync status description with pending records`() = runTest {
        // Given
        coEvery { mockAttendanceRepository.getUnsyncedAttendanceCount() } returns 3
        
        // When
        syncStatusManager.updateUnsyncedCount()
        val description = syncStatusManager.getSyncStatusDescription()
        
        // Then
        assertTrue(description.contains("3 attendance records pending sync"))
    }
    
    @Test
    fun `test sync status description with no pending records`() = runTest {
        // Given
        coEvery { mockAttendanceRepository.getUnsyncedAttendanceCount() } returns 0
        
        // When
        syncStatusManager.updateUnsyncedCount()
        val description = syncStatusManager.getSyncStatusDescription()
        
        // Then
        assertTrue(description.contains("All attendance records synced"))
    }
    
    @Test
    fun `test is sync needed returns true when unsynced records exist`() = runTest {
        // Given
        coEvery { mockAttendanceRepository.getUnsyncedAttendanceCount() } returns 2
        
        // When
        syncStatusManager.updateUnsyncedCount()
        val isSyncNeeded = syncStatusManager.isSyncNeeded()
        
        // Then
        assertTrue(isSyncNeeded)
    }
    
    @Test
    fun `test is sync needed returns false when no unsynced records`() = runTest {
        // Given
        coEvery { mockAttendanceRepository.getUnsyncedAttendanceCount() } returns 0
        
        // When
        syncStatusManager.updateUnsyncedCount()
        val isSyncNeeded = syncStatusManager.isSyncNeeded()
        
        // Then
        assertFalse(isSyncNeeded)
    }
    
    @Test
    fun `test is syncing returns true during sync`() = runTest {
        // When
        syncStatusManager.triggerSync()
        val isSyncing = syncStatusManager.isSyncing()
        
        // Then
        assertTrue(isSyncing)
    }
    
    @Test
    fun `test schedule periodic sync calls service`() {
        // When
        syncStatusManager.schedulePeriodicSync()
        
        // Then
        verify { mockContext.startService(match { intent ->
            intent.action == AttendanceSyncService.ACTION_SCHEDULE_PERIODIC_SYNC
        }) }
    }
    
    @Test
    fun `test cancel periodic sync calls service`() {
        // When
        syncStatusManager.cancelPeriodicSync()
        
        // Then
        verify { mockContext.startService(match { intent ->
            intent.action == AttendanceSyncService.ACTION_CANCEL_SYNC
        }) }
    }
    
    @Test
    fun `test cleanup unregisters broadcast receiver`() {
        // When
        syncStatusManager.cleanup()
        
        // Then
        verify { mockContext.unregisterReceiver(any()) }
    }
}