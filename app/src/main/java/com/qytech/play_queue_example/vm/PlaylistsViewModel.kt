package com.qytech.play_queue_example.vm

import androidx.lifecycle.viewModelScope
import androidx.paging.map
import com.qytech.play_queue_example.base.BaseViewModel
import com.qytech.play_queue_example.data.QueueAction
import com.qytech.play_queue_example.model.Playlist
import com.qytech.play_queue_example.model.toModel
import com.qytech.play_queue_example.repository.PLAY_QUEUE_PAGE_SIZE
import com.qytech.play_queue_example.repository.PlayQueueRepository
import com.qytech.play_queue_example.repository.SourceRepository
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val playQueueRepository: PlayQueueRepository
) : BaseViewModel() {

    val playlistsPagingData = sourceRepository.playlistPager()
        .flow
        .map { pagingData ->
            pagingData.map { entity ->
                entity.toModel()
            }
        }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            sourceRepository.initPlaylists()
            sourceRepository.initSongs()
        }
    }

    fun onAction(playlist: Playlist, action: QueueAction) {
        viewModelScope.launch(Dispatchers.IO) {
            when (action) {
                QueueAction.PlayNow -> {
                    playQueueRepository.setPlayQueueFirst(
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
                        )
                    )
                }

                QueueAction.InsertNext -> {
                    playQueueRepository.insertSegmentToNext(
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
                        currentGlobalPosition = 2
                    )
                }

                QueueAction.AppendToEnd -> {
                    playQueueRepository.addSegmentToTail(
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
                        )
                    )
                }
            }
            playQueueRepository.preloadQueueWindow(window = playQueueRepository.visibleWindow.value)
        }
    }

}