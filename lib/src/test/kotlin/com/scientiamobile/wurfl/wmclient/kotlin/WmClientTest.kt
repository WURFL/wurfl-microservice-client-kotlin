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

import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.isAccessible
import kotlin.system.measureNanoTime
import kotlin.test.*


class WmClientTest {


    // Test values for lookupRequest tests
    private val MOCK_REQUEST_UA =
        "Mozilla/5.0 (Nintendo Switch; WebApplet) AppleWebKit/601.6 (KHTML, like Gecko) NF/4.0.0.5.9 NintendoBrowser/5.1.0.13341"
    private val MOCK_REQUEST_X_UC_BROWSER =
        "Mozilla/5.0 (Nintendo Switch; ShareApplet) AppleWebKit/601.6 (KHTML, like Gecko) NF/4.0.0.5.9 NintendoBrowser/5.1.0.13341"
    private val MOCK_REQUEST_DEVICE_STOCK_UA =
        "Mozilla/5.0 (Nintendo Switch; WifiWebAuthApplet) AppleWebKit/601.6 (KHTML, like Gecko) NF/4.0.0.5.9 NintendoBrowser/5.1.0.13341"


    @Test
    fun createAndGetInfoOk() {
        val wmclient = WmClient.create("http", "localhost", "8080", "")
        assertNotNull(wmclient)
        val info = wmclient.getInfo()

        assertNotNull(info)
        assertNotNull(info.wurflApiVersion)
        assertNotNull(info.wurflInfo)
        assertTrue { info.importantHeaders.isNotEmpty() }
        assertTrue { info.staticCaps.isNotEmpty() }
        assertTrue { info.virtualCaps.isNotEmpty() }
        wmclient.destroy()
    }

    @Test(expected = WmException::class)
    fun createWithEmptyServerValuesTest() {
        WmClient.create("", "", "", "")
    }

    @Test(expected = WmException::class)
    fun testCreateWithoutHostTest() {
        WmClient.create("http", "", "8080", "")
    }

    @Test(expected = WmException::class)
    fun createWithServerDownTest() {
        WmClient.create("http", "localhost", "18080", "")
    }

    @Test
    fun hasStaticCapabilityTest() {
        val client = createTestClient()
        assertNotNull(client)
        assertTrue(client.hasStaticCapability("brand_name"))
        assertTrue(client.hasStaticCapability("model_name"))
        assertTrue(client.hasStaticCapability("is_smarttv"))
        // this is a virtual capability, so it shouldn't be returned
        assertFalse(client.hasStaticCapability("is_app"))
        client.destroy()
    }

    @Test
    fun hasVirtualCapabilityTest() {
        val client = createTestClient()
        assertTrue(client.hasVirtualCapability("is_app"))
        assertTrue(client.hasVirtualCapability("is_smartphone"))
        assertTrue(client.hasVirtualCapability("form_factor"))
        assertTrue(client.hasVirtualCapability("is_app_webview"))
        // this is a static capability, so it shouldn't be returned
        assertFalse(client.hasVirtualCapability("brand_name"))
        assertFalse(client.hasVirtualCapability("is_wireless_device"))
        client.destroy()
    }


    @Test
    fun lookupUserAgentTest() {
        val ua =
            "Mozilla/5.0 (Linux; Android 7.0; SAMSUNG SM-G950F Build/NRD90M) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/5.2 Chrome/51.0.2704.106 Mobile Safari/537.36"
        val client = createTestClient()
        val device: JSONDeviceData = client.lookupUseragent(ua)
        assertNotNull(device)
        val capabilities = device.capabilities
        val dcount = capabilities.size
        assertTrue(dcount >= 40)
        assertEquals(capabilities["model_name"], "SM-G950F")
        assertEquals("false", capabilities["is_app"])
        assertEquals("false", capabilities["is_app_webview"])
        client.destroy()
    }

    @Test
    fun lookupUserAgentWithSpecificCapsTest() {
        val client = createTestClient()
        val reqCaps =
            arrayOf("brand_name", "model_name", "physical_screen_width", "device_os", "is_android", "is_ios", "is_app")
        client.setRequestedCapabilities(reqCaps)
        val ua =
            "Mozilla/5.0 (Nintendo Switch; WebApplet) AppleWebKit/601.6 (KHTML, like Gecko) NF/4.0.0.5.9 NintendoBrowser/5.1.0.13341"
        val device: JSONDeviceData = client.lookupUseragent(ua)
        val capabilities = device.capabilities
        assertNotNull(device)
        assertNotNull(capabilities)
        assertEquals("Nintendo", capabilities["brand_name"])
        assertEquals("Switch", capabilities["model_name"])
        assertEquals("false", capabilities["is_android"])
        assertEquals(8, capabilities.size)
        client.destroy()
    }

    @Test
    fun lookupUseragentEmptyUaTest() {
        val client = createTestClient()
        try {
            val device: JSONDeviceData = client.lookupUseragent("")
            assertNotNull(device)
            assertEquals(device.capabilities["wurfl_id"], "generic")
        } catch (e: WmException) {
            fail(e.message)
        }
    }

    @Test
    fun lookupDeviceIdTest() {
        val client = createTestClient()
        val device: JSONDeviceData = client.lookupDeviceId("nokia_generic_series40")
        assertNotNull(device)
        val capabilities = device.capabilities
        assertNotNull(capabilities)
        // num caps + num vcaps + wurfl id
        assertTrue(capabilities.size >= 40)
        assertEquals("false", capabilities["is_android"])
        assertEquals("128", capabilities["resolution_width"])
        client.destroy()
    }

    @Test
    fun lookupDeviceIdWithSpecificCaps() {
        val client = createTestClient()
        val reqCaps = arrayOf("brand_name", "is_smarttv")
        val reqvCaps = arrayOf("is_app", "is_app_webview")
        client.setRequestedStaticCapabilities(reqCaps)
        client.setRequestedVirtualCapabilities(reqvCaps)
        val device: JSONDeviceData = client.lookupDeviceId("generic_opera_mini_version1")
        assertNotNull(device)
        val capabilities = device.capabilities
        assertNotNull(capabilities)
        assertEquals("Opera", capabilities["brand_name"])
        assertEquals("false", capabilities["is_smarttv"])
        assertEquals(5, capabilities.size)
        client.destroy()
    }

    @Test
    fun lookupDeviceIdWithWrongIdTest() {
        val client = createTestClient()
        var exc = false
        try {
            // this ID does not exist
            val device = client.lookupDeviceId("nokia_generic_series40_wrong")
            println(device.capabilities["wurfl_id"])

        } catch (e: WmException) {
            exc = true
            assertTrue(e.message!!.contains("device is missing"))
        }
        assertTrue(exc)
    }

    @Test
    fun lookupDeviceIdWithEmptyIdTest() {
        val client = createTestClient()
        var exc = false
        try {
            client.lookupDeviceId("")
        } catch (e: WmException) {
            exc = true
            assertTrue(e.message!!.contains("device is missing"))
        }
        assertTrue(exc)
    }

    @Test
    fun lookupRequestTestOk() {

        val client = createTestClient()
        withTestApplication {

            val callMock = createCall {
                addHeader("User-Agent", MOCK_REQUEST_UA)
                addHeader("Device-Stock-UA", MOCK_REQUEST_DEVICE_STOCK_UA)
                addHeader("X-UCBrowser-Device-UA", MOCK_REQUEST_X_UC_BROWSER)
                addHeader("Device-Stock-UA", MOCK_REQUEST_DEVICE_STOCK_UA)
                addHeader("Content-Type", ContentType.Application.Json.contentType)
                addHeader("Accept-Encoding", "gzip, deflate")
            }

            val device = client.lookupRequest(callMock.request)
            val capabilities = device.capabilities
            assertNotNull(capabilities)
            assertTrue(capabilities.size >= 40)
            assertEquals("Smart-TV", capabilities.get("form_factor"))
            assertEquals("5.1.0.13341", capabilities.get("advertised_browser_version"))
            assertEquals("false", capabilities.get("is_app"))
            assertEquals("false", capabilities.get("is_app_webview"))
            assertEquals("Nintendo", capabilities.get("advertised_device_os"))
            assertEquals("Nintendo Switch", capabilities.get("complete_device_name"))
            assertEquals("nintendo_switch_ver1", capabilities.get("wurfl_id"))
        }
        client.destroy()
    }

    @Test
    fun lookupRequestOkWithSpecificCaps() {
        val client = createTestClient()
        val reqCaps = arrayOf("is_mobile", "form_factor", "is_app", "complete_device_name",
            "advertised_device_os", "brand_name")
        client.setRequestedCapabilities(reqCaps)

        withTestApplication {

            val callMock = createCall {
                addHeader("User-Agent", MOCK_REQUEST_UA)
                addHeader("Device-Stock-UA", MOCK_REQUEST_DEVICE_STOCK_UA)
                addHeader("X-UCBrowser-Device-UA", MOCK_REQUEST_X_UC_BROWSER)
                addHeader("Device-Stock-UA", MOCK_REQUEST_DEVICE_STOCK_UA)
                addHeader("Content-Type", ContentType.Application.Json.contentType)
                addHeader("Accept-Encoding", "gzip, deflate")
            }

            val device = client.lookupRequest(callMock.request)
            val capabilities = device.capabilities
            assertNotNull(capabilities)
            assertEquals(7, capabilities.size)
            assertEquals("false", capabilities["is_app"])
            assertEquals("Nintendo", capabilities["advertised_device_os"])
            assertEquals("Nintendo Switch", capabilities["complete_device_name"])
            assertEquals("nintendo_switch_ver1", capabilities["wurfl_id"])
        }
        client.destroy()
    }

    @Test
    fun lookupRequestWithSpecificCapsAndNoHeadersTest() {
        val client = createTestClient()
        val reqCaps = arrayOf("is_mobile", "form_factor", "is_app", "complete_device_name",
            "advertised_device_os", "brand_name")
        client.setRequestedCapabilities(reqCaps)

        withTestApplication {


            // this will create a test application request with an empty headers map
            val callMock = createCall {}
            val device = client.lookupRequest(callMock.request)
            val capabilities = device.capabilities
            assertNotNull(capabilities)
            assertEquals(7, capabilities.size)
            assertEquals("generic", capabilities["wurfl_id"])
        }
        client.destroy()
    }

    @Test
    fun lookupRequestWithCacheTest() {
        val client = createTestClient()
        client.setCacheSize(1000)

        withTestApplication {

            val callMock = createCall {
                addHeader("User-Agent", MOCK_REQUEST_UA)
                addHeader("Device-Stock-UA", MOCK_REQUEST_DEVICE_STOCK_UA)
                addHeader("X-UCBrowser-Device-UA", MOCK_REQUEST_X_UC_BROWSER)
                addHeader("Device-Stock-UA", MOCK_REQUEST_DEVICE_STOCK_UA)
                addHeader("Content-Type", ContentType.Application.Json.contentType)
                addHeader("Accept-Encoding", "gzip, deflate")
            }

            for (i in 0..49) {
                val device = client.lookupRequest(callMock.request)
                val capabilities = device.capabilities
                assertNotNull(capabilities)
                assertEquals("Nintendo", capabilities["brand_name"])
                assertEquals("true", capabilities["is_mobile"])
                assertTrue(capabilities.size >= 40)
                val cacheSize = client.getActualCacheSizes()
                assertEquals(cacheSize.first, 0)
                assertEquals(cacheSize.second, 1)
            }
        }
        client.destroy()
    }

    @Test
    fun lookupRequestWithMixedCaseHeadersTest() {

        val client = createTestClient()
        withTestApplication {

            val callMock = createCall {
                addHeader("uSer-AgeNt", MOCK_REQUEST_UA)
                addHeader("device-sTock-UA", MOCK_REQUEST_DEVICE_STOCK_UA)
                addHeader("X-UCBroWser-DevicE-uA", MOCK_REQUEST_X_UC_BROWSER)
                addHeader("Device-stOCk-UA", MOCK_REQUEST_DEVICE_STOCK_UA)
                addHeader("ContenT-TypE", ContentType.Application.Json.contentType)
                addHeader("aCcept-EnCodIng", "gzip, deflate")
            }

            val device = client.lookupRequest(callMock.request)
            val capabilities = device.capabilities
            assertNotNull(capabilities)
            assertTrue(capabilities.size >= 40)
            assertEquals("Smart-TV", capabilities.get("form_factor"))
            assertEquals("5.1.0.13341", capabilities.get("advertised_browser_version"))
            assertEquals("false", capabilities.get("is_app"))
            assertEquals("false", capabilities.get("is_app_webview"))
            assertEquals("Nintendo", capabilities.get("advertised_device_os"))
            assertEquals("Nintendo Switch", capabilities.get("complete_device_name"))
            assertEquals("nintendo_switch_ver1", capabilities.get("wurfl_id"))
        }
        client.destroy()
    }

    @Test
    fun lookupHeadersOKTest() {

        val client = createTestClient()
        val headers: Map<String, String> = createTestHeaders(false)
        val device: JSONDeviceData = client.lookupHeaders(headers)
        val capabilities = device.capabilities
        assertNotNull(capabilities)
        assertTrue(capabilities.size >= 40)
        assertEquals("Smart-TV", capabilities["form_factor"])
        assertEquals("5.1.0.13341", capabilities["advertised_browser_version"])
        assertEquals("false", capabilities["is_app"])
        assertEquals("false", capabilities["is_app_webview"])
        assertEquals("Nintendo", capabilities["advertised_device_os"])
        assertEquals("Nintendo Switch", capabilities["complete_device_name"])
        assertEquals("nintendo_switch_ver1", capabilities["wurfl_id"])
        client.destroy()
    }

    private fun createTestHeaders(useMixedCase: Boolean): Map<String, String> {

        if (useMixedCase) {
            return mapOf(
                "User-AGenT" to
                        "Mozilla/5.0 (Nintendo Switch; WebApplet) AppleWebKit/601.6 (KHTML, like Gecko) NF/4.0.0.5.9 NintendoBrowser/5.1.0.13341",
                "Content-TYPe" to "gzip, deflate",
                "Accept-EnCoding" to "application/json",
                "X-UCBrowsEr-Device-UA" to
                        "Mozilla/5.0 (Nintendo Switch; ShareApplet) AppleWebKit/601.6 (KHTML, like Gecko) NF/4.0.0.5.9 NintendoBrowser/5.1.0.13341",
                "Device-StOck-UA" to
                        "Mozilla/5.0 (Nintendo Switch; WifiWebAuthApplet) AppleWebKit/601.6 (KHTML, like Gecko) NF/4.0.0.5.9 NintendoBrowser/5.1.0.13341")
        }

        return mapOf("User-Agent" to MOCK_REQUEST_UA,
            "Content-Type" to "gzip, deflate",
            "Accept-Encoding" to "application/json",
            "X-UCBrowser-Device-UA" to MOCK_REQUEST_X_UC_BROWSER,
            "Device-Stock-UA" to MOCK_REQUEST_DEVICE_STOCK_UA)
    }

    @Test
    fun lookupHeadersWithMixedCaseTest() {

        val client = createTestClient()
        val headers: Map<String, String> = createTestHeaders(true)
        val device: JSONDeviceData = client.lookupHeaders(headers)
        assertNotNull(device)
        val capabilities = device.capabilities
        assertNotNull(capabilities)
        assertTrue(capabilities.size >= 40)
        assertEquals("Smart-TV", capabilities["form_factor"])
        assertEquals("5.1.0.13341", capabilities["advertised_browser_version"])
        assertEquals("false", capabilities["is_app"])
        assertEquals("false", capabilities["is_app_webview"])
        assertEquals("Nintendo", capabilities["advertised_device_os"])
        assertEquals("Nintendo Switch", capabilities["complete_device_name"])
        assertEquals("nintendo_switch_ver1", capabilities["wurfl_id"])
    }

    @Test
    fun lookupHeadersWithMixedCaseAndCachedClientTest() {
        val client = createTestClient()
        client.setCacheSize(1000)
        var headers: Map<String, String> = createTestHeaders(true)
        var device = client.lookupHeaders(headers)
        var capabilities: Map<String, String> = device.capabilities
        assertNotNull(capabilities)
        assertTrue(capabilities.size >= 40)
        assertEquals("Smart-TV", capabilities["form_factor"])
        assertEquals("5.1.0.13341", capabilities["advertised_browser_version"])
        assertEquals("false", capabilities["is_app"])
        assertEquals("false", capabilities["is_app_webview"])
        assertEquals("Nintendo", capabilities["advertised_device_os"])
        assertEquals("Nintendo Switch", capabilities["complete_device_name"])
        assertEquals("nintendo_switch_ver1", capabilities["wurfl_id"])
        var cacheSize = client.getActualCacheSizes()
        assertEquals(cacheSize.second, 1)

        // Now mix headers case in a different way (we should hit the cache now)
        headers = mapOf(
            "UseR-AGenT" to MOCK_REQUEST_UA,
            "ConTent-TYPe" to "gzip, deflate",
            "AccEpt-EnCoding" to "application/json",
            "X-UCbrowsEr-DeviCe-UA" to MOCK_REQUEST_X_UC_BROWSER,
            "DevIce-StOck-Ua" to MOCK_REQUEST_DEVICE_STOCK_UA)
        device = client.lookupHeaders(headers)
        capabilities = device.capabilities
        assertNotNull(capabilities)
        assertEquals("nintendo_switch_ver1", capabilities["wurfl_id"])
        // Cache size should stay 1, which means that previously stored cache value has been hit even if header case has been changed
        cacheSize = client.getActualCacheSizes()
        assertEquals(cacheSize.second, 1)
        client.destroy()
    }

    @Test
    fun lookupHeadersWithEmptyHeadersTest() {
        val client = createTestClient()
        val device: JSONDeviceData = client.lookupHeaders(emptyMap())
        val capabilities = device.capabilities
        assertNotNull(capabilities)
        assertEquals("generic", capabilities["wurfl_id"])
        client.destroy()
    }

    @Test
    fun setRequestedCapabilitiesTest() {
        val client = createTestClient()
        client.setCacheSize(1000)
        client.setRequestedStaticCapabilities(arrayOf("wrong1", "brand_name", "is_ios"))
        client.setRequestedVirtualCapabilities(arrayOf("wrong2", "brand_name", "is_ios"))
        val ua =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 10_2_1 like Mac OS X) AppleWebKit/602.4.6 (KHTML, like Gecko) Version/10.0 Mobile/14D27 Safari/602.1"
        var d = client.lookupUseragent(ua)
        assertNotNull(d)
        assertEquals(d.capabilities.size, 3)
        assertNull(d.capabilities["wrong1"])

        // This will reset static caps
        client.setRequestedStaticCapabilities(null)
        d = client.lookupUseragent(ua)
        assertEquals(d.capabilities.size, 2)
        // If all required caps arrays are reset, ALL caps are returned
        client.setRequestedVirtualCapabilities(null)
        d = client.lookupUseragent(ua)
        val capsize: Int = d.capabilities.size
        assertTrue(capsize >= 40)
        client.destroy()
    }

    @Test
    fun lookupWithCacheExpirationTest() {
        val client = createTestClient()
        client.setCacheSize(1000)
        // perform a couple of detection, one adds a device to deviceID based cache, the other to headers based cache
        val d1 = client.lookupDeviceId("nokia_generic_series40")
        client.lookupUseragent("Mozilla/5.0 (iPhone; CPU iPhone OS 10_2_1 like Mac OS X) AppleWebKit/602.4.6 (KHTML, like Gecko) Version/10.0 Mobile/14D27 Safari/602.1")
        var csizes = client.getActualCacheSizes()
        assertEquals(1, csizes.first)
        assertEquals(1, csizes.second)
        // Date doesn't change, so cache stays full
        invokeClearCacheIfNeeded(client, d1.ltime!!)
        assertEquals(1, csizes.first)
        assertEquals(1, csizes.second)

        // Force cache expiration using reflection: now, date changes, so caches must be cleared
        invokeClearCacheIfNeeded(client, "2199-12-31")
        csizes = client.getActualCacheSizes()
        assertEquals(0, csizes.first)
        assertEquals(0, csizes.second)
        // Load a device again
        client.lookupDeviceId("nokia_generic_series40")
        client.lookupUseragent("Mozilla/5.0 (iPhone; CPU iPhone OS 10_2_1 like Mac OS X) AppleWebKit/602.4.6 (KHTML, like Gecko) Version/10.0 Mobile/14D27 Safari/602.1")

        // caches are filled again
        csizes = client.getActualCacheSizes()
        assertEquals(1, csizes.first)
        assertEquals(1, csizes.second)
        client.destroy()
    }

    @Test
    fun destroyClientTest() {
        var exc = false
        try {
            val client = createTestClient()
            client.destroy()
            // triggering client requests after destroy causes a WM exception or a JobCancellation exception
            client.getInfo()

        } catch (e: WmException) {
            exc = true
        } catch (e: Exception) {
            exc = true
        }
        assertTrue(exc)
    }

    @Test
    fun realCacheUsageTest() {
        val client = createTestClient()
        try {
            val elapsedNoCache = measureNanoTime {
                for (ua in TestData.USER_AGENTS) {
                    client.lookupUseragent(ua)
                }
            }

            // Now, let's add a cache layer
            client.setCacheSize(100000)

            // fill cache
            for (ua in TestData.USER_AGENTS) {
                client.lookupUseragent(ua)
            }

            // now use it
            val elapsedWithCache = measureNanoTime {
                for (ua in TestData.USER_AGENTS) {
                    client.lookupUseragent(ua)
                }
            }

            // Cache must be at least an order of magnitude faster
            assertTrue(elapsedNoCache > elapsedWithCache * 10)
        } finally {
            client.destroy()
        }
    }

    @Test
    fun realCacheUsageTest_2() {
        val client = createTestClient()
        try {

            val elapsedNoCache = measureNanoTime {
                for (ua in TestData.USER_AGENTS) {
                    client.lookupUseragent(ua)
                }
            }
            val avgNoCache = elapsedNoCache.toDouble() / TestData.USER_AGENTS.size.toDouble()

            // Now, let's add a cache layer
            client.setCacheSize(100000)

            // fill cache
            for (ua in TestData.USER_AGENTS) {
                client.lookupUseragent(ua)
            }

            // now use it
            val elapsedWithCache =  measureNanoTime {
                for (ua in TestData.USER_AGENTS) {
                    client.lookupUseragent(ua)
                }
            }
            val avgWithCache = elapsedWithCache.toDouble() / TestData.USER_AGENTS.size.toDouble()

            // Cache must be at least an order of magnitude faster
            assertTrue(avgNoCache > avgWithCache * 10)
        } finally {
            client.destroy()
        }
    }

    //----------------------------- ENUMERATORS TESTS -----------------------------
    @Test
    fun getAllOsesTest() {
        val client: WmClient = createTestClient()
        try {
            val oses = client.getAllOSes()
            assertNotNull(oses)
            assertTrue(oses.size >= 30)
        } finally {
            client.destroy()
        }
    }

    @Test
    fun getAllVersionsForOSTest() {
        val client: WmClient = createTestClient()
        try {
            val osVersions = client.getAllVersionsForOS("Android")
            assertNotNull(osVersions)
            assertTrue(osVersions.size > 30)
            assertNotNull(osVersions[0])
        } finally {
            client.destroy()
        }
    }

    @Test(expected = WmException::class)
    fun getAllVersionsForOsWithWrongOsTest() {
        val client: WmClient = createTestClient()
        try {
            client.getAllVersionsForOS("FakeOS")
        } finally {
            client.destroy()
        }
    }


    // Uses reflection to force invoke of private method clearCacheIfNeeded for testing purposes
    private fun invokeClearCacheIfNeeded(client: WmClient, ltime: String) {
        val clientClass = WmClient::class
        val functs = clientClass.functions.filter { it.name == "clearCachesIfNeeded" }
        val funct = functs[0]
        funct.isAccessible = true
        funct.call(client, ltime)
        funct.isAccessible = false
    }

    private fun createTestClient(): WmClient {
        val host = System.getenv("WM_HOST") ?: "localhost"
        val port = System.getenv("WM_PORT") ?: "8080"
        return WmClient.create("http", host, port, "")
    }
}
