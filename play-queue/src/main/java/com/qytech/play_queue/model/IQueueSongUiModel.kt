package com.qytech.play_queue.model

/**
 * SongUiModel 表示“真正显示到界面上的一首歌”。
 * 它包含播放状态、时长文本、歌单名等 UI 需要的信息。
 */
interface IQueueSongUiModel {
    val globalPosition: Int
    val songId: String
    val segmentId: String
    val segmentType: String
    val segmentName: String
    val name: String
    val singerName: String
    val durationText: String
    val isCurrentPlayable: Boolean
    val isPlayingStatus: Boolean
}