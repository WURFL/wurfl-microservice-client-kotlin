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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WmClientTest {

    @Test fun createAndGetInfoOk() {
        val wmclient = WmClient.create("http", "localhost", "8080", "")
        assertNotNull(wmclient)
        val info = wmclient.getInfo()

        assertNotNull(info)
        assertNotNull(info.wurflApiVersion)
        assertNotNull(info.wurflInfo)
        assertTrue { info.importantHeaders.isNotEmpty() }
        assertTrue { info.staticCaps.isNotEmpty() }
        assertTrue { info.virtualCaps.isNotEmpty() }
    }

    @Test(expected = WmException::class)
    fun TestCreateWithEmptyServerValues() {
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
    @Throws(WmException::class)
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
    }
}
