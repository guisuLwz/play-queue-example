package com.qytech.play_queue_example.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.qytech.play_queue_example.base.BaseViewModel
import com.qytech.play_queue_example.data.QueueAction
import com.qytech.play_queue_example.data.QueueActionLabels
import com.qytech.play_queue_example.model.Playlist
import com.qytech.play_queue_example.model.Song
import com.qytech.play_queue_example.ui.component.CoverBlock
import com.qytech.play_queue_example.ui.component.EmptyState
import com.qytech.play_queue_example.ui.component.ErrorState
import com.qytech.play_queue_example.ui.component.LoadingRow
import com.qytech.play_queue_example.ui.component.LoadingState
import com.qytech.play_queue_example.ui.component.QueueActionMenu
import com.qytech.play_queue_example.ui.component.appendLoadState
import com.qytech.play_queue_example.util.toDurationText
import com.qytech.play_queue_example.vm.PlaylistSongsViewModel

@Composable
fun PlaylistSongsScreen(
    playlist: Playlist,
    viewModel: PlaylistSongsViewModel = hiltViewModel()
) {
    val songs = viewModel.songs.collectAsLazyPagingItems()

    LaunchedEffect(playlist) {
        viewModel.updateParams(playlist)
    }

    PagedContent(
        loadState = songs.loadState.refresh,
        itemCount = songs.itemCount,
        emptyText = "这个歌单还没有歌曲",
        onRetry = songs::retry,
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {  }
            },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PlaylistHeader(playlist = playlist)
            }

            items(songs.itemCount) { index ->
                val song = songs[index]
                if (song == null) {
                    LoadingRow()
                } else {
                    SongRow(
                        song = song,
                        position = index + 1,
                        isCurrent = false,
                        isPlaying = false,
                        onClick = { viewModel.onClick(song) },
                        onAction = { action -> viewModel.onAction(song, action) },
                    )
                }
            }

            appendLoadState(
                loadState = songs.loadState.append,
                onRetry = songs::retry,
            )
        }
    }

}

@Composable
private fun PagedContent(
    loadState: LoadState,
    itemCount: Int,
    emptyText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            loadState is LoadState.Loading -> LoadingState()
            loadState is LoadState.Error -> ErrorState(
                message = loadState.error.message ?: "加载失败",
                onRetry = onRetry,
            )
            itemCount == 0 -> EmptyState(text = emptyText)
            else -> content()
        }
    }
}

@Composable
private fun PlaylistHeader(playlist: Playlist) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverBlock(
            color = Color(playlist.colorArgb),
            text = playlist.id.toString(),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${playlist.songCount} 首歌曲 · ${playlist.subtitle}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    position: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAction: (QueueAction) -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isCurrent && isPlaying) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                    )
                } else {
                    Text(
                        text = if (isCurrent) "暂停" else position.toString(),
                        color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${song.artist} · ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (isPlaying) "播放中" else "已暂停",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = song.durationSeconds.toDurationText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QueueActionMenu(
                labels = QueueActionLabels.Song,
                onAction = onAction,
            )
        }
    }
}