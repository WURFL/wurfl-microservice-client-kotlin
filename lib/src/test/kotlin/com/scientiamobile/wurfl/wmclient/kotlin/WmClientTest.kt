package com.scientiamobile.wurfl.wmclient.kotlin

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WmClientTest {

    @Test fun createAndGetInfoOk() {
        val wmclient = WmClient.create("http", "localhost", "9080", "")
        assertNotNull(wmclient)
        val info = wmclient.getInfo()

        assertNotNull(info)
        assertNotNull(info.wurflApiVersion)
        assertNotNull(info.wurflInfo)
        assertTrue { info.importantHeaders.isNotEmpty() }
        assertTrue { info.staticCaps.isNotEmpty() }
        assertTrue { info.virtualCaps.isNotEmpty() }
    }
}
