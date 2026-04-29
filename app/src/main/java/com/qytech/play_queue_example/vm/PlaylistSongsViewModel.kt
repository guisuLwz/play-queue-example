package com.qytech.play_queue_example.vm

import androidx.paging.map
import com.qytech.play_queue_example.base.BaseViewModel
import com.qytech.play_queue_example.model.Playlist
import com.qytech.play_queue_example.model.toModel
import com.qytech.play_queue_example.repository.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class PlaylistSongsViewModel @Inject constructor(
    private val sourceRepository: SourceRepository
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

    fun updateParams(playlist: Playlist) {
        queryParams.update { playlist }
    }
}