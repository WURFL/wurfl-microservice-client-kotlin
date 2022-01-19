/*
Copyright 2022 ScientiaMobile Inc. http://www.scientiamobile.com
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.scientiamobile.wurfl.wmclient.kotlin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JSONInfoData(
    @SerialName("wurfl_api_version")
    val wurflApiVersion: String,
    @SerialName("wm_version")
    val wmVersion: String,
    @SerialName("wurfl_info")
    val wurflInfo: String,
    @SerialName("important_headers")
    val importantHeaders: Array<String>,
    @SerialName("static_caps")
    val staticCaps: Array<String>,
    @SerialName("virtual_caps")
    val virtualCaps: Array<String>,
    @SerialName("ltime")
    val ltime: String,
)

/**
 * Holds the detected device data received from wm server.
 */
@Serializable
data class JSONDeviceData(
    @SerialName("capabilities")
    val capabilities: Map<String, String>? = null,
    @SerialName("error")
    val error: String,
    @SerialName("mtime")
    val mtime: Int,
    @SerialName("ltime")
    val ltime: String? = null,

    val apiVersion: String
)

/**
 * Holds data relevant for the HTTP request that will be sent to wm server
 */
@Serializable
data class Request(
    @SerialName("lookup_headers")
    val lookupHeaders: Map<String, String>,
    @SerialName("requested_caps")
    val requestedCaps: Array<String>?,
    @SerialName("requested_vcaps")
    val requestedVcaps: Array<String>?,

    val wurflId: String,
)
@Serializable
data class JSONDeviceOsVersions(
    @SerialName("device_os")
    var osName: String,
    @SerialName("device_os_version")
    var osVersion: String = "",
)

@Serializable
data class JSONMakeModel(
    @SerialName("brand_name")
    var brandName: String = "",
    @SerialName("model_name")
    var modelName: String = "",
    @SerialName("marketing_name")
    var marketingName: String = "",
)

@Serializable
data class JSONModelMktName(
    @SerialName("model_name")
    var modelName: String,
    @SerialName("marketing_name")
    var marketingName: String,
)

/**
 * WmException is a general purpose exception thrown whenever an unrecoverable error occurs during device detection (ie: no connection available to WM server,
 * wrong url or port configurations, etc.
 */
class WmException(message: String) : Exception(message)