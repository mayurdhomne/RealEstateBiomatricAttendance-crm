package com.crm.realestate.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * Utility class for consistent animations throughout the app
 * Provides smooth transitions and professional UI feedback
 */
object AnimationUtils {
    
    // Animation durations
    const val DURATION_SHORT = 150L
    const val DURATION_MEDIUM = 300L
    const val DURATION_LONG = 500L
    
    // Animation interpolators
    private val fastOutSlowIn = FastOutSlowInInterpolator()
    private val decelerate = DecelerateInterpolator()
    private val accelerateDecelerate = AccelerateDecelerateInterpolator()
    private val overshoot = OvershootInterpolator(1.2f)
    
    /**
     * Fade in animation
     */
    fun fadeIn(
        view: View,
        duration: Long = DURATION_MEDIUM,
        startDelay: Long = 0,
        onComplete: (() -> Unit)? = null
    ) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(decelerate)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onComplete?.invoke()
                }
            })
            .start()
    }
    
    /**
     * Fade out animation
     */
    fun fadeOut(
        view: View,
        duration: Long = DURATION_MEDIUM,
        startDelay: Long = 0,
        hideOnComplete: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(decelerate)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (hideOnComplete) {
                        view.visibility = View.GONE
                    }
                    onComplete?.invoke()
                }
            })
            .start()
    }
    
    /**
     * Scale up animation (for buttons, cards)
     */
    fun scaleUp(
        view: View,
        duration: Long = DURATION_SHORT,
        fromScale: Float = 0.8f,
        toScale: Float = 1f,
        onComplete: (() -> Unit)? = null
    ) {
        view.scaleX = fromScale
        view.scaleY = fromScale
        view.visibility = View.VISIBLE
        
        val animatorSet = AnimatorSet()
        val scaleXAnimator = ObjectAnimator.ofFloat(view, "scaleX", fromScale, toScale)
        val scaleYAnimator = ObjectAnimator.ofFloat(view, "scaleY", fromScale, toScale)
        val alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        
        animatorSet.playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
        animatorSet.duration = duration
        animatorSet.interpolator = overshoot
        animatorSet.doOnEnd { onComplete?.invoke() }
        animatorSet.start()
    }
    
    /**
     * Scale down animation
     */
    fun scaleDown(
        view: View,
        duration: Long = DURATION_SHORT,
        fromScale: Float = 1f,
        toScale: Float = 0.8f,
        hideOnComplete: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        val animatorSet = AnimatorSet()
        val scaleXAnimator = ObjectAnimator.ofFloat(view, "scaleX", fromScale, toScale)
        val scaleYAnimator = ObjectAnimator.ofFloat(view, "scaleY", fromScale, toScale)
        val alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
        
        animatorSet.playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
        animatorSet.duration = duration
        animatorSet.interpolator = accelerateDecelerate
        animatorSet.doOnEnd {
            if (hideOnComplete) {
                view.visibility = View.GONE
            }
            onComplete?.invoke()
        }
        animatorSet.start()
    }
    
    /**
     * Slide in from right animation
     */
    fun slideInFromRight(
        view: View,
        duration: Long = DURATION_MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        view.translationX = view.width.toFloat()
        view.alpha = 0f
        view.visibility = View.VISIBLE
        
        val animatorSet = AnimatorSet()
        val translateAnimator = ObjectAnimator.ofFloat(view, "translationX", view.width.toFloat(), 0f)
        val alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        
        animatorSet.playTogether(translateAnimator, alphaAnimator)
        animatorSet.duration = duration
        animatorSet.interpolator = fastOutSlowIn
        animatorSet.doOnEnd { onComplete?.invoke() }
        animatorSet.start()
    }
    
    /**
     * Slide out to left animation
     */
    fun slideOutToLeft(
        view: View,
        duration: Long = DURATION_MEDIUM,
        hideOnComplete: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        val animatorSet = AnimatorSet()
        val translateAnimator = ObjectAnimator.ofFloat(view, "translationX", 0f, -view.width.toFloat())
        val alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
        
        animatorSet.playTogether(translateAnimator, alphaAnimator)
        animatorSet.duration = duration
        animatorSet.interpolator = fastOutSlowIn
        animatorSet.doOnEnd {
            if (hideOnComplete) {
                view.visibility = View.GONE
            }
            onComplete?.invoke()
        }
        animatorSet.start()
    }
    
    /**
     * Slide up animation (for bottom sheets, cards)
     */
    fun slideUp(
        view: View,
        duration: Long = DURATION_MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        view.translationY = view.height.toFloat()
        view.alpha = 0f
        view.visibility = View.VISIBLE
        
        val animatorSet = AnimatorSet()
        val translateAnimator = ObjectAnimator.ofFloat(view, "translationY", view.height.toFloat(), 0f)
        val alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        
        animatorSet.playTogether(translateAnimator, alphaAnimator)
        animatorSet.duration = duration
        animatorSet.interpolator = fastOutSlowIn
        animatorSet.doOnEnd { onComplete?.invoke() }
        animatorSet.start()
    }
    
    /**
     * Slide down animation
     */
    fun slideDown(
        view: View,
        duration: Long = DURATION_MEDIUM,
        hideOnComplete: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        val animatorSet = AnimatorSet()
        val translateAnimator = ObjectAnimator.ofFloat(view, "translationY", 0f, view.height.toFloat())
        val alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
        
        animatorSet.playTogether(translateAnimator, alphaAnimator)
        animatorSet.duration = duration
        animatorSet.interpolator = fastOutSlowIn
        animatorSet.doOnEnd {
            if (hideOnComplete) {
                view.visibility = View.GONE
            }
            onComplete?.invoke()
        }
        animatorSet.start()
    }
    
    /**
     * Bounce animation for success feedback
     */
    fun bounce(
        view: View,
        duration: Long = DURATION_MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        val animatorSet = AnimatorSet()
        val scaleXAnimator = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.2f, 1f)
        val scaleYAnimator = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.2f, 1f)
        
        animatorSet.playTogether(scaleXAnimator, scaleYAnimator)
        animatorSet.duration = duration
        animatorSet.interpolator = overshoot
        animatorSet.doOnEnd { onComplete?.invoke() }
        animatorSet.start()
    }
    
    /**
     * Shake animation for error feedback
     */
    fun shake(
        view: View,
        duration: Long = DURATION_MEDIUM,
        intensity: Float = 10f,
        onComplete: (() -> Unit)? = null
    ) {
        val animator = ObjectAnimator.ofFloat(view, "translationX", 0f, intensity, -intensity, intensity, -intensity, 0f)
        animator.duration = duration
        animator.interpolator = accelerateDecelerate
        animator.doOnEnd { onComplete?.invoke() }
        animator.start()
    }
    
    /**
     * Pulse animation for attention
     */
    fun pulse(
        view: View,
        duration: Long = DURATION_LONG,
        minAlpha: Float = 0.5f,
        maxAlpha: Float = 1f,
        repeatCount: Int = ValueAnimator.INFINITE
    ): ValueAnimator {
        val animator = ObjectAnimator.ofFloat(view, "alpha", minAlpha, maxAlpha, minAlpha)
        animator.duration = duration
        animator.repeatCount = repeatCount
        animator.interpolator = accelerateDecelerate
        animator.start()
        return animator
    }
    
    /**
     * Rotate animation
     */
    fun rotate(
        view: View,
        fromDegrees: Float = 0f,
        toDegrees: Float = 360f,
        duration: Long = DURATION_MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        val animator = ObjectAnimator.ofFloat(view, "rotation", fromDegrees, toDegrees)
        animator.duration = duration
        animator.interpolator = accelerateDecelerate
        animator.doOnEnd { onComplete?.invoke() }
        animator.start()
    }
    
    /**
     * Cross fade between two views
     */
    fun crossFade(
        viewOut: View,
        viewIn: View,
        duration: Long = DURATION_MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        viewIn.alpha = 0f
        viewIn.visibility = View.VISIBLE
        
        val animatorSet = AnimatorSet()
        val fadeOutAnimator = ObjectAnimator.ofFloat(viewOut, "alpha", 1f, 0f)
        val fadeInAnimator = ObjectAnimator.ofFloat(viewIn, "alpha", 0f, 1f)
        
        animatorSet.playTogether(fadeOutAnimator, fadeInAnimator)
        animatorSet.duration = duration
        animatorSet.interpolator = fastOutSlowIn
        animatorSet.doOnEnd {
            viewOut.visibility = View.GONE
            onComplete?.invoke()
        }
        animatorSet.start()
    }
    
    /**
     * Staggered animation for list items
     */
    fun staggeredFadeIn(
        views: List<View>,
        duration: Long = DURATION_MEDIUM,
        staggerDelay: Long = 50L,
        onComplete: (() -> Unit)? = null
    ) {
        views.forEachIndexed { index, view ->
            val delay = index * staggerDelay
            val isLast = index == views.size - 1
            
            fadeIn(
                view = view,
                duration = duration,
                startDelay = delay,
                onComplete = if (isLast) onComplete else null
            )
        }
    }
    
    /**
     * Success animation with scale and color change
     */
    fun successAnimation(
        view: View,
        context: Context,
        duration: Long = DURATION_MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        // Scale up with bounce
        scaleUp(view, duration, 0.9f, 1.1f) {
            // Scale back to normal
            scaleUp(view, DURATION_SHORT, 1.1f, 1f, onComplete)
        }
    }
    
    /**
     * Error animation with shake and color change
     */
    fun errorAnimation(
        view: View,
        context: Context,
        duration: Long = DURATION_MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        shake(view, duration, 15f, onComplete)
    }
    
    /**
     * Loading animation for buttons
     */
    fun buttonLoadingAnimation(
        view: View,
        isLoading: Boolean,
        duration: Long = DURATION_SHORT
    ) {
        if (isLoading) {
            view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .alpha(0.7f)
                .setDuration(duration)
                .setInterpolator(decelerate)
                .start()
        } else {
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(decelerate)
                .start()
        }
    }
    
    /**
     * Card press animation
     */
    fun cardPressAnimation(view: View, isPressed: Boolean) {
        val scale = if (isPressed) 0.98f else 1f
        val elevation = if (isPressed) 8f else 4f
        
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .translationZ(elevation)
            .setDuration(DURATION_SHORT)
            .setInterpolator(decelerate)
            .start()
    }
}