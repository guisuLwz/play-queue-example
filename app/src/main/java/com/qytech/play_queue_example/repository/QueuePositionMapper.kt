package com.qytech.play_queue_example.repository

import com.qytech.play_queue.domain.BaseQueuePositionMapper
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentRef

class QueuePositionMapper(
    private val queueRefs: List<QueueSegmentRef>,
    private val segmentsById: Map<String, QueueSegmentEntity>
) : BaseQueuePositionMapper<QueueSegmentEntity, QueueSegmentRef>(
    queueRefs = queueRefs,
    segmentsById = segmentsById
)
