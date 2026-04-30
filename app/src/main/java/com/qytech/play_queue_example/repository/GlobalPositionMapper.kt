package com.qytech.play_queue_example.repository

import com.qytech.play_queue.domain.BaseGlobalPositionMapper
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentEntity

class GlobalPositionMapper(
    segments: List<QueueSegmentEntity>
) : BaseGlobalPositionMapper<QueueSegmentEntity>(
    segments = segments
)
