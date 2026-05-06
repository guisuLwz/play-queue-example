package com.qytech.play_queue.domain

import com.qytech.play_queue.data.LocatedPosition
import com.qytech.play_queue.data.SegmentWindowRange
import com.qytech.play_queue.local.IQueueSegmentEntity

interface IGlobalPositionMapper<SEG : IQueueSegmentEntity> {
    val totalSize: Int
    fun locate(globalPosition: Int): LocatedPosition<SEG>?
    fun rangesFor(globalRange: IntRange): List<SegmentWindowRange<SEG>>
}