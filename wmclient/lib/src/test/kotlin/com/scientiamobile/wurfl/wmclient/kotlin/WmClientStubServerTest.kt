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

import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.http.HttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests that run against an in-process stub HTTP server (JDK HttpServer), so no live WM server is needed.
 * The stub serves canned JSON that mimics a server (2.18.0+) which OMITS the "error" field on success.
 */
class WmClientStubServerTest {

    private lateinit var server: HttpServer
    private val port: String get() = server.address.port.toString()

    // path -> (status, body); paths are normalized (client builds URLs with repeated slashes)
    private val responses = ConcurrentHashMap<String, Pair<Int, String>>()
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    private val clients = mutableListOf<WmClient>()

    private val ua = "Mozilla/5.0 (Linux; Android 10; SM-G960F) AppleWebKit/537.36 Chrome/80.0 Mobile Safari/537.36"

    private val infoBody = """
        {"wurfl_api_version":"1.12.8.0","wm_version":"2.18.0","wurfl_info":"WURFL Microservice test",
         "important_headers":["User-Agent","Device-Stock-UA"],
         "static_caps":["brand_name","model_name","is_mobile"],
         "virtual_caps":["is_smartphone","form_factor"],
         "ltime":"2025-10-10 10:00:00"}
    """.trimIndent()

    private fun deviceBody(error: String? = null, ltime: String? = "2025-10-10 10:00:00", mtime: Int? = 1700000000): String {
        val sb = StringBuilder("""{"apiVersion":"1.12.8.0","capabilities":{"wurfl_id":"samsung_sm_g960f_ver1","brand_name":"Samsung","is_mobile":"true"}""")
        if (error != null) sb.append(""","error":"$error"""")
        if (mtime != null) sb.append(""","mtime":$mtime""")
        if (ltime != null) sb.append(""","ltime":"$ltime"""")
        return sb.append("}").toString()
    }

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { ex: HttpExchange ->
            val path = "/" + ex.requestURI.path.trimStart('/')
            hits.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            ex.requestBody.readBytes()
            val (status, body) = responses[path] ?: Pair(404, "{}")
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        responses["/v2/getinfo/json"] = Pair(200, infoBody)
    }

    @After
    fun stopServer() {
        clients.forEach { it.destroy() }
        server.stop(0)
    }

    private fun client(): WmClient {
        val c = WmClient.create("http", "localhost", port, "")
        clients.add(c)
        return c
    }

    private fun hitCount(path: String) = hits[path]?.get() ?: 0

    // ---- model deserialization (no HTTP) ----

    @Test
    fun deviceDataWithoutErrorFieldDeserializesToNullError() {
        val d = Gson().fromJson(deviceBody(), JSONDeviceData::class.java)
        assertNull(d.error)
        assertEquals("Samsung", d.capabilities["brand_name"])
    }

    @Test
    fun deviceDataWithoutLtimeDeserializes() {
        val d = Gson().fromJson(deviceBody(ltime = null), JSONDeviceData::class.java)
        assertNull(d.ltime)
    }

    @Test
    fun requestWireFormatKeepsExpectedKeys() {
        val json = Gson().toJson(Request(mapOf("User-Agent" to ua), arrayOf("brand_name"), arrayOf("is_smartphone"), ""))
        for (key in listOf("lookup_headers", "requested_caps", "requested_vcaps", "wurfl_id")) {
            assertTrue(json.contains("\"$key\""), "missing $key in $json")
        }
    }

    // ---- lookups against a server that omits "error" on success (C1) ----

    @Test
    fun lookupUseragentWithoutErrorField() {
        responses["/v2/lookupuseragent/json"] = Pair(200, deviceBody())
        val d = client().lookupUseragent(ua)
        assertEquals("Samsung", d.capabilities["brand_name"])
    }

    @Test
    fun lookupHeadersWithoutErrorField() {
        responses["/v2/lookuprequest/json"] = Pair(200, deviceBody())
        val d = client().lookupHeaders(mapOf("User-Agent" to ua))
        assertEquals("Samsung", d.capabilities["brand_name"])
    }

    @Test
    fun lookupRequestWithoutErrorField() {
        responses["/v2/lookuprequest/json"] = Pair(200, deviceBody())
        val req = Mockito.mock(HttpServletRequest::class.java)
        Mockito.`when`(req.getHeader("User-Agent")).thenReturn(ua)
        val d = client().lookupRequest(req)
        assertEquals("Samsung", d.capabilities["brand_name"])
    }

    @Test
    fun lookupDeviceIdWithoutErrorField() {
        responses["/v2/lookupdeviceid/json"] = Pair(200, deviceBody())
        val d = client().lookupDeviceId("samsung_sm_g960f_ver1")
        assertEquals("true", d.capabilities["is_mobile"])
    }

    @Test
    fun emptyErrorFieldIsTreatedAsSuccess() {
        responses["/v2/lookupuseragent/json"] = Pair(200, deviceBody(error = ""))
        val d = client().lookupUseragent(ua)
        assertEquals("Samsung", d.capabilities["brand_name"])
    }

    @Test
    fun responseWithoutLtimeIsAccepted() {
        responses["/v2/lookupuseragent/json"] = Pair(200, deviceBody(ltime = null))
        val c = client()
        c.setCacheSize(10)
        c.lookupUseragent(ua)
        c.lookupUseragent(ua)
        // a missing ltime must neither throw nor wipe the cache
        assertEquals(1, hitCount("/v2/lookupuseragent/json"))
    }

    // ---- caches ----

    @Test
    fun headersCacheServesSecondLookup() {
        responses["/v2/lookupuseragent/json"] = Pair(200, deviceBody())
        val c = client()
        c.setCacheSize(10)
        c.lookupUseragent(ua)
        c.lookupUseragent(ua)
        assertEquals(1, hitCount("/v2/lookupuseragent/json"))
        assertEquals(1, c.getActualCacheSizes().second)
    }

    @Test
    fun deviceIdCacheServesSecondLookup() {
        responses["/v2/lookupdeviceid/json"] = Pair(200, deviceBody())
        val c = client()
        c.setCacheSize(10)
        c.lookupDeviceId("samsung_sm_g960f_ver1")
        c.lookupDeviceId("samsung_sm_g960f_ver1")
        assertEquals(1, hitCount("/v2/lookupdeviceid/json"))
        assertEquals(1, c.getActualCacheSizes().first)
    }

    // ---- error handling ----

    @Test
    fun serverErrorSurfacesAsWmExceptionWithServerText() {
        responses["/v2/lookupdeviceid/json"] = Pair(200, """{"capabilities":{},"error":"device is not present","mtime":1}""")
        val ex = assertFailsWith<WmException> { client().lookupDeviceId("nonexistent") }
        assertTrue(ex.message!!.contains("device is not present"), ex.message)
        assertFalse(ex.message!!.contains("JSONDeviceData("), "message must not dump the object: ${ex.message}")
    }

    @Test
    fun infoMissingRequiredFieldsFailsCreate() {
        responses["/v2/getinfo/json"] = Pair(200, """{"important_headers":[],"static_caps":[],"virtual_caps":[]}""")
        assertFailsWith<WmException> { WmClient.create("http", "localhost", port, "") }
    }
}
