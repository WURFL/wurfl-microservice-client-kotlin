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
        val client = WmClient.create("http", "localhost", "8080", "")
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
        val client = WmClient.create("http", "localhost", "8080", "")
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
        val client = WmClient.create("http", "localhost", "8080", "")
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
        val client = WmClient.create("http", "localhost", "8080", "")
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
        val client = WmClient.create("http", "localhost", "8080", "")
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
        val client = WmClient.create("http", "localhost", "8080", "")
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
        val client = WmClient.create("http", "localhost", "8080", "")
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
        val client = WmClient.create("http", "localhost", "8080", "")
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
        val client = WmClient.create("http", "localhost", "8080", "")
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

        val client = WmClient.create("http", "localhost", "8080", "")
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
        val client = WmClient.create("http", "localhost", "8080", "")
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
        val client = WmClient.create("http", "localhost", "8080", "")
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
    fun lookupRequestWithCacheTest(){
        val client = WmClient.create("http", "localhost", "8080", "")
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
                val cacheSize  = client.getActualCacheSizes()
                assertEquals(cacheSize.first, 0)
                assertEquals(cacheSize.second, 1)
            }
        }
        client.destroy()
    }

    @Test
    fun lookupRequestWithMixedCaseHeadersTest() {

        val client = WmClient.create("http", "localhost", "8080", "")
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
    @Throws(WmException::class)
    fun setRequestedCapabilitiesTest() {
        val client = WmClient.create("http", "localhost", "8080", "")
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
    fun destroyClientTest() {
        var exc = false
        try {
            val client = WmClient.create("http", "localhost", "8080", "")
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
}
