package com.qytech.play_queue_example.data

import com.qytech.play_queue_example.model.Song

data class QueueState(
    val items: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val playbackMode: PlaybackMode = PlaybackMode.Sequential,
    val shuffleHistory: List<Long> = emptyList(),
) {
    val currentSong: Song?
        get() = items.getOrNull(currentIndex)

    val hasPrevious: Boolean
        get() = when {
            items.isEmpty() -> false
            currentIndex !in items.indices -> true
            playbackMode == PlaybackMode.ListLoop -> true
            playbackMode == PlaybackMode.SingleLoop -> true
            playbackMode == PlaybackMode.Shuffle -> shuffleHistory.isNotEmpty()
            else -> currentIndex > 0
        }

    val hasNext: Boolean
        get() = items.isNotEmpty()
}