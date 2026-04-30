package com.qytech.play_queue.domain

import com.qytech.play_queue.data.LocatedPosition
import com.qytech.play_queue.data.SegmentWindowRange
import com.qytech.play_queue.local.IQueueSegmentEntity
import kotlin.math.max
import kotlin.math.min

abstract class BaseGlobalPositionMapper<SEG: IQueueSegmentEntity>(
    private val segments: List<SEG>
) {

    /**
     * starts 保存每个歌单在全局队列里的起始位置。
     * Pair<PlaylistEntity, Int> 表示：某个歌单 + 这个歌单从全局第几个位置开始。
     * 例如 daily 从 0 开始，focus 从 320000 开始。
     */
    protected val starts: List<Pair<SEG, Int>> = buildList {
        var cursor = 0
        segments.forEach { playlist ->
            add(playlist to cursor)
            cursor += playlist.logicalLength()
        }
    }

    /**
     * totalSize 是所有歌单拼接后的总长度。
     * 这只是一个数字，不会创建 totalSize 个对象。
     */
    val totalSize: Int = starts.lastOrNull()?.let { (playlist, start) ->
        // 最后一个歌单的起点 + 最后一个歌单长度 = 全局总长度。
        start + playlist.logicalLength()
        // 如果没有任何歌单，totalSize 就是 0。
    } ?: 0

    /**
     * 把一个全局位置转换成“所属歌单 + 歌单内偏移”。
     * @param globalPosition 整个播放列表的position位置，如100_000的数量里面有3个歌单，现在是2000，属于第一个歌单
     * @return 返回当前位置是哪个歌单，歌单的全局开始位置，当前位置在当前歌单的offset(index)位置
     */
    fun locate(globalPosition: Int): LocatedPosition<SEG>? {
        if (globalPosition !in 0 until totalSize) return null
        val match = starts.lastOrNull { (_, start) -> start <= globalPosition } ?: return null
        val (segment, start) = match
        return LocatedPosition(
            segment = segment,
            startGlobalPosition = start,
            offsetInSegment = globalPosition - start
        )
    }

    /**
     * 把一个全局窗口范围拆成多个歌单内部范围。如全局 319950..320120 可能跨过 daily/focus 两个歌单，需要拆成两个 range。
     * @param globalRange 全局列表的一个窗口范围
     * @return 返回这个返回内，包含的所有歌单信息，歌单信息：
     */
    fun rangesFor(globalRange: IntRange): List<SegmentWindowRange<SEG>> {
        if (globalRange.isEmpty() || totalSize == 0) return emptyList()
        val safeStart = globalRange.first.coerceIn(0, totalSize - 1)
        val safeEnd = globalRange.last.coerceIn(0, totalSize - 1)
        if (safeStart > safeEnd) return emptyList()

        return starts.mapNotNull { (segment, segmentStart) ->
            // 这个歌单的全局结束位置。
            val segmentEnd = segmentStart + segment.logicalLength() - 1
            // 交集起点 = 窗口起点和歌单起点中的较大值。
            val intersectStart = max(safeStart, segmentStart)
            // 交集终点 = 窗口终点和歌单终点中的较小值。
            val intersectEnd = min(safeEnd, segmentEnd)
            // 如果交集起点大于终点，说明这个歌单和窗口无交集。
            if (intersectStart > intersectEnd) {
                // mapNotNull 里返回 null 会被过滤掉。
                null
            } else {
                // 有交集，创建一个 PlaylistWindowRange。
                SegmentWindowRange(
                    // 交集所属歌单。
                    segment = segment,
                    // 交集在全局队列里的起点。
                    globalStart = intersectStart,
                    // 交集在全局队列里的终点。
                    globalEnd = intersectEnd,
                    // 交集在歌单内部的起始偏移。
                    /**
                     * 真正的交集开始位置
                     */
                    offsetStart = intersectStart - segmentStart,
                    // 交集在歌单内部的结束偏移。
                    /**
                     * 真正的交集结束位置
                     */
                    offsetEnd = intersectEnd - segmentStart
                )
            }
        }
    }

    protected fun SEG.logicalLength(): Int {
        return totalCount ?: (loadedCount + pageSize)
    }
}



