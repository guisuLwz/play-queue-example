package com.qytech.play_queue_example.vm

import androidx.lifecycle.viewModelScope
import androidx.paging.map
import com.qytech.play_queue_example.base.BaseViewModel
import com.qytech.play_queue_example.data.QueueAction
import com.qytech.play_queue_example.model.Playlist
import com.qytech.play_queue_example.model.toModel
import com.qytech.play_queue_example.repository.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val sourceRepository: SourceRepository
): BaseViewModel() {

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

    }

}