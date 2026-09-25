package com.punctum.gallery

/** Keep unchanged instances while taking both ordering and edited metadata from the fresh scan. */
internal fun <K, T> mergeOrderedItems(
    current: List<T>, incoming: List<T>, identity: (T) -> K,
): List<T> {
    if (current.isEmpty()) return incoming
    val currentByID = current.associateBy(identity)
    return incoming.map { fresh ->
        currentByID[identity(fresh)]?.takeIf { it == fresh } ?: fresh
    }
}
