package com.qytech.play_queue.data

enum class PlaybackMode {
    Sequence,
    RepeatAll,
    RepeatOne,
    Shuffle
}

val PlaybackMode.label: String
    get() = when (this) {
        PlaybackMode.RepeatAll -> "列表循环"
        PlaybackMode.Sequence -> "顺序播放"
        PlaybackMode.RepeatOne -> "单曲循环"
        PlaybackMode.Shuffle -> "随机播放"
    }