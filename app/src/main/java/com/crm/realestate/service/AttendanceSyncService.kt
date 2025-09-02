package com.crm.realestate.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.work.*
import com.crm.realestate.data.repository.RepositoryProvider
import com.crm.realestate.utils.NetworkConnectivityMonitor
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Background service for syncing offline attendance records
 */
class AttendanceSyncService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC_NOW -> {
                syncAttendanceNow()
            }
            ACTION_SCHEDULE_PERIODIC_SYNC -> {
                schedulePeriodicSync()
            }
            ACTION_CANCEL_SYNC -> {
                cancelPeriodicSync()
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    private fun syncAttendanceNow() {
        serviceScope.launch {
            try {
                val attendanceRepository = RepositoryProvider.getAttendanceRepository(this@AttendanceSyncService)
                val networkMonitor = NetworkConnectivityMonitor(this@AttendanceSyncService)
                
                if (networkMonitor.isCurrentlyConnected()) {
                    val result = attendanceRepository.syncOfflineAttendance()
                    
                    when (result) {
                        is com.crm.realestate.data.models.Result.Success -> {
                            if (result.data.isNotEmpty()) {
                                sendSyncBroadcast(SYNC_SUCCESS, "Successfully synced ${result.data.size} attendance records")
                            } else {
                                sendSyncBroadcast(SYNC_SUCCESS, "No pending attendance records to sync")
                            }
                        }
                        is com.crm.realestate.data.models.Result.Error -> {
                            sendSyncBroadcast(SYNC_FAILED, result.message ?: result.exception.message ?: "Sync failed")
                        }
                        else -> {
                            sendSyncBroadcast(SYNC_FAILED, "Unknown sync error")
                        }
                    }
                } else {
                    sendSyncBroadcast(SYNC_FAILED, "No internet connection available")
                }
            } catch (e: Exception) {
                sendSyncBroadcast(SYNC_FAILED, "Sync error: ${e.message}")
            } finally {
                stopSelf()
            }
        }
    }
    
    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val syncWorkRequest = PeriodicWorkRequestBuilder<AttendanceSyncWorker>(
            15, TimeUnit.MINUTES // Sync every 15 minutes when connected
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }
    
    private fun cancelPeriodicSync() {
        WorkManager.getInstance(this).cancelUniqueWork(SYNC_WORK_NAME)
    }
    
    private fun sendSyncBroadcast(status: String, message: String) {
        val intent = Intent(SYNC_BROADCAST_ACTION).apply {
            putExtra(EXTRA_SYNC_STATUS, status)
            putExtra(EXTRA_SYNC_MESSAGE, message)
        }
        sendBroadcast(intent)
    }
    
    companion object {
        const val ACTION_SYNC_NOW = "com.crm.realestate.SYNC_NOW"
        const val ACTION_SCHEDULE_PERIODIC_SYNC = "com.crm.realestate.SCHEDULE_PERIODIC_SYNC"
        const val ACTION_CANCEL_SYNC = "com.crm.realestate.CANCEL_SYNC"
        
        const val SYNC_BROADCAST_ACTION = "com.crm.realestate.SYNC_STATUS"
        const val EXTRA_SYNC_STATUS = "sync_status"
        const val EXTRA_SYNC_MESSAGE = "sync_message"
        
        const val SYNC_SUCCESS = "success"
        const val SYNC_FAILED = "failed"
        const val SYNC_IN_PROGRESS = "in_progress"
        
        private const val SYNC_WORK_NAME = "attendance_sync_work"
        
        /**
         * Start immediate sync
         */
        fun startSync(context: Context) {
            val intent = Intent(context, AttendanceSyncService::class.java).apply {
                action = ACTION_SYNC_NOW
            }
            context.startService(intent)
        }
        
        /**
         * Schedule periodic sync
         */
        fun schedulePeriodicSync(context: Context) {
            val intent = Intent(context, AttendanceSyncService::class.java).apply {
                action = ACTION_SCHEDULE_PERIODIC_SYNC
            }
            context.startService(intent)
        }
        
        /**
         * Cancel periodic sync
         */
        fun cancelSync(context: Context) {
            val intent = Intent(context, AttendanceSyncService::class.java).apply {
                action = ACTION_CANCEL_SYNC
            }
            context.startService(intent)
        }
    }
}

/**
 * WorkManager worker for periodic attendance sync
 */
class AttendanceSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val attendanceRepository = RepositoryProvider.getAttendanceRepository(applicationContext)
            val networkMonitor = NetworkConnectivityMonitor(applicationContext)
            
            if (!networkMonitor.isCurrentlyConnected()) {
                return ListenableWorker.Result.retry()
            }
            
            val syncResult = attendanceRepository.syncOfflineAttendance()
            
            when (syncResult) {
                is com.crm.realestate.data.models.Result.Success -> {
                    // Send success broadcast
                    val intent = Intent(AttendanceSyncService.SYNC_BROADCAST_ACTION).apply {
                        putExtra(AttendanceSyncService.EXTRA_SYNC_STATUS, AttendanceSyncService.SYNC_SUCCESS)
                        putExtra(AttendanceSyncService.EXTRA_SYNC_MESSAGE, 
                            "Background sync completed: ${syncResult.data.size} records synced")
                    }
                    applicationContext.sendBroadcast(intent)
                    ListenableWorker.Result.success()
                }
                is com.crm.realestate.data.models.Result.Error -> {
                    // Send failure broadcast
                    val intent = Intent(AttendanceSyncService.SYNC_BROADCAST_ACTION).apply {
                        putExtra(AttendanceSyncService.EXTRA_SYNC_STATUS, AttendanceSyncService.SYNC_FAILED)
                        putExtra(AttendanceSyncService.EXTRA_SYNC_MESSAGE, 
                            syncResult.message ?: syncResult.exception.message ?: "Background sync failed")
                    }
                    applicationContext.sendBroadcast(intent)
                    ListenableWorker.Result.retry()
                }
                else -> ListenableWorker.Result.retry()
            }
        } catch (e: Exception) {
            ListenableWorker.Result.failure()
        }
    }
}