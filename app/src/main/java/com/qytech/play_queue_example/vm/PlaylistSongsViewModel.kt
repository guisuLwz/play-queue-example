package com.qytech.play_queue_example.vm

import androidx.paging.map
import androidx.lifecycle.viewModelScope
import com.qytech.play_queue_example.base.BaseViewModel
import com.qytech.play_queue_example.data.QueueAction
import com.qytech.play_queue_example.model.Playlist
import com.qytech.play_queue_example.model.Song
import com.qytech.play_queue_example.model.toModel
import com.qytech.play_queue_example.player.PlaybackQueueController
import com.qytech.play_queue_example.repository.PLAY_QUEUE_PAGE_SIZE
import com.qytech.play_queue_example.repository.PlayQueueRepository
import com.qytech.play_queue_example.repository.SourceRepository
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistSongsViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val playQueueRepository: PlayQueueRepository,
    private val playbackQueueController: PlaybackQueueController
): BaseViewModel() {

    private val queryParams = MutableStateFlow<Playlist?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs = queryParams
        .filterNotNull()
        .flatMapLatest { playlist ->
        sourceRepository.songPager(playlist.id)
            .flow
            .map { pagingData ->
                pagingData.map { entity ->
                    entity.toModel()
                }
            }
    }

    fun onClick(playlist: Playlist, offsetInSegment: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = playQueueRepository.playSegmentFromOffset(
                segment = QueueSegmentEntity(
                    id = playlist.id.toString(),
                    name = playlist.name,
                    coverUrl = null,
                    loadedCount = 0,
                    totalCount = playlist.totalCount,
                    pageSize = PLAY_QUEUE_PAGE_SIZE,
                    hasMore = true,
                    lastError = null,
                    type = "playlist"
                ),
                offsetInSegment = offsetInSegment
            )
            playbackQueueController.applyQueueMutationResult(result)
            playQueueRepository.preloadQueueWindow(window = playQueueRepository.visibleWindow.value)
        }
    }

    fun onAction(song: Song, action: QueueAction) {

    }

    fun updateParams(playlist: Playlist) {
        queryParams.update { playlist }
    }
}
