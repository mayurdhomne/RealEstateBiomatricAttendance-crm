package com.crm.realestate.data.models

/**
 * Data layer result class for network/repository operations
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String? = null) : Result<Nothing>()
    data class Loading(val loading: Boolean = true, val loadingMessage: String? = null) : Result<Nothing>()
    
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    fun isLoading(): Boolean = this is Loading
    
    fun getDataOrNull(): T? = (this as? Success)?.data
    fun getErrorOrNull(): Throwable? = (this as? Error)?.exception
}
