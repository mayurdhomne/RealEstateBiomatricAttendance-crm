package com.crm.realestate.data.api

import com.crm.realestate.data.api.dto.NetworkLoginResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Retrofit API service interface for authentication endpoints
 */
interface AuthApiService {
    
    /**
     * Login endpoint for employee authentication
     * @param username Employee username provided by admin
     * @param password Employee password provided by admin
     * @return LoginResponse containing token and employee info
     */
    @FormUrlEncoded
    @POST("login/")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<NetworkLoginResponse>
}