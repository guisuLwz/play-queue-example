package com.qytech.play_queue_example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.qytech.play_queue_example.data.QueueState
import com.qytech.play_queue_example.data.label
import com.qytech.play_queue_example.global.PlaylistSongs
import com.qytech.play_queue_example.global.Playlists
import com.qytech.play_queue_example.ui.screen.PlaylistSongsScreen
import com.qytech.play_queue_example.ui.screen.PlaylistsScreen
import com.qytech.play_queue_example.util.toDurationText
import com.qytech.play_queue_example.vm.MainRouteViewModel

@Composable
fun MainRoute(
    modifier: Modifier = Modifier,
    viewModel: MainRouteViewModel = hiltViewModel()
) {
    val backStack = rememberNavBackStack(Playlists)

    MainRouteContent(
        modifier = modifier
            .fillMaxSize(),
        backStack = backStack,
        queueState = null,
        onTogglePlay = viewModel::onTogglePlay,
        onPrevious = viewModel::onPrevious,
        onNext = viewModel::onNext,
        onPlaybackModeClick = viewModel::onPlaybackModeClick,
        onQueueClick = viewModel::onQueueClick
    )
}

@Composable
private fun MainRouteContent(
    modifier: Modifier = Modifier,
    backStack: NavBackStack<NavKey>,
    queueState: QueueState? = null,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlaybackModeClick: () -> Unit,
    onQueueClick: () -> Unit
) {
    Column(modifier) {
        NavDisplay(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            backStack = backStack,
            onBack = {
                backStack.removeLastOrNull()
            },
            entryProvider = entryProvider {
                entry<Playlists> {
                    PlaylistsScreen(
                        onOpen = { playlist ->
                            backStack.add(PlaylistSongs(playlist))
                        }
                    )
                }

                entry<PlaylistSongs> { route ->
                    PlaylistSongsScreen(route.playlist)
                }
            }
        )

        PlayerController(
            queueState = queueState,
            onTogglePlay = onTogglePlay,
            onPrevious = onPrevious,
            onNext = onNext,
            onPlaybackModeClick = onPlaybackModeClick,
            onQueueClick = onQueueClick
        )
    }
}

@Composable
private fun PlayerController(
    queueState: QueueState? = null,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlaybackModeClick: () -> Unit,
    onQueueClick: () -> Unit,
) {
    val currentSong = queueState?.currentSong

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 104.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "还没有正在播放的歌曲",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentSong?.let { "${it.artist} · ${it.durationSeconds.toDurationText()}" }
                            ?: "从歌单或歌曲菜单添加到播放列表",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onQueueClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "播放列表",
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onPlaybackModeClick,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(queueState?.playbackMode?.label ?: "列表循环")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onPrevious,
                    enabled = queueState?.hasPrevious ?: false,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("上一首")
                }
                IconButton(
                    onClick = onTogglePlay,
                    enabled = queueState?.items?.isNotEmpty() ?: false,
                ) {
                    if (queueState?.isPlaying == true) {
                        Text("暂停", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放",
                        )
                    }
                }
                TextButton(
                    onClick = onNext,
                    enabled = queueState?.hasNext ?: false,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("下一首")
                }
            }
        }
    }
}