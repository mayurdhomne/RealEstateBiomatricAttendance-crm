package com.crm.realestate.data.models

import com.google.gson.annotations.SerializedName

/**
 * Simple API response with only a detail message
 * Used for attendance endpoints that return {"detail": "message"}
 */
data class DetailResponse(
    @SerializedName("detail")
    val detail: String
)
