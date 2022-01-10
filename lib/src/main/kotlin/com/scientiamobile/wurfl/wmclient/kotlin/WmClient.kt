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
import io.ktor.request.*
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.*
import javax.servlet.http.HttpServletRequest


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

    private val deviceOSesLock = Any()
    private var deviceOSes = emptyArray<String>()
    private var deviceOsVersionsMap: Map<String, List<String>> = emptyMap()

    // List of device manufacturers
    private var deviceMakes = emptyArray<String>()

    // Lock object used for deviceMakes safety
    private val deviceMakesLock = Any()

    // Map that associates brand name to JSONModelMktName objects
    private var deviceMakesMap: Map<String, List<JSONModelMktName>> = emptyMap()

    private fun createUrl(path: String): String {
        var basePath = "$scheme://$host:$port/"
        if (baseURI.isNotEmpty()) {
            basePath += "$baseURI/"
        }
        return "$basePath/$path"
    }

    /**
     * @return This client API version
     */
    fun getApiVersion(): String {
        return "1.0.0"
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

    /**
     * Performs a device detection using an HTTP request object, as passed from Java Web applications
     *
     * @param ktorRequest an instance of Ktor framework ApplicationRequest object
     * @return An object containing the device capabilities
     * @throws WmException In case any error occurs during device detection
     */
    @Throws(WmException::class)
    fun lookupRequest(ktorRequest: ApplicationRequest): JSONDeviceData {

        val reqHeaders: MutableMap<String, String> = HashMap()
        for (headerName in importantHeaders) {
            val headerValue: String? = ktorRequest.headers[headerName]
            if (headerValue!= null && headerValue.isNotEmpty()) {
                reqHeaders[headerName] = headerValue
            }
        }
        return internalRequest("/v2/lookuprequest/json", Request(reqHeaders, requestedStaticCaps,
            requestedVirtualCaps, ""), HEADERS_CACHE_TYPE)
    }

    @Throws(WmException::class)
    fun lookupRequest(request: HttpServletRequest): JSONDeviceData {

        val reqHeaders: MutableMap<String, String> = HashMap()
        for (headerName in importantHeaders) {
            val headerValue: String? = request.getHeader(headerName)
            if (headerValue!= null && headerValue.isNotEmpty()) {
                reqHeaders[headerName] = headerValue
            }
        }
        return internalRequest("/v2/lookuprequest/json", Request(reqHeaders, requestedStaticCaps,
            requestedVirtualCaps, ""), HEADERS_CACHE_TYPE)
    }

    /**
     * Performs a device detection using an HTTP request object, as passed from Java Web applications
     *
     * @param headers headers map
     * @return An object containing the device capabilities
     * @throws WmException In case any error occurs during device detection
     */
    @Throws(WmException::class)
    fun lookupHeaders(headers: Map<String, String>): JSONDeviceData {

        // creates a map with lowercase keys
        val lowerKeyMap = headers.mapKeys { it.key.lowercase(Locale.getDefault()) }
        val reqHeaders = HashMap<String,String>()
        for (headerName in importantHeaders) {
            val headerValue = lowerKeyMap[headerName.lowercase(Locale.getDefault())]
            if (headerValue != null && headerValue.isNotEmpty()) {
                reqHeaders[headerName] = headerValue
            }
        }
        return internalRequest("/v2/lookuprequest/json",
            Request(reqHeaders, requestedStaticCaps, requestedVirtualCaps, ""), HEADERS_CACHE_TYPE)
    }

    /**
     * @return an array of all devices device_os capabilities in WM server
     * @throws WmException In case a connection error occurs or malformed data are sent
     */
    @Throws(WmException::class)
    fun getAllOSes(): Array<String> {
        loadDeviceOsesData()
        return deviceOSes
    }

    /**
     * returns a slice
     *
     * @param osName a device OS name
     * @return an array containing device_os_version for the given os_name
     * @throws WmException In case a connection error occurs or malformed data are sent
     */
    @Throws(WmException::class)
    fun getAllVersionsForOS(osName: String?): Array<String> {
        loadDeviceOsesData()
        return if (deviceOsVersionsMap.containsKey(osName)) {
            val osVers: List<String>? = deviceOsVersionsMap[osName]?.toMutableList()
            if (osVers != null) {
                val cleanedOSVersions = osVers.filter { it != "" }
                cleanedOSVersions.toTypedArray()
            } else {
                emptyArray()
            }
        } else {
            throw WmException("Error getting data from WM server: $osName does not exist")
        }
    }

    /**
     * @return GetAllDeviceMakes returns a string array of all devices brand_name capabilities in WM server
     * @throws WmException In case a connection error occurs or malformed data are sent
     */
    @Throws(WmException::class)
    fun getAllDeviceMakes(): Array<String> {
        loadDeviceMakesData()
        return deviceMakes
    }

    /**
     * @param make a brand name
     * @return An array of [com.scientiamobile.wurfl.wmclient.kotlin.JSONModelMktName] that contain values for model_name
     * and marketing_name (the latter, if available).
     * @throws WmException In case a connection error occurs, malformed data are sent, or the given brand name parameter does not exist in WM server.
     */
    @Throws(WmException::class)
    fun getAllDevicesForMake(make: String): Array<JSONModelMktName> {
        loadDeviceMakesData()
        if (deviceMakesMap.containsKey(make)) {
            val mdMks = deviceMakesMap[make]
            return mdMks?.toTypedArray() ?: emptyArray()
        } else {
            throw WmException(String.format("Error getting data from WM server: $make does not exist"))
        }
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
            cacheKey = this.getHeadersCacheKey(request.lookupHeaders)
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

    private fun getHeadersCacheKey(headers: Map<String, String>): String {
        var key = ""

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

    @Throws(WmException::class)
    private fun loadDeviceOsesData() {
        synchronized(deviceOSesLock) {
            if (deviceOSes.isNotEmpty()) {
                return
            }
        }
        try {
            val localOSes = runBlocking {
                return@runBlocking  internalClient.get<Array<JSONDeviceOsVersions>>(createUrl("/v2/alldeviceosversions/json"))
            }

            val dmMap: MutableMap<String, MutableList<String>> = HashMap()
            val devOSes: MutableSet<String> = HashSet()
            for (osVer in localOSes) {
                devOSes.add(osVer.osName)
                if (!dmMap.containsKey(osVer.osName)) {
                    dmMap[osVer.osName] = ArrayList()
                }
                dmMap[osVer.osName]!!.add(osVer.osVersion)
            }
            synchronized(deviceOSesLock) {
                deviceOSes = devOSes.toTypedArray()
                deviceOsVersionsMap = dmMap
            }
        } catch (e: IOException) {
            throw WmException("An error occurred getting device os name and version data " + e.message)
        }
    }

    @Throws(WmException::class)
    private fun loadDeviceMakesData() {

        // If deviceMakes cache has values everything has already been loaded, thus we exit
        synchronized(deviceMakesLock) {
            if (deviceMakes.isNotEmpty()) {
                return
            }
        }

        // No values already loaded, let's do it.
        try {
            val localMakeModels = runBlocking {
                return@runBlocking  internalClient.get<Array<JSONMakeModel>>(createUrl("/v2/alldevices/json"))
            }

            val dmMap: MutableMap<String, MutableList<JSONModelMktName>> = HashMap()
            val devMakes: MutableSet<String> = HashSet()
            localMakeModels.forEach {
                if (!dmMap.containsKey(it.brandName)) {
                    devMakes.add(it.brandName)
                }
                var mdMkNames = dmMap[it.brandName]
                if (mdMkNames == null) {
                    mdMkNames = ArrayList()
                    dmMap[it.brandName] = mdMkNames
                }
                mdMkNames.add(JSONModelMktName(modelName = it.modelName, marketingName = it.marketingName))
            }

            synchronized(deviceMakesLock) {
                deviceMakesMap = dmMap
                deviceMakes = devMakes.toTypedArray()
            }
        } catch (e: IOException) {
            throw WmException("An error occurred getting makes and model data " + e.message)
        }
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

// TODO 2022:
//  - multithreading tests
//  - teamcity builds
//  - wuperf for kotlin
//  - examples (usual client API example, ktor server example)
//  - compare performance of different kotlin HTTP clients (CIO, okHttp, etc.)