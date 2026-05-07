package com.qytech.play_queue.data

data class RemovedGlobalRange(
    val startInclusive: Int,
    val endExclusive: Int
)

val RemovedGlobalRange.length: Int
    get() = endExclusive - startInclusive

fun RemovedGlobalRange.contains(position: Int): Boolean {
    return position in startInclusive until endExclusive
}

fun RemovedGlobalRange.removedCountBefore(position: Int): Int {
    return (position.coerceAtMost(endExclusive) - startInclusive).coerceAtLeast(0)
}

fun List<RemovedGlobalRange>.removedCountBefore(position: Int): Int {
    return sumOf { it.removedCountBefore(position) }
}

fun List<RemovedGlobalRange>.containsPosition(position: Int): Boolean {
    return any { it.contains(position) }
}

fun List<RemovedGlobalRange>.hasRemoval(): Boolean {
    return any { it.length > 0 }
}