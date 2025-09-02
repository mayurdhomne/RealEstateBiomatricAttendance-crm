package com.crm.realestate.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.crm.realestate.data.repository.AttendanceRepository
import com.crm.realestate.service.AttendanceSyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages sync status and provides user feedback for offline attendance sync
 */
class SyncStatusManager(
    private val context: Context,
    private val attendanceRepository: AttendanceRepository
) {
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Sync status state
    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    
    // Unsynced count state
    private val _unsyncedCount = MutableStateFlow(0)
    val unsyncedCount: StateFlow<Int> = _unsyncedCount.asStateFlow()
    
    // Sync message state
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()
    
    // Network connectivity monitor
    private val networkMonitor = NetworkConnectivityMonitor(context)
    
    // Broadcast receiver for sync status updates
    private val syncBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AttendanceSyncService.SYNC_BROADCAST_ACTION) {
                val status = intent.getStringExtra(AttendanceSyncService.EXTRA_SYNC_STATUS)
                val message = intent.getStringExtra(AttendanceSyncService.EXTRA_SYNC_MESSAGE)
                
                when (status) {
                    AttendanceSyncService.SYNC_SUCCESS -> {
                        _syncStatus.value = SyncStatus.SUCCESS
                        _syncMessage.value = message
                        updateUnsyncedCount()
                    }
                    AttendanceSyncService.SYNC_FAILED -> {
                        _syncStatus.value = SyncStatus.FAILED
                        _syncMessage.value = message
                    }
                    AttendanceSyncService.SYNC_IN_PROGRESS -> {
                        _syncStatus.value = SyncStatus.SYNCING
                        _syncMessage.value = message
                    }
                }
                
                // Clear message after 5 seconds
                scope.launch {
                    delay(5000)
                    if (_syncMessage.value == message) {
                        _syncMessage.value = null
                        if (_syncStatus.value != SyncStatus.SYNCING) {
                            _syncStatus.value = SyncStatus.IDLE
                        }
                    }
                }
            }
        }
    }
    
    init {
        // Register broadcast receiver
        val filter = IntentFilter(AttendanceSyncService.SYNC_BROADCAST_ACTION)
        context.registerReceiver(syncBroadcastReceiver, filter)
        
        // Monitor network connectivity for automatic sync
        scope.launch {
            networkMonitor.isConnected.collect { isConnected ->
                if (isConnected && _unsyncedCount.value > 0) {
                    // Automatically trigger sync when network becomes available
                    triggerSync()
                }
            }
        }
        
        // Update unsynced count initially
        updateUnsyncedCount()
    }
    
    /**
     * Trigger manual sync
     */
    fun triggerSync() {
        if (_syncStatus.value == SyncStatus.SYNCING) {
            return // Already syncing
        }
        
        _syncStatus.value = SyncStatus.SYNCING
        _syncMessage.value = "Syncing attendance records..."
        
        AttendanceSyncService.startSync(context)
    }
    
    /**
     * Update unsynced count from database
     */
    fun updateUnsyncedCount() {
        scope.launch {
            try {
                val count = attendanceRepository.getUnsyncedAttendanceCount()
                _unsyncedCount.value = count
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }
    
    /**
     * Get sync status description for UI
     */
    fun getSyncStatusDescription(): String {
        return when (_syncStatus.value) {
            SyncStatus.IDLE -> {
                if (_unsyncedCount.value > 0) {
                    "${_unsyncedCount.value} attendance records pending sync"
                } else {
                    "All attendance records synced"
                }
            }
            SyncStatus.SYNCING -> "Syncing attendance records..."
            SyncStatus.SUCCESS -> _syncMessage.value ?: "Sync completed successfully"
            SyncStatus.FAILED -> _syncMessage.value ?: "Sync failed"
        }
    }
    
    /**
     * Check if sync is needed
     */
    fun isSyncNeeded(): Boolean {
        return _unsyncedCount.value > 0
    }
    
    /**
     * Check if currently syncing
     */
    fun isSyncing(): Boolean {
        return _syncStatus.value == SyncStatus.SYNCING
    }
    
    /**
     * Get network status description
     */
    fun getNetworkStatusDescription(): String {
        return if (networkMonitor.isCurrentlyConnected()) {
            "Connected via ${networkMonitor.getNetworkTypeDescription()}"
        } else {
            "No internet connection"
        }
    }
    
    /**
     * Schedule periodic sync
     */
    fun schedulePeriodicSync() {
        AttendanceSyncService.schedulePeriodicSync(context)
    }
    
    /**
     * Cancel periodic sync
     */
    fun cancelPeriodicSync() {
        AttendanceSyncService.cancelSync(context)
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(syncBroadcastReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        scope.cancel()
    }
    
    /**
     * Sync status enumeration
     */
    enum class SyncStatus {
        IDLE,
        SYNCING,
        SUCCESS,
        FAILED
    }
}