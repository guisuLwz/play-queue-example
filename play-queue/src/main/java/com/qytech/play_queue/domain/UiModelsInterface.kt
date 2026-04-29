package com.qytech.play_queue.domain

enum class PlaybackMode {
    Sequence,
    RepeatAll,
    RepeatOne,
    Shuffle
}

/**
 * SegmentLoadState 表示“一个歌单在界面上展示出来的加载状态”。
 * 注意：它不是数据库表，也不是网络返回，它是给 UI 用的状态模型。
 */
interface ISegmentLoadState {
    val segmentId: String
    val name: String
    val totalCount: Int?
    val cachedCount: Int
    val pageSize: Int
    val isLoading: Boolean
    val error: String?
    val hasMore: Boolean
}

/**
 * SongUiModel 表示“真正显示到界面上的一首歌”。
 * 它包含播放状态、时长文本、歌单名等 UI 需要的信息。
 */
interface ISongUiModel {
    val uiGlobalPosition: Int
    val dbGlobalPosition: String
    val songId: String
    val segmentId: String
    val segmentName: String
    val name: String
    val artist: String
    val durationText: String
    val isPlaying: Boolean
}

/**
 * QueueRow 表示 LazyColumn 里的一行。
 * 这个列表里不只有真实歌曲，还可能有占位行、错误行，所以用 sealed interface 表示有限的几种行类型。
 */
sealed interface QueueRow {
    val uiGlobalPosition: Int
    val dbGlobalPosition: String
    val segmentId: String
    val segmentName: String

    data class SongRow<S: ISongUiModel>(
        val song: S
    ): QueueRow {
        override val uiGlobalPosition: Int = song.uiGlobalPosition
        override val dbGlobalPosition: String = song.dbGlobalPosition
        override val segmentId: String = song.segmentId
        override val segmentName: String = song.segmentName
    }

    data class PlaceholderRow(
        override val segmentId: String,
        override val segmentName: String,
        val offsetInPlaylist: Int,
        val page: Int,
        val isPageLoading: Boolean,
        val isOutsideMemoryWindow: Boolean,
        override val uiGlobalPosition: Int,
        override val dbGlobalPosition: String
    ) : QueueRow

    data class ErrorRow(
        override val segmentId: String,
        override val segmentName: String,
        val page: Int,
        val message: String,
        override val uiGlobalPosition: Int,
        override val dbGlobalPosition: String
    ) : QueueRow
}

/**
 * MusicListUiState 表示整个页面一次渲染需要的所有状态。
 * Compose 最适合吃这种“一个不可变状态对象”，状态变了就自动重组。
 */
interface IMusicListUiState<S: ISongUiModel, SEG_LOAD_STATE: ISegmentLoadState> {
    val totalCount: Int
    val rowsByPosition: Map<Int, QueueRow>
    val playlistStates: List<SEG_LOAD_STATE>
    val currentPlayingSong: S?
    val isPlaying: Boolean
    val playbackMode: PlaybackMode
    val visibleWindow: IntRange
}
