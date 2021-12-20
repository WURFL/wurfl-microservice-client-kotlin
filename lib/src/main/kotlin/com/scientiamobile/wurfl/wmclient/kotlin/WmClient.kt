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
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.io.IOException


private const val DEVICE_ID_CACHE_TYPE = "dId-cache"
private const val HEADERS_CACHE_TYPE = "head-cache"

// Timeouts in milliseconds
private const val DEFAULT_CONN_TIMEOUT: Int = 10000
private const val DEFAULT_RW_TIMEOUT: Int = 60000

class WmClient private constructor(
    private val scheme: String,
    private val host: String,
    private val port: String,
    private val baseURI: String,
) {

    companion object {
        fun create(scheme: String, host: String, port: String, baseURI: String?): WmClient {
            val uri = baseURI ?: ""
            if (scheme.isEmpty()) {
                throw WmException("WM client scheme cannot be empty")
            }

            val wmclient = WmClient(scheme, host, port, uri)
            if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
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
            } else {
                throw WmException("Invalid connection scheme specified:  [ $scheme ]")
            }

            try {
                val info = wmclient.getInfo()
                wmclient.staticCaps = info.staticCaps.sortedArray()
                wmclient.virtualCaps = info.virtualCaps.sortedArray()
                wmclient.importantHeaders = info.importantHeaders
                return wmclient
            } catch (e: Exception){
                throw WmException("Unable to create wm client: ${e.message}")
            }
        }
    }

    // These are the lists of all static or virtual that can be returned by the running wm server
    private lateinit var staticCaps: Array<String>
    private lateinit var virtualCaps: Array<String>

    // Requested are used in the lookup requests
    private var requestedStaticCaps: Array<String>? = null
    private var requestedVirtualCaps: Array<String>? = null

    private lateinit var importantHeaders: Array<String>

    // Time of last WURFL.xml file load on server
    private var ltime: String = ""

    // Internal caches
    // Maps device ID -> JSONDeviceData
    private var devIDCache: LRUCache<String, JSONDeviceData>? = null
    // Maps concat headers (mainly UA) -> JSONDeviceData
    private var headersCache : LRUCache<String, JSONDeviceData>? = null

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
        val info = runBlocking {
            return@runBlocking internalClient.get<JSONInfoData>(urlString = createUrl("/v2/getinfo/json"))
        }
        if (!(checkData(info))) {
            throw WmException("Server returned empty data or a wrong json format")
        }
        return info
    }

    @Throws(WmException::class)
    fun lookupUseragent(useragent: String): JSONDeviceData {
        val headers: MutableMap<String, String> = HashMap()
        headers["User-Agent"] = useragent
        val request = Request(headers,
            requestedStaticCaps, requestedVirtualCaps, "")
        return internalRequest("/v2/lookupuseragent/json", request, HEADERS_CACHE_TYPE)
    }

    /**
     * Returns the device matching the given WURFL ID
     *
     * @param wurflId a WURFL device identifier
     * @return An object containing the device capabilities
     * @throws WmException In case any error occurs
     */
    @Throws(WmException::class)
    fun lookupDeviceId(wurflId: String): JSONDeviceData {
        val request = Request(emptyMap(),
            requestedStaticCaps, requestedVirtualCaps, wurflId)
        return internalRequest("/v2/lookupdeviceid/json", request, DEVICE_ID_CACHE_TYPE)
    }

    fun setCacheSize(uaMaxEntries: Int) {
        this.headersCache = LRUCache(uaMaxEntries)
        devIDCache = LRUCache() // this has the default cache size
    }

    fun getActualCacheSizes(): Pair<Int,Int> {

        var dIdCacheSize = 0
        if (devIDCache != null) {
            dIdCacheSize = devIDCache!!.size()
        }
        var headerCacheSize = 0
        if (headersCache != null) {
            headerCacheSize = headersCache!!.size()
        }

        return Pair(dIdCacheSize, headerCacheSize)
    }

    private fun checkData(info: JSONInfoData): Boolean {
        // If these are empty there's something wrong, like server returning a json error message or a different data format
        return (info.wmVersion.isNotEmpty() && info.wurflApiVersion.isNotEmpty() && info.wurflInfo.isNotEmpty()
                && (info.staticCaps.isNotEmpty()) || info.virtualCaps.isNotEmpty())
    }

    @Throws(WmException::class)
    private fun internalRequest(path: String, request: Request, cacheType: String): JSONDeviceData {
        var device: JSONDeviceData?
        var cacheKey = ""

        try {

        if (DEVICE_ID_CACHE_TYPE == cacheType) {
            cacheKey = request.wurflId
        } else if (HEADERS_CACHE_TYPE == cacheType) {
            cacheKey = this.getHeadersCacheKey(request.lookupHeaders, cacheType)
        }

        // First, do a cache lookup
        if (cacheType.isNotEmpty() && cacheKey.isNotEmpty()) {
            if (cacheType == DEVICE_ID_CACHE_TYPE && devIDCache != null) {
                device = request.wurflId.let { devIDCache!!.getEntry(it) }
                if (device != null) {
                    return device
                }
            } else if (cacheType == HEADERS_CACHE_TYPE && headersCache != null) {
                device = headersCache!!.getEntry(cacheKey)
                if (device != null) {
                    return device
                }
            }
        }

        device = runBlocking {
            internalClient.post<JSONDeviceData>(createUrl(path)) {
                contentType(ContentType.Application.Json)
                body = request
            }
        }

            if (device.error.isNotEmpty()) {
                throw WmException("Unable to complete request to WM server:  $device.error")
            }

            // Check if caches must be cleared before adding a new device
            clearCachesIfNeeded(device.ltime)
            if (cacheType == HEADERS_CACHE_TYPE && devIDCache != null && "" != cacheKey) {
                headersCache?.putEntry(cacheKey, device)
            } else if (cacheType == DEVICE_ID_CACHE_TYPE && headersCache != null && "" != cacheKey) {
                devIDCache?.putEntry(cacheKey, device)
            }
            return device
        } catch (e: Exception) {
            throw WmException("Unable to complete request to WM server: " + e.message)
        }
    }

    private fun getHeadersCacheKey(headers: Map<String, String>, cacheType: String): String {
        var key = ""

        if (headers.isEmpty() && HEADERS_CACHE_TYPE == cacheType) {
            throw WmException("You must provide at least on headers (the 'User-Agent')")
        }

        // Using important headers array preserves header name order

        // Using important headers array preserves header name order
        for (h in importantHeaders) {
            val hval = headers[h]
            if (hval != null) {
                key += hval
            }
        }
        return key
    }

    private fun clearCachesIfNeeded(ltime: String?) {
        if (ltime != null && ltime != this.ltime) {
            this.ltime = ltime
            clearCaches()
        }
    }

    private fun clearCaches() {
        headersCache?.clear()
        devIDCache?.clear()

        // TODO: uncomment when introducing WM data enumerators
        /*
        makeModels = arrayOfNulls<JSONMakeModel>(0)
        deviceMakes = arrayOfNulls<String>(0)
        deviceMakesMap = HashMap<String, List<JSONModelMktName>>()
        synchronized(deviceOSesLock) {
            deviceOSes = arrayOfNulls<String>(0)
            deviceOsVersionsMap = HashMap<String, List<String>>()
        }*/
    }

    fun setRequestedStaticCapabilities(capsList: Array<String>?) {
        if (capsList == null) {
            requestedStaticCaps = null
            this.clearCaches()
            return
        }
        val stCaps: MutableList<String> = ArrayList()
        for (name in capsList) {
            if (hasStaticCapability(name)) {
                stCaps.add(name)
            }
        }
        requestedStaticCaps = stCaps.toTypedArray()
        clearCaches()
    }

    fun setRequestedVirtualCapabilities(vcapsList: Array<String>?) {
        if (vcapsList == null) {
            requestedVirtualCaps = null
            this.clearCaches()
            return
        }
        val vCaps: MutableList<String> = ArrayList()
        for (name in vcapsList) {
            if (hasVirtualCapability(name)) {
                vCaps.add(name)
            }
        }
        requestedVirtualCaps = vCaps.toTypedArray()
        clearCaches()
    }

    /**
     * @param capName capability name
     * @return true if the given static capability is handled by this client, false otherwise
     */
    fun hasStaticCapability(capName: String?): Boolean {
        return staticCaps.contains(capName)
    }

    /**
     * @param capName capability name
     * @return true if the given virtual capability is handled by this client, false otherwise
     */
    fun hasVirtualCapability(capName: String?): Boolean {
        return virtualCaps.contains(capName)
    }

    fun setRequestedCapabilities(capsList: Array<String>?) {
        val capNames: MutableList<String> = ArrayList()
        val vcapNames: MutableList<String> = ArrayList()
        if (capsList == null){
            requestedStaticCaps = null
            requestedVirtualCaps = null
            return
        }

        for (name in capsList) {
            if (hasStaticCapability(name)) {
                capNames.add(name)
            } else if (hasVirtualCapability(name)) {
                vcapNames.add(name)
            }
        }
        if (capsList.isNotEmpty()) {
            requestedStaticCaps = capNames.toTypedArray()
        }
        if (vcapNames.isNotEmpty()) {
            requestedVirtualCaps = vcapNames.toTypedArray()
        }
        clearCaches()
    }

    /**
     * Deallocates all resources used by client. All subsequent usage of client will result in a WmException (you need to create the client again
     * with a call to WmClient.create().
     *
     * @throws WmException In case of closing connection errors.
     */
    @Throws(WmException::class)
    fun destroy() {
        try {
            clearCaches()
            headersCache = null
            devIDCache = null
            /*
            makeModels = null
            deviceMakesMap = null
            deviceMakes = null
            deviceOsVersionsMap = null
            deviceOSes = null
            */
            internalClient.close()
        } catch (e: IOException) {
            throw WmException("Unable to close client: " + e.message)
        }
    }
}
