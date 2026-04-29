package com.qytech.play_queue_example.data

data class QueueActionLabels(
    val playNow: String,
    val insertNext: String,
    val appendToEnd: String,
) {
    companion object {
        val Playlist = QueueActionLabels(
            playNow = "播放当前歌单",
            insertNext = "将歌单插入到当前播放的下一首播放",
            appendToEnd = "追加到播放列表的队尾播放",
        )

        val Song = QueueActionLabels(
            playNow = "播放当前歌曲",
            insertNext = "将歌曲插入到当前播放的下一首播放",
            appendToEnd = "追加到播放列表的队尾播放",
        )
    }
}
