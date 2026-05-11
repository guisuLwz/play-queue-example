package com.qytech.play_queue_example.state

import com.qytech.play_queue.state.IQueueSegmentLoadState

data class QueueSegmentLoadState(
    override val segmentId: String,
    override val name: String,
    override val totalCount: Int?,
    override val cachedCount: Int,
    override val pageSize: Int,
    override val isLoading: Boolean,
    override val error: String?,
    override val hasMore: Boolean,
    override val isFullyCached: Boolean = false,
    override val hasPageError: Boolean = false,
    val isSelected: Boolean = false,
) : IQueueSegmentLoadState
