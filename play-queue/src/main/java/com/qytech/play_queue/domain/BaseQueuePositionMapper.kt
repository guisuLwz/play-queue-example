package com.qytech.play_queue.domain

import com.qytech.play_queue.data.LocatedPosition
import com.qytech.play_queue.data.SegmentWindowRange
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSegmentRefEntity
import kotlin.math.max
import kotlin.math.min

abstract class BaseQueuePositionMapper<SEG: IQueueSegmentEntity, SEG_REF: IQueueSegmentRefEntity>(
    private val queueRefs: List<SEG_REF>,
    private val segmentsById: Map<String, SEG>
): IGlobalPositionMapper<SEG> {
    private val entries: List<Entry<SEG>> = queueRefs.mapNotNull { ref ->
        val segment = segmentsById[ref.segmentId] ?: return@mapNotNull null
        val safeStart = ref.startOffsetInSegment.coerceAtLeast(0)
        val maxLength = (segment.logicalLength() - safeStart).coerceAtLeast(0)
        val safeLength = ref.length.coerceIn(0, maxLength)
        if (safeLength <= 0) {
            null
        } else {
            Entry(
                segment = segment,
                startOffsetInSegment = safeStart,
                length = safeLength
            )
        }
    }

    val segments: List<SEG> = entries.map { it.segment }

    override val totalSize: Int = entries.sumOf { it.length }

    override fun locate(globalPosition: Int): LocatedPosition<SEG>? {
        if (globalPosition !in 0 until totalSize) return null

        var cursor = 0
        entries.forEach { entry ->
            val endExclusive = cursor + entry.length
            if (globalPosition in cursor until endExclusive) {
                return LocatedPosition(
                    segment = entry.segment,
                    startGlobalPosition = cursor,
                    offsetInSegment = entry.startOffsetInSegment + (globalPosition - cursor)
                )
            }
            cursor = endExclusive
        }
        return null
    }

    override fun rangesFor(globalRange: IntRange): List<SegmentWindowRange<SEG>> {
        if (globalRange.isEmpty() || totalSize == 0) return emptyList()

        val safeStart = globalRange.first.coerceIn(0, totalSize - 1)
        val safeEnd = globalRange.last.coerceIn(0, totalSize - 1)
        if (safeStart > safeEnd) return emptyList()

        val ranges = mutableListOf<SegmentWindowRange<SEG>>()
        var cursor = 0
        entries.forEach { entry ->
            val entryStart = cursor
            val entryEnd = cursor + entry.length - 1
            val intersectStart = maxOf(safeStart, entryStart)
            val intersectEnd = minOf(safeEnd, entryEnd)
            if (intersectStart <= intersectEnd) {
                ranges += SegmentWindowRange(
                    segment = entry.segment,
                    globalStart = intersectStart,
                    globalEnd = intersectEnd,
                    offsetStart = entry.startOffsetInSegment + (intersectStart - entryStart),
                    offsetEnd = entry.startOffsetInSegment + (intersectEnd - entryStart)
                )
            }
            cursor += entry.length
        }
        return ranges
    }

    private fun SEG.logicalLength(): Int {
        return totalCount ?: (loadedCount + pageSize)
    }

    private data class Entry<SEG : IQueueSegmentEntity>(
        val segment: SEG,
        val startOffsetInSegment: Int,
        val length: Int
    )
}



