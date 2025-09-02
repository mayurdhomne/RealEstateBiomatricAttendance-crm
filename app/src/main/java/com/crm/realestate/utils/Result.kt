package com.crm.realestate.utils

import android.content.Context

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val appError: ErrorHandler.AppError? = null) : Result<Nothing>()
    data class Loading(val loading: Boolean = true, val message: String = "Loading...") : Result<Nothing>()
    
    inline fun <R> map(transform: (value: T) -> R): Result<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> Error(exception, appError)
            is Loading -> Loading(loading, message)
        }
    }
    
    inline fun onSuccess(action: (value: T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (exception: Throwable, appError: ErrorHandler.AppError?) -> Unit): Result<T> {
        if (this is Error) action(exception, appError)
        return this
    }
    
    inline fun onLoading(action: (isLoading: Boolean, message: String) -> Unit): Result<T> {
        if (this is Loading) action(loading, message)
        return this
    }
    
    /**
     * Convert exception to user-friendly error
     */
    fun toAppError(context: Context): ErrorHandler.AppError {
        return when (this) {
            is Error -> appError ?: ErrorHandler.handleException(context, exception)
            else -> ErrorHandler.AppError.UnknownError("Invalid state")
        }
    }
    
    /**
     * Check if result is successful
     */
    fun isSuccess(): Boolean = this is Success
    
    /**
     * Check if result is error
     */
    fun isError(): Boolean = this is Error
    
    /**
     * Check if result is loading
     */
    fun isLoading(): Boolean = this is Loading
    
    /**
     * Get data or null
     */
    fun getDataOrNull(): T? = (this as? Success)?.data
    
    /**
     * Get error or null
     */
    fun getErrorOrNull(): Throwable? = (this as? Error)?.exception
    
    /**
     * Get app error or null
     */
    fun getAppErrorOrNull(): ErrorHandler.AppError? = (this as? Error)?.appError
}

/**
 * Extension functions for easier Result handling
 */

/**
 * Create success result
 */
fun <T> T.asSuccess(): Result<T> = Result.Success(this)

/**
 * Create error result with exception
 */
fun Throwable.asError(): Result<Nothing> = Result.Error(this)

/**
 * Create error result with app error
 */
fun ErrorHandler.AppError.asError(): Result<Nothing> = Result.Error(RuntimeException(this.message), this)

/**
 * Create loading result
 */
fun String.asLoading(): Result<Nothing> = Result.Loading(true, this)

/**
 * Handle result with comprehensive error handling
 */
inline fun <T> Result<T>.handle(
    onSuccess: (T) -> Unit,
    onError: (ErrorHandler.AppError) -> Unit,
    noinline onLoading: ((String) -> Unit)? = null,
    context: Context? = null
) {
    when (this) {
        is Result.Success -> onSuccess(data)
        is Result.Error -> {
            val appError = appError ?: context?.let { ErrorHandler.handleException(it, exception) }
                ?: ErrorHandler.AppError.UnknownError(exception.message)
            onError(appError)
        }
        is Result.Loading -> onLoading?.invoke(message)
    }
}