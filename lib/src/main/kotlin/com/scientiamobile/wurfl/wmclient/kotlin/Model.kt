package com.scientiamobile.wurfl.wmclient.kotlin

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class JSONInfoData(
    @SerializedName("wurfl_api_version")
    val wurflApiVersion: String,
    @SerializedName("wm_version")
    val wmVersion: String,
    @SerializedName("wurfl_info")
    val wurflInfo: String,
    @SerializedName("important_headers")
    var importantHeaders: Array<String>,
    @SerializedName("static_caps")
    var staticCaps: Array<String>,
    @SerializedName("virtual_caps")
    var virtualCaps: Array<String>,
    @SerializedName("ltime")
    var ltime: String
)