package com.qytech.play_queue_example.data

enum class PlaybackMode {
    ListLoop,
    Sequential,
    SingleLoop,
    Shuffle,
}

val PlaybackMode.label: String
    get() = when (this) {
        PlaybackMode.ListLoop -> "列表循环"
        PlaybackMode.Sequential -> "顺序播放"
        PlaybackMode.SingleLoop -> "单曲循环"
        PlaybackMode.Shuffle -> "随机播放"
    }