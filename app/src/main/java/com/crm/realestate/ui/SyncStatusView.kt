package com.crm.realestate.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.crm.realestate.R
import com.crm.realestate.sync.SyncStatus
import com.crm.realestate.utils.SyncStatusManager

/**
 * Custom view for displaying sync status with visual indicators
 */
class SyncStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    private val statusIcon: ImageView
    private val statusText: TextView
    private val syncProgress: ProgressBar
    private val networkStatus: TextView
    private val syncButton: TextView
    
    init {
        orientation = HORIZONTAL
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.view_sync_status, this, true)
        
        statusIcon = findViewById(R.id.iv_sync_status_icon)
        statusText = findViewById(R.id.tv_sync_status_text)
        syncProgress = findViewById(R.id.pb_sync_progress)
        networkStatus = findViewById(R.id.tv_network_status)
        syncButton = findViewById(R.id.tv_sync_button)
    }
    
    /**
     * Update the sync status display
     */
    fun updateSyncStatus(
        status: SyncStatus,
        message: String?,
        unsyncedCount: Int,
        networkDescription: String
    ) {
        when (status) {
            is SyncStatus.InProgress -> {
                showSyncingStatus(message)
            }
            is SyncStatus.Success -> {
                showSuccessStatus(message)
            }
            is SyncStatus.NetworkUnavailable -> {
                showNetworkUnavailableStatus(message)
            }
            is SyncStatus.Complete -> {
                showCompleteStatus(message)
            }
            is SyncStatus.Error -> {
                showFailedStatus(message)
            }
        }
        
        updateNetworkStatus(networkDescription)
        updateSyncButton(status, unsyncedCount)
    }
    
    private fun showSyncingStatus(message: String?) {
        statusIcon.setImageResource(R.drawable.ic_sync)
        statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.color_orange))
        statusText.text = message ?: "Syncing attendance records..."
        statusText.setTextColor(ContextCompat.getColor(context, R.color.color_orange))
        syncProgress.visibility = View.VISIBLE
    }
    
    private fun showSuccessStatus(message: String?) {
        statusIcon.setImageResource(R.drawable.ic_sync_done)
        statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.green))
        statusText.text = message ?: "Sync completed successfully"
        statusText.setTextColor(ContextCompat.getColor(context, R.color.green))
        syncProgress.visibility = View.GONE
    }
    
    private fun showNetworkUnavailableStatus(message: String?) {
        statusIcon.setImageResource(R.drawable.ic_sync_problem)
        statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.blue))
        statusText.text = message ?: "Offline"
        statusText.setTextColor(ContextCompat.getColor(context, R.color.blue))
        syncProgress.visibility = View.GONE
    }
    
    private fun showCompleteStatus(message: String?) {
        statusIcon.setImageResource(R.drawable.ic_sync_done)
        statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.green))
        statusText.text = message ?: "Up to date"
        statusText.setTextColor(ContextCompat.getColor(context, R.color.green))
        syncProgress.visibility = View.GONE
    }
    
    private fun showFailedStatus(message: String?) {
        statusIcon.setImageResource(R.drawable.ic_sync_problem)
        statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.red))
        statusText.text = message ?: "Sync failed"
        statusText.setTextColor(ContextCompat.getColor(context, R.color.red))
        syncProgress.visibility = View.GONE
    }
    
    private fun updateNetworkStatus(networkDescription: String) {
        networkStatus.text = networkDescription
        
        val isConnected = !networkDescription.contains("No internet")
        val colorRes = if (isConnected) R.color.green else R.color.red
        networkStatus.setTextColor(ContextCompat.getColor(context, colorRes))
    }
    
    private fun updateSyncButton(status: SyncStatus, unsyncedCount: Int) {
        when {
            status is SyncStatus.InProgress -> {
                syncButton.text = "Syncing..."
                syncButton.isEnabled = false
                syncButton.alpha = 0.6f
            }
            unsyncedCount > 0 -> {
                syncButton.text = "Sync Now"
                syncButton.isEnabled = true
                syncButton.alpha = 1.0f
            }
            else -> {
                syncButton.text = "Check Sync"
                syncButton.isEnabled = true
                syncButton.alpha = 1.0f
            }
        }
    }
    
    /**
     * Set click listener for sync button
     */
    fun setSyncButtonClickListener(listener: OnClickListener) {
        syncButton.setOnClickListener(listener)
    }
    
    /**
     * Show/hide the entire sync status view
     */
    fun setVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }
}