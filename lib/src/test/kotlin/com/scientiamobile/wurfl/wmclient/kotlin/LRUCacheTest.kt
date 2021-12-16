package com.scientiamobile.wurfl.wmclient.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LRUCacheTest {

    @Test
    public fun replaceOnMultiAddTest() {
        val cache = LRUCache<String, Int>(5)
        for (i in 0..5) {
            cache.putEntry(i.toString(), i)
        }

        // re-add element with different value
        cache.putEntry("3", 7)
        assertEquals(cache.size(), 5)
        assertEquals(cache.getEntry("3"), 7)
    }

    @Test
    public fun removeOnMaxSizeTest() {
        val cache = LRUCache<String, Int>(5)
        for (i in 0..5) {
            cache.putEntry(i.toString(), i)
        }
        assertEquals(cache.size(), 5)
        // "0" entry has been removed when inserting "5"
        assertNull(cache.getEntry("0"))
    }

}