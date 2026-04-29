package com.qytech.play_queue_example.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.qytech.play_queue_example.data.QueueAction
import com.qytech.play_queue_example.data.QueueActionLabels
import com.qytech.play_queue_example.model.Playlist
import com.qytech.play_queue_example.ui.component.CoverBlock
import com.qytech.play_queue_example.ui.component.LoadingRow
import com.qytech.play_queue_example.ui.component.QueueActionMenu
import com.qytech.play_queue_example.ui.component.appendLoadState
import com.qytech.play_queue_example.vm.PlaylistsViewModel

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel = hiltViewModel(),
    onOpen: (Playlist) -> Unit
) {

    val playlists = viewModel.playlistsPagingData.collectAsLazyPagingItems()

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
        ) {
            items(playlists.itemCount) { index ->
                val playlist = playlists[index]
                if (playlist == null) {
                    LoadingRow()
                } else {
                    PlaylistRow(
                        playlist = playlist,
                        onOpen = { onOpen(playlist) },
                        onAction = { action -> viewModel.onAction(playlist, action) },
                    )
                }
            }

            appendLoadState(
                loadState = playlists.loadState.append,
                onRetry = playlists::retry,
            )
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onOpen: () -> Unit,
    onAction: (QueueAction) -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverBlock(
                color = Color(playlist.colorArgb),
                text = playlist.id.toString(),
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            QueueActionMenu(
                labels = QueueActionLabels.Playlist,
                onAction = onAction,
            )
        }
    }
}


