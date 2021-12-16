package com.scientiamobile.wurfl.wmclient.kotlin

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class LRUCacheTest {

    @Test
    fun replaceOnMultiAddTest() {
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
    fun removeOnMaxSizeTest() {
        val cache = LRUCache<String, Int>(5)
        for (i in 0..5) {
            cache.putEntry(i.toString(), i)
        }
        assertEquals(cache.size(), 5)
        // "0" entry has been removed when inserting "5"
        assertNull(cache.getEntry("0"))
    }

    @Test
    fun multithreadGetAndClearCacheTest() {
        val cache = LRUCache<String, Any>(1000)
        val numOfCoroutines = 32
        val results = BooleanArray(numOfCoroutines)
        runBlocking {
            coroutineScope { // scope for coroutines
                repeat(numOfCoroutines) { index ->
                    println("Starting coroutine #: $index")
                    launch {
                        // Start task
                        val userAgentList = TestData.createTestUserAgentList()
                        try {
                            var readLines = 0
                            for (line in userAgentList) {
                                cache.getEntry(line)
                                // every 300 detections and only on even threads we clear cache to check it does not cause error
                                readLines++

                                // every 300 and only on even threads, we clear cache, to see if getEntry handles it without raising errors
                                if (readLines % 300 == 0 && index % 2 == 0) {
                                    cache.clear()
                                } else if (index % 2 != 0) {
                                    cache.putEntry(line, Any())
                                }
                            }
                            println("Lines read from terminated task #$index : $readLines")
                            results[index] = true

                        } catch (e: Exception) {
                            results[index] = false
                            fail("Test failed due to exception :" + e.message, e)
                        }
                    }
                }
            }
        }

        for (i in 0 until numOfCoroutines){
            assertTrue(results[i])
        }
    }
}