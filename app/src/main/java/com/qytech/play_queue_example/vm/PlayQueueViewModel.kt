package com.qytech.play_queue_example.vm

import com.qytech.play_queue_example.base.BaseViewModel
import com.qytech.play_queue_example.state.PlayQueueUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class PlayQueueViewModel @Inject constructor(

): BaseViewModel() {

    val uiState = MutableStateFlow(PlayQueueUiState())

}