package com.punctum.gallery.data

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Serial background writes, with the latest snapshot per key winning each batch. */
internal class CoalescingCacheWriter<K, V : Any>(
    private val delayMillis: Long = 400,
    private val write: (K, V) -> Unit,
    private val onFailure: (Exception) -> Unit = {},
) {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "photo-cache-writer").apply { isDaemon = true }
    }
    private val lock = Any()
    private val latest = mutableMapOf<K, V>()
    private val pending = mutableMapOf<K, V>()
    private var scheduled = false

    fun latest(key: K): V? = synchronized(lock) { latest[key] }

    fun submit(key: K, value: V) = synchronized(lock) {
        latest[key] = value
        pending[key] = value
        if (!scheduled) {
            scheduled = true
            executor.schedule(::drain, delayMillis, TimeUnit.MILLISECONDS)
        }
    }

    private fun drain() {
        val batch = synchronized(lock) {
            pending.toMap().also {
                pending.clear()
                scheduled = false
            }
        }
        batch.forEach { (key, value) ->
            try {
                write(key, value)
                synchronized(lock) {
                    if (latest[key] === value) latest.remove(key)
                }
            } catch (error: Exception) {
                onFailure(error)
            }
        }
    }
}
