package com.qytech.play_queue.data

import com.qytech.play_queue.local.IQueueSegmentRefEntity

data class DeduplicatedQueueRefs<SEG_REF : IQueueSegmentRefEntity>(
    val refs: List<SEG_REF>,
    val removedGlobalRanges: List<RemovedGlobalRange>
)