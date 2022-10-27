package com.example.gps_foreground_test

import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface POCApi {

    @POST("/api/AP/AddPOCLocation")
    fun postPOC(@Body body: RequestBody): Call<POCResponse>

}

data class POCResponse(
    @SerializedName("rtnCode")
    val rtnCode: Int = 0,

    @SerializedName("rtnMsg")
    val rtnMsg: String = "",

    @SerializedName("data")
    var data: Int? = 0
)