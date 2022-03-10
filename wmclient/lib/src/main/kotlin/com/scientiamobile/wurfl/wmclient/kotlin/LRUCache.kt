/*
Copyright 2019 ScientiaMobile Inc. http://www.scientiamobile.com

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

import java.util.concurrent.ConcurrentHashMap

/**
 * Caches JSONDeviceData using string keys.<br></br>
 * The implementation is internally synchronized, as it is backed by a SynchronizedMap.<br></br>
 * Created by Andrea Castello on 11/09/2017.
 */
internal class LRUCache<K, E> @JvmOverloads constructor(maxSize: Int = DEFAULT_SIZE) {
    // We'll use this object to lock
    private val mutex: Any
    private var size = 0
    private val cache: ConcurrentHashMap<K, Node<K, E>?>
    private var head: Node<K, E>? = null
    private var tail: Node<K, E>? = null

    init {
        size = if (maxSize > 0) {
            maxSize
        } else {
            DEFAULT_SIZE
        }
        cache = ConcurrentHashMap<K, Node<K, E>?>(maxSize, 0.75f, 64)
        mutex = this
    }

    /**
     * Returns the element mapped to the given key, or null if key does not exist in cache
     *
     * @param key the cache key
     * @return the cache entry
     */
    fun getEntry(key: K): E? {
        synchronized(mutex) {
            val entry: Node<K, E> = cache[key] ?: return null

            // Since it has been used now, we send the entry to the head
            moveToHead(entry)
            return entry.value
        }
    }

    /**
     * Removes all elements from cache.
     */
    fun clear() {
        synchronized(mutex) {
            cache.clear()
            head = null
            tail = null
        }
    }

    /**
     * Puts the entry device in cache.
     *
     * @param key   the cache key
     * @param value the value to be cached
     */
    fun putEntry(key: K, value: E) {
        synchronized(mutex) {
            var entry = cache[key]
            if (entry == null) {
                entry = Node(key, value)
                if (size() == this.size) {
                    cache.remove(tail?.key)
                    tail = tail?.previous
                    if (tail != null) {
                        // isn't this redundant?
                        tail!!.next = null
                    }
                }
                cache[key] = entry
            }

            entry.value = value
            moveToHead(entry)
            if (tail == null) tail = head
        }
    }

    companion object {
        private const val DEFAULT_SIZE = 20000
    }

    fun size(): Int {
        synchronized(mutex) { return cache.size }
    }

    // moves the given entry to the head of the cache
    private fun moveToHead(entry: Node<K, E>?) {
        if (entry === head) {
            return
        }
        val next: Node<K, E>? = entry?.next
        val previous: Node<K, E>? = entry?.previous
        if (next != null) {
            next.previous = entry.previous
        }
        if (previous != null) {
            previous.next = entry.next
        }
        entry?.previous = null
        entry?.next = head
        if (head != null) {
            head?.previous = entry
        }
        head = entry
        if (tail === entry) {
            tail = previous
        }
    }

    // represents a node in the internal cache, holding references to its previous and next elements
    private class Node<K, E> constructor(val key: K, var value: E) {
        var next: Node<K, E>? = null
        var previous: Node<K, E>? = null
    }
}