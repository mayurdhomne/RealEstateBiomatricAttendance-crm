package com.crm.realestate.utils

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import kotlin.math.roundToInt

/**
 * Helper class for implementing responsive design across different screen sizes
 * Ensures optimal layout on 5-7 inch screens and various orientations
 */
object ResponsiveDesignHelper {
    
    // Screen size categories
    enum class ScreenSize {
        SMALL,      // < 5 inches
        MEDIUM,     // 5-6 inches
        LARGE,      // 6-7 inches
        EXTRA_LARGE // > 7 inches
    }
    
    // Screen orientation
    enum class ScreenOrientation {
        PORTRAIT,
        LANDSCAPE
    }
    
    /**
     * Get current screen size category
     */
    fun getScreenSize(context: Context): ScreenSize {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density
        val screenSizeInches = kotlin.math.sqrt(
            (screenWidthDp * screenWidthDp + screenHeightDp * screenHeightDp).toDouble()
        ) / 160.0
        
        return when {
            screenSizeInches < 5.0 -> ScreenSize.SMALL
            screenSizeInches < 6.0 -> ScreenSize.MEDIUM
            screenSizeInches < 7.0 -> ScreenSize.LARGE
            else -> ScreenSize.EXTRA_LARGE
        }
    }
    
    /**
     * Get current screen orientation
     */
    fun getScreenOrientation(context: Context): ScreenOrientation {
        return when (context.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> ScreenOrientation.LANDSCAPE
            else -> ScreenOrientation.PORTRAIT
        }
    }
    
    /**
     * Get responsive dimension based on screen size
     */
    fun getResponsiveDimension(
        context: Context,
        smallDp: Int,
        mediumDp: Int,
        largeDp: Int,
        extraLargeDp: Int
    ): Int {
        return when (getScreenSize(context)) {
            ScreenSize.SMALL -> smallDp
            ScreenSize.MEDIUM -> mediumDp
            ScreenSize.LARGE -> largeDp
            ScreenSize.EXTRA_LARGE -> extraLargeDp
        }
    }
    
    /**
     * Convert dp to pixels
     */
    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).roundToInt()
    }
    
    /**
     * Convert pixels to dp
     */
    fun pxToDp(context: Context, px: Int): Int {
        return (px / context.resources.displayMetrics.density).roundToInt()
    }
    
    /**
     * Setup responsive margins for a view
     */
    fun setupResponsiveMargins(
        view: View,
        smallMarginDp: Int = 8,
        mediumMarginDp: Int = 16,
        largeMarginDp: Int = 24,
        extraLargeMarginDp: Int = 32
    ) {
        val context = view.context
        val marginDp = getResponsiveDimension(
            context, smallMarginDp, mediumMarginDp, largeMarginDp, extraLargeMarginDp
        )
        val marginPx = dpToPx(context, marginDp)
        
        val layoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams
        layoutParams?.setMargins(marginPx, marginPx, marginPx, marginPx)
        view.layoutParams = layoutParams
    }
    
    /**
     * Setup responsive padding for a view
     */
    fun setupResponsivePadding(
        view: View,
        smallPaddingDp: Int = 8,
        mediumPaddingDp: Int = 16,
        largePaddingDp: Int = 24,
        extraLargePaddingDp: Int = 32
    ) {
        val context = view.context
        val paddingDp = getResponsiveDimension(
            context, smallPaddingDp, mediumPaddingDp, largePaddingDp, extraLargePaddingDp
        )
        val paddingPx = dpToPx(context, paddingDp)
        
        view.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
    }
    
    /**
     * Setup responsive text size
     */
    fun setupResponsiveTextSize(
        view: android.widget.TextView,
        smallSizeSp: Float = 12f,
        mediumSizeSp: Float = 14f,
        largeSizeSp: Float = 16f,
        extraLargeSizeSp: Float = 18f
    ) {
        val context = view.context
        val textSizeSp = when (getScreenSize(context)) {
            ScreenSize.SMALL -> smallSizeSp
            ScreenSize.MEDIUM -> mediumSizeSp
            ScreenSize.LARGE -> largeSizeSp
            ScreenSize.EXTRA_LARGE -> extraLargeSizeSp
        }
        
        view.textSize = textSizeSp
    }
    
    /**
     * Setup responsive layout for dashboard cards
     */
    fun setupResponsiveDashboardLayout(
        context: Context,
        cardContainer: LinearLayout
    ) {
        val orientation = getScreenOrientation(context)
        val screenSize = getScreenSize(context)
        
        // Adjust orientation based on screen size and orientation
        when {
            orientation == ScreenOrientation.LANDSCAPE -> {
                // In landscape, use horizontal layout for better space utilization
                cardContainer.orientation = LinearLayout.HORIZONTAL
            }
            screenSize == ScreenSize.SMALL -> {
                // On small screens, use vertical layout to prevent cramping
                cardContainer.orientation = LinearLayout.VERTICAL
            }
            else -> {
                // Default horizontal layout for medium and large screens
                cardContainer.orientation = LinearLayout.HORIZONTAL
            }
        }
    }
    
    /**
     * Setup responsive button layout
     */
    fun setupResponsiveButtonLayout(
        context: Context,
        buttonContainer: LinearLayout
    ) {
        val screenSize = getScreenSize(context)
        val orientation = getScreenOrientation(context)
        
        when {
            screenSize == ScreenSize.SMALL || orientation == ScreenOrientation.LANDSCAPE -> {
                // Stack buttons vertically on small screens or in landscape
                buttonContainer.orientation = LinearLayout.VERTICAL
            }
            else -> {
                // Use horizontal layout for larger screens in portrait
                buttonContainer.orientation = LinearLayout.HORIZONTAL
            }
        }
    }
    
    /**
     * Setup responsive form layout
     */
    fun setupResponsiveFormLayout(
        context: Context,
        formContainer: ViewGroup
    ) {
        val screenSize = getScreenSize(context)
        val orientation = getScreenOrientation(context)
        
        // Adjust form width based on screen size
        val formWidthDp = when {
            screenSize == ScreenSize.EXTRA_LARGE && orientation == ScreenOrientation.LANDSCAPE -> 600
            screenSize == ScreenSize.LARGE && orientation == ScreenOrientation.LANDSCAPE -> 500
            orientation == ScreenOrientation.LANDSCAPE -> 400
            else -> ViewGroup.LayoutParams.MATCH_PARENT
        }
        
        if (formWidthDp != ViewGroup.LayoutParams.MATCH_PARENT) {
            val formWidthPx = dpToPx(context, formWidthDp)
            val layoutParams = formContainer.layoutParams
            layoutParams.width = formWidthPx
            formContainer.layoutParams = layoutParams
        }
    }
    
    /**
     * Setup responsive image size
     */
    fun setupResponsiveImageSize(
        view: android.widget.ImageView,
        smallSizeDp: Int = 32,
        mediumSizeDp: Int = 40,
        largeSizeDp: Int = 48,
        extraLargeSizeDp: Int = 56
    ) {
        val context = view.context
        val sizeDp = getResponsiveDimension(
            context, smallSizeDp, mediumSizeDp, largeSizeDp, extraLargeSizeDp
        )
        val sizePx = dpToPx(context, sizeDp)
        
        val layoutParams = view.layoutParams
        layoutParams.width = sizePx
        layoutParams.height = sizePx
        view.layoutParams = layoutParams
    }
    
    /**
     * Setup responsive card layout
     */
    fun setupResponsiveCardLayout(
        context: Context,
        cardView: androidx.cardview.widget.CardView
    ) {
        val screenSize = getScreenSize(context)
        val orientation = getScreenOrientation(context)
        
        // Adjust card elevation and corner radius based on screen size
        val elevation = when (screenSize) {
            ScreenSize.SMALL -> 2f
            ScreenSize.MEDIUM -> 4f
            ScreenSize.LARGE -> 6f
            ScreenSize.EXTRA_LARGE -> 8f
        }
        
        val cornerRadius = when (screenSize) {
            ScreenSize.SMALL -> 8f
            ScreenSize.MEDIUM -> 12f
            ScreenSize.LARGE -> 16f
            ScreenSize.EXTRA_LARGE -> 20f
        }
        
        cardView.cardElevation = dpToPx(context, elevation.toInt()).toFloat()
        cardView.radius = dpToPx(context, cornerRadius.toInt()).toFloat()
    }
    
    /**
     * Setup responsive constraint layout
     */
    fun setupResponsiveConstraintLayout(
        context: Context,
        constraintLayout: ConstraintLayout,
        viewIds: List<Int>
    ) {
        val screenSize = getScreenSize(context)
        val orientation = getScreenOrientation(context)
        
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        
        // Adjust constraints based on screen size and orientation
        when {
            orientation == ScreenOrientation.LANDSCAPE -> {
                // In landscape, create more horizontal layouts
                setupLandscapeConstraints(constraintSet, viewIds)
            }
            screenSize == ScreenSize.SMALL -> {
                // On small screens, create more vertical layouts
                setupSmallScreenConstraints(constraintSet, viewIds)
            }
            else -> {
                // Default portrait layout for medium and large screens
                setupDefaultConstraints(constraintSet, viewIds)
            }
        }
        
        constraintSet.applyTo(constraintLayout)
    }
    
    private fun setupLandscapeConstraints(constraintSet: ConstraintSet, viewIds: List<Int>) {
        // Implementation for landscape constraints
        // This would depend on specific layout requirements
    }
    
    private fun setupSmallScreenConstraints(constraintSet: ConstraintSet, viewIds: List<Int>) {
        // Implementation for small screen constraints
        // This would depend on specific layout requirements
    }
    
    private fun setupDefaultConstraints(constraintSet: ConstraintSet, viewIds: List<Int>) {
        // Implementation for default constraints
        // This would depend on specific layout requirements
    }
    
    /**
     * Get responsive column count for grid layouts
     */
    fun getResponsiveColumnCount(context: Context): Int {
        val screenSize = getScreenSize(context)
        val orientation = getScreenOrientation(context)
        
        return when {
            orientation == ScreenOrientation.LANDSCAPE -> {
                when (screenSize) {
                    ScreenSize.SMALL -> 2
                    ScreenSize.MEDIUM -> 3
                    ScreenSize.LARGE -> 4
                    ScreenSize.EXTRA_LARGE -> 5
                }
            }
            else -> {
                when (screenSize) {
                    ScreenSize.SMALL -> 1
                    ScreenSize.MEDIUM -> 2
                    ScreenSize.LARGE -> 2
                    ScreenSize.EXTRA_LARGE -> 3
                }
            }
        }
    }
    
    /**
     * Check if device is in tablet mode
     */
    fun isTablet(context: Context): Boolean {
        return getScreenSize(context) in listOf(ScreenSize.LARGE, ScreenSize.EXTRA_LARGE)
    }
    
    /**
     * Get responsive FAB margin
     */
    fun getResponsiveFabMargin(context: Context): Int {
        return getResponsiveDimension(context, 12, 16, 20, 24)
    }
    
    /**
     * Get responsive dialog width
     */
    fun getResponsiveDialogWidth(context: Context): Int {
        val screenSize = getScreenSize(context)
        val displayMetrics = context.resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels
        
        return when (screenSize) {
            ScreenSize.SMALL -> (screenWidthPx * 0.9).toInt()
            ScreenSize.MEDIUM -> (screenWidthPx * 0.8).toInt()
            ScreenSize.LARGE -> (screenWidthPx * 0.7).toInt()
            ScreenSize.EXTRA_LARGE -> dpToPx(context, 600)
        }
    }
}