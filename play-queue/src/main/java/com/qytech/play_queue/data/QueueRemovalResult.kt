package com.qytech.play_queue.data

data class QueueRemovalResult(
    val removedSegmentId: String,
    val removedRanges: List<RemovedGlobalRange>
) {
    val removed: Boolean get() = removedRanges.isNotEmpty()

    fun contains(globalPosition: Int): Boolean {
        return removedRanges.any { it.contains(globalPosition) }
    }

    fun removedCountBefore(globalPosition: Int): Int {
        return removedRanges.sumOf { it.removedCountBefore(globalPosition) }
    }

    companion object {
        fun none(segmentId: String) = QueueRemovalResult(
            removedSegmentId = segmentId,
            removedRanges = emptyList()
        )
    }
}
