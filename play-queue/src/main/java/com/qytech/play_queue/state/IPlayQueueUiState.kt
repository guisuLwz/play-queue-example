package com.qytech.play_queue.state

import com.qytech.play_queue.data.PlaybackMode
import com.qytech.play_queue.model.IQueueSongUiModel
import com.qytech.play_queue.model.QueueRow

/**
 * MusicListUiState 表示整个页面一次渲染需要的所有状态。
 * Compose 最适合吃这种“一个不可变状态对象”，状态变了就自动重组。
 */
interface IPlayQueueUiState<S: IQueueSongUiModel, SEG_LOAD_STATE: IQueueSegmentLoadState> {
    val totalCount: Int
    val rowsByPosition: Map<Int, QueueRow>
    val segmentStates: List<SEG_LOAD_STATE>
    val currentPlayingSong: S?
    val isPlaying: Boolean
    val playbackMode: PlaybackMode
    val visibleWindow: IntRange
}