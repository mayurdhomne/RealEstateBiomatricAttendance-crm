package com.crm.realestate.utils

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.crm.realestate.data.repository.AttendanceRepository
import com.crm.realestate.service.AttendanceSyncService
import com.crm.realestate.sync.SyncStatus
import com.crm.realestate.utils.SyncStatusManager
import com.crm.realestate.ui.SyncStatusView
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Manager class to handle offline attendance functionality integration
 * Provides easy integration of sync status and offline capabilities into activities
 */
class OfflineAttendanceManager(
    private val context: Context,
    private val attendanceRepository: AttendanceRepository,
    private val lifecycleOwner: LifecycleOwner
) {
    
    private var syncStatusManager: SyncStatusManager? = null
    private var networkMonitor: NetworkConnectivityMonitor? = null
    
    /**
     * Initialize offline attendance management
     */
    fun initialize() {
        syncStatusManager = SyncStatusManager(context, attendanceRepository)
        networkMonitor = NetworkConnectivityMonitor(context)
        
        // Schedule periodic sync
        syncStatusManager?.schedulePeriodicSync()
    }
    
    /**
     * Setup sync status view with automatic updates
     */
    fun setupSyncStatusView(syncStatusView: SyncStatusView) {
        val syncManager = syncStatusManager ?: return
        val netMonitor = networkMonitor ?: return
        
        // Observe sync status changes and update UI
        lifecycleOwner.lifecycleScope.launch {
            combine(
                syncManager.syncStatus,
                syncManager.unsyncedCount,
                syncManager.syncMessage
            ) { status, count, message ->
                Triple(status, count, message)
            }.collect { (status, count, message) ->
                val networkDescription = netMonitor.getNetworkTypeDescription()
                val convertedStatus = when (status) {
                    SyncStatusManager.SyncStatus.IDLE -> SyncStatus.Complete
                    SyncStatusManager.SyncStatus.SYNCING -> SyncStatus.InProgress
                    SyncStatusManager.SyncStatus.SUCCESS -> SyncStatus.Success("Last sync completed")
                    SyncStatusManager.SyncStatus.FAILED -> SyncStatus.Error("Sync failed")
                }
                syncStatusView.updateSyncStatus(convertedStatus, message, count, networkDescription)
            }
        }
        
        // Set sync button click listener
        syncStatusView.setSyncButtonClickListener {
            triggerManualSync()
        }
        
        // Update unsynced count initially
        syncManager.updateUnsyncedCount()
    }
    
    /**
     * Trigger manual sync
     */
    fun triggerManualSync() {
        syncStatusManager?.triggerSync()
    }
    
    /**
     * Check if sync is needed
     */
    fun isSyncNeeded(): Boolean {
        return syncStatusManager?.isSyncNeeded() ?: false
    }
    
    /**
     * Get current sync status description
     */
    fun getSyncStatusDescription(): String {
        return syncStatusManager?.getSyncStatusDescription() ?: "Sync status unavailable"
    }
    
    /**
     * Check if currently syncing
     */
    fun isSyncing(): Boolean {
        return syncStatusManager?.isSyncing() ?: false
    }
    
    /**
     * Get network status description
     */
    fun getNetworkStatusDescription(): String {
        return networkMonitor?.getNetworkTypeDescription() ?: "Network status unavailable"
    }
    
    /**
     * Check if network is connected
     */
    fun isNetworkConnected(): Boolean {
        return networkMonitor?.isCurrentlyConnected() ?: false
    }
    
    /**
     * Save attendance for offline sync when network is unavailable
     */
    suspend fun saveOfflineAttendance(
        latitude: Double,
        longitude: Double,
        scanType: String,
        attendanceType: String
    ) {
        attendanceRepository.saveOfflineAttendance(latitude, longitude, scanType, attendanceType)
        syncStatusManager?.updateUnsyncedCount()
    }
    
    /**
     * Get count of unsynced attendance records
     */
    suspend fun getUnsyncedCount(): Int {
        return attendanceRepository.getUnsyncedAttendanceCount()
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        syncStatusManager?.cleanup()
        syncStatusManager = null
        networkMonitor = null
    }
    
    companion object {
        /**
         * Create and initialize OfflineAttendanceManager
         */
        fun create(
            context: Context,
            attendanceRepository: AttendanceRepository,
            lifecycleOwner: LifecycleOwner
        ): OfflineAttendanceManager {
            val manager = OfflineAttendanceManager(context, attendanceRepository, lifecycleOwner)
            manager.initialize()
            return manager
        }
    }
}