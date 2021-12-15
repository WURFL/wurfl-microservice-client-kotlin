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

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking


private const val DEVICE_ID_CACHE_TYPE = "dId-cache"
private const val HEADERS_CACHE_TYPE = "head-cache"

// Timeouts in milliseconds
private const val DEFAULT_CONN_TIMEOUT: Int = 10000;
private const val DEFAULT_RW_TIMEOUT: Int = 60000;

class WmClient internal constructor(
    private val scheme: String,
    private val host: String,
    private val port: String,
    private val baseURI: String,
) {

    companion object {
        fun create(scheme: String, host: String, port: String, baseURI: String?): WmClient {
            val uri = baseURI ?: ""
            val wmclient = WmClient(scheme, host, port, uri)
            wmclient.internalClient = HttpClient(Apache) {
                engine {
                    connectTimeout = DEFAULT_CONN_TIMEOUT
                    socketTimeout = DEFAULT_RW_TIMEOUT
                    customizeClient {
                        setMaxConnPerRoute(100)
                    }
                }
                install(JsonFeature) {
                    serializer = GsonSerializer()
                }
            }
            val info = wmclient.getInfo()
            wmclient.staticCaps = info.staticCaps.sortedArray()
            wmclient.virtualCaps = info.virtualCaps.sortedArray()
            wmclient.importantHeaders = info.importantHeaders
            return wmclient
        }
    }

    // These are the lists of all static or virtual that can be returned by the running wm server
    private lateinit var staticCaps: Array<String>
    private lateinit var virtualCaps: Array<String>

    // Requested are used in the lookup requests
    private lateinit var requestedStaticCaps: Array<String>
    private lateinit var requestedVirtualCaps: Array<String>

    private lateinit var importantHeaders: Array<String>

    // Time of last WURFL.xml file load on server
    private var ltime: String = ""

    private lateinit var internalClient: HttpClient

    private fun createUrl(path: String): String {
        var basePath = "$scheme://$host:$port/"
        if (baseURI.isNotEmpty()) {
            basePath += "$baseURI/"
        }
        return "$basePath/$path"
    }

    @Throws(WmException::class)
    fun getInfo(): JSONInfoData {
        return runBlocking {
            return@runBlocking internalClient.get<JSONInfoData>(urlString = createUrl("/v2/getinfo/json"))
        }
    }

    /*
    private fun checkData(info: JSONInfoData): Boolean {
        // If these are empty there's something wrong, like server returning a json error message or a different data format
        return (info.isNotEmpty(info.getWmVersion()) && StringUtils.isNotEmpty(info.getWurflApiVersion()) && StringUtils.isNotEmpty(
            info.getWurflInfo())
                && (ArrayUtils.isNotEmpty(info.getStaticCaps()) || ArrayUtils.isNotEmpty(info.getVirtualCaps())))
    }*/
}
