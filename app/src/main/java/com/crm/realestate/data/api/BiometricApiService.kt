package com.crm.realestate.data.api

import com.crm.realestate.data.models.BiometricRegistrationRequest
import com.crm.realestate.data.models.BiometricRegistrationResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API service interface for biometric registration endpoints
 */
interface BiometricApiService {
    
    /**
     * Register employee biometrics (face and/or fingerprint)
     * @param request BiometricRegistrationRequest containing employee_id and registration status
     * @return BiometricRegistrationResponse with success status and message
     */
    @POST("biometric/register/")
    suspend fun registerBiometrics(
        @Body request: BiometricRegistrationRequest
    ): Response<BiometricRegistrationResponse>
}