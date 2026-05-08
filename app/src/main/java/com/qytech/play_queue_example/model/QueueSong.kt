package com.qytech.play_queue_example.model

import com.qytech.play_queue.model.IQueueSongUiModel

data class QueueSong(
    override val globalPosition: Int,
    override val songId: String,
    override val segmentId: String,
    override val segmentName: String,
    override val name: String,
    override val singerName: String,
    override val durationText: String,
    override val isPlaying: Boolean
): IQueueSongUiModel {
}