package com.example.gps_foreground_test

import com.google.gson.annotations.SerializedName

import android.os.Parcelable

data class AddPOC(

    @SerializedName("deviceID")
    val deviceID: String = "",

    @SerializedName("latitude")
    val latitude: Double? = 0.0,

    @SerializedName("longitude")
    var longitude: Double? = 0.0
)

/*
    companion object {
        const val LANGUAGE_EN = "en"
    }
*/