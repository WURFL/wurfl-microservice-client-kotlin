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
    val importantHeaders: Array<String>,
    @SerializedName("static_caps")
    val staticCaps: Array<String>,
    @SerializedName("virtual_caps")
    val virtualCaps: Array<String>,
    @SerializedName("ltime")
    val ltime: String
)

/**
 * WmException is a general purpose exception thrown whenever an unrecoverable error occurs during device detection (ie: no connection available to WM server,
 * wrong url or port configurations, etc.
 */
class WmException(message: String) : Exception(message)