package com.crm.realestate.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.crm.realestate.R
import com.crm.realestate.data.api.AttendanceApiService
import com.crm.realestate.data.repository.DashboardRepository
import com.crm.realestate.network.ApiConfig
import com.crm.realestate.utils.BaseErrorHandlingActivity
import com.crm.realestate.utils.ErrorHandler
import com.crm.realestate.utils.LoadingStateManager
import com.crm.realestate.viewmodel.DashboardViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

/**
 * DashboardActivity displays attendance overview, leave information, and provides
 * access to attendance functionality through a floating action button
 */
class DashboardActivity : BaseErrorHandlingActivity() {

    private lateinit var viewModel: DashboardViewModel
    private lateinit var dashboardRepository: DashboardRepository
    private lateinit var attendanceRepository: com.crm.realestate.data.repository.AttendanceRepository
    
    // UI Components
    private lateinit var tvDaysPresent: TextView
    private lateinit var tvLastCheckIn: TextView
    private lateinit var tvSickLeaves: TextView
    private lateinit var tvOtherLeaves: TextView
    private lateinit var pieChart: PieChart
    private lateinit var progressBar: ProgressBar
    private lateinit var btnCheckIn: MaterialButton
    private lateinit var btnCheckOut: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        
        initializeComponents()
        setupViewModel()
        setupObservers()
        setupClickListeners()
        
        // Load dashboard data
        viewModel.loadDashboardData()
    }
    
    /**
     * Initialize UI components
     */
    private fun initializeComponents() {
        tvDaysPresent = findViewById(R.id.tvDaysPresent)
        tvLastCheckIn = findViewById(R.id.tvLastCheckIn)
        tvSickLeaves = findViewById(R.id.tvSickLeaves)
        tvOtherLeaves = findViewById(R.id.tvOtherLeaves)
        pieChart = findViewById(R.id.pieChart)
        progressBar = findViewById(R.id.progressBar)
    btnCheckIn = findViewById(R.id.btnCheckIn)
    btnCheckOut = findViewById(R.id.btnCheckOut)
        
        setupPieChart()
    }
    
    /**
     * Setup ViewModel with repository
     */
    private fun setupViewModel() {
        try {
            val retrofit = ApiConfig.provideRetrofit(this) { 
                // Handle unauthorized access - redirect to login
                redirectToLogin()
            }
            val apiService = retrofit.create(AttendanceApiService::class.java)
            dashboardRepository = DashboardRepository(apiService)
            attendanceRepository = com.crm.realestate.data.repository.RepositoryProvider.provideAttendanceRepository(this)
            
            viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return DashboardViewModel(dashboardRepository, attendanceRepository) as T
                }
            })[DashboardViewModel::class.java]
        } catch (e: Exception) {
            val error = ErrorHandler.handleException(this, e)
            handleError(error, onRetry = { recreate() })
        }
    }
    
    /**
     * Setup observers for ViewModel LiveData
     */
    private fun setupObservers() {
        viewModel.attendanceOverview.observe(this) { overview ->
            tvDaysPresent.text = overview.daysPresent.toString()
            
            // Format and display last check-in time
            overview.lastCheckIn?.let { lastCheckIn ->
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val date = inputFormat.parse(lastCheckIn)
                    tvLastCheckIn.text = date?.let { outputFormat.format(it) } ?: "--:--"
                } catch (e: Exception) {
                    tvLastCheckIn.text = "--:--"
                }
            } ?: run {
                tvLastCheckIn.text = "--:--"
            }
            
            // Update pie chart
            updatePieChart(overview.daysPresent, overview.totalWorkingDays)
        }
        
        viewModel.leaveInfo.observe(this) { leaveInfo ->
            tvSickLeaves.text = leaveInfo.sickLeaves.toString()
            tvOtherLeaves.text = leaveInfo.otherLeaves.toString()
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                showLoading("Loading dashboard data...", operationType = LoadingStateManager.OperationType.NETWORK)
            } else {
                hideLoading()
            }
        }
        
        viewModel.error.observe(this) { errorMessage ->
            errorMessage?.let {
                val error = ErrorHandler.AppError.UnknownError(it)
                handleError(error, onRetry = { 
                    viewModel.loadDashboardData() 
                })
                viewModel.clearError()
            }
        }
    }
    
    /**
     * Setup click listeners
     */
    private fun setupClickListeners() {
        btnCheckIn.setOnClickListener {
            // Show bottom sheet with attendance options for check-in
            showAttendanceBottomSheet(attendanceType = "check_in")
        }
        btnCheckOut.setOnClickListener {
            // Show bottom sheet with attendance options for check-out
            showAttendanceBottomSheet(attendanceType = "check_out")
        }
    }
    
    /**
     * Show bottom sheet with attendance options
     * @param attendanceType Either "check_in" or "check_out"
     */
    private fun showAttendanceBottomSheet(attendanceType: String) {
        // First check current attendance status
        checkNetworkAndExecute(
            operation = {
                // Load current status first to ensure we show the right options
                viewModel.loadDashboardData()
                
                val bottomSheetDialog = BottomSheetDialog(this)
                val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_attendance, null)
                bottomSheetDialog.setContentView(bottomSheetView)
                
                // Setup bottom sheet components
                val btnClose = bottomSheetView.findViewById<ImageView>(R.id.btnClose)
                val tvAttendanceType = bottomSheetView.findViewById<TextView>(R.id.tvAttendanceType)
                val cardFaceOption = bottomSheetView.findViewById<CardView>(R.id.cardFaceOption)
                val cardFingerprintOption = bottomSheetView.findViewById<CardView>(R.id.cardFingerprintOption)
                
                // Set attendance type and update UI
                if (attendanceType == "check_out") {
                    tvAttendanceType.text = "Check Out"
                    tvAttendanceType.setTextColor(ContextCompat.getColor(this, R.color.error_red))
                } else {
                    tvAttendanceType.text = "Check In"
                    tvAttendanceType.setTextColor(ContextCompat.getColor(this, R.color.success_green))
                }
                
                // Close button click listener
                btnClose.setOnClickListener {
                    bottomSheetDialog.dismiss()
                }
                
                // Face ID option click listener - with fallback to fingerprint on failure
                cardFaceOption.setOnClickListener {
                    bottomSheetDialog.dismiss()
                    checkNetworkAndExecute(
                        operation = {
                            startAttendanceWithFace(attendanceType)
                        },
                        showOfflineOption = true,
                        onOfflineMode = {
                            showConfirmation(
                                "Offline Mode",
                                "You can still record $attendanceType offline. It will be synced when connection is restored.",
                                onConfirm = {
                                    startAttendanceWithFace(attendanceType, offlineMode = true)
                                }
                            )
                        }
                    )
                }
                
                // Fingerprint option click listener
                cardFingerprintOption.setOnClickListener {
                    bottomSheetDialog.dismiss()
                    checkNetworkAndExecute(
                        operation = {
                            startAttendanceWithFingerprint(attendanceType)
                        },
                        showOfflineOption = true,
                        onOfflineMode = {
                            showConfirmation(
                                "Offline Mode",
                                "You can still record $attendanceType offline. It will be synced when connection is restored.",
                                onConfirm = {
                                    startAttendanceWithFingerprint(attendanceType, offlineMode = true)
                                }
                            )
                        }
                    )
                }
                
                bottomSheetDialog.show()
            },
            showOfflineOption = false
        )
    }
    
    /**
     * Start attendance process with face recognition
     * @param attendanceType Either "check_in" or "check_out"
     * @param offlineMode Whether to operate in offline mode
     */
    private fun startAttendanceWithFace(attendanceType: String, offlineMode: Boolean = false) {
        val intent = Intent(this, AttendanceActivity::class.java)
        intent.putExtra("biometric_type", "face")
        intent.putExtra("attendance_type", attendanceType)
        intent.putExtra("offline_mode", offlineMode)
        intent.putExtra("enable_fallback", true) // Enable fallback to fingerprint
        startActivity(intent)
    }
    
    /**
     * Start attendance process with fingerprint
     * @param attendanceType Either "check_in" or "check_out" 
     * @param offlineMode Whether to operate in offline mode
     */
    private fun startAttendanceWithFingerprint(attendanceType: String, offlineMode: Boolean = false) {
        val intent = Intent(this, AttendanceActivity::class.java)
        intent.putExtra("biometric_type", "fingerprint")
        intent.putExtra("attendance_type", attendanceType)
        intent.putExtra("offline_mode", offlineMode)
        startActivity(intent)
    }
    
    override fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    /**
     * Setup pie chart configuration
     */
    private fun setupPieChart() {
        pieChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            setExtraOffsets(5f, 10f, 5f, 5f)
            
            dragDecelerationFrictionCoef = 0.95f
            
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            
            holeRadius = 58f
            transparentCircleRadius = 61f
            
            setDrawCenterText(true)
            centerText = "Attendance\nOverview"
            setCenterTextSize(14f)
            setCenterTextColor(getColor(R.color.colorAccent))
            
            setRotationAngle(0f)
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            
            legend.isEnabled = true
            legend.textColor = getColor(R.color.colorAccent)
            legend.textSize = 12f
        }
    }
    
    /**
     * Update pie chart with attendance data
     */
    private fun updatePieChart(daysPresent: Int, totalWorkingDays: Int) {
        val daysAbsent = totalWorkingDays - daysPresent
        
        val entries = ArrayList<PieEntry>()
        if (daysPresent > 0) {
            entries.add(PieEntry(daysPresent.toFloat(), "Present"))
        }
        if (daysAbsent > 0) {
            entries.add(PieEntry(daysAbsent.toFloat(), "Absent"))
        }
        
        if (entries.isEmpty()) {
            entries.add(PieEntry(1f, "No Data"))
        }
        
        val dataSet = PieDataSet(entries, "").apply {
            setDrawIcons(false)
            sliceSpace = 3f
            iconsOffset = com.github.mikephil.charting.utils.MPPointF(0f, 40f)
            selectionShift = 5f
            
            colors = if (entries.size == 1 && entries[0].label == "No Data") {
                listOf(Color.LTGRAY)
            } else {
                listOf(
                    getColor(R.color.colorAccent), // Navy for Present
                    getColor(R.color.yellow)       // Gold for Absent
                )
            }
            
            valueLinePart1OffsetPercentage = 80f
            valueLinePart1Length = 0.2f
            valueLinePart2Length = 0.4f
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        }
        
        val data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter())
            setValueTextSize(11f)
            setValueTextColor(getColor(R.color.colorAccent))
        }
        
        pieChart.data = data
        pieChart.highlightValues(null)
        pieChart.invalidate()
    }
}