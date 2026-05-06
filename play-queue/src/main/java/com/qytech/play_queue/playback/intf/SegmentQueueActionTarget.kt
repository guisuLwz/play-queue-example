package com.qytech.play_queue.playback.intf

import com.qytech.play_queue.data.QueueMutationResult
import com.qytech.play_queue.local.IQueueSegmentEntity

interface SegmentQueueActionTarget<SEG: IQueueSegmentEntity> {
    suspend fun playSegmentNow(segment: SEG): QueueMutationResult

    suspend fun playSegmentFromOffset(
        segment: SEG,
        offsetInSegment: Int
    ): QueueMutationResult

    suspend fun insertSegmentToNext(
        segment: SEG,
        currentGlobalPosition: Int?
    ): QueueMutationResult

    suspend fun addSegmentToTail(segment: SEG): QueueMutationResult
}