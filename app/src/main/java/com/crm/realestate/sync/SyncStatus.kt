package com.crm.realestate.sync

sealed class SyncStatus {
    object InProgress : SyncStatus()
    data class Success(val lastSyncTime: String) : SyncStatus()
    object NetworkUnavailable : SyncStatus()
    object Complete : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}