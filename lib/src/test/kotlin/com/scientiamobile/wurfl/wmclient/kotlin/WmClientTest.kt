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

import kotlin.test.*

class WmClientTest {

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
    fun testCreateWithoutHostTest(){
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
    fun destroyClientTest(){
        var exc = false
        try{
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
