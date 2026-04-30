package com.qytech.play_queue.model

/**
 * QueueRow 表示 LazyColumn 里的一行。
 * 这个列表里不只有真实歌曲，还可能有占位行、错误行，所以用 sealed interface 表示有限的几种行类型。
 */
sealed interface QueueRow {
    val globalPosition: Int
    val segmentId: String
    val segmentName: String

    data class SongRow<S: IQueueSongUiModel>(
        val song: S
    ): QueueRow {
        override val globalPosition: Int = song.globalPosition
        override val segmentId: String = song.segmentId
        override val segmentName: String = song.segmentName
    }

    data class PlaceholderRow(
        override val globalPosition: Int,
        override val segmentId: String,
        override val segmentName: String,
        val offsetInSegment: Int,
        val page: Int,
        val isPageLoading: Boolean,
        val isOutsideMemoryWindow: Boolean
    ) : QueueRow

    data class ErrorRow(
        override val globalPosition: Int,
        override val segmentId: String,
        override val segmentName: String,
        val page: Int,
        val message: String
    ) : QueueRow
}