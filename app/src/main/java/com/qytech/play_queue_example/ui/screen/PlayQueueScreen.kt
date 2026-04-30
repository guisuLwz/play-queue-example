package com.qytech.play_queue_example.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.qytech.play_queue.model.QueueRow
import com.qytech.play_queue_example.model.QueueSong
import com.qytech.play_queue_example.ui.component.EmptyListMessage
import com.qytech.play_queue_example.ui.component.SegmentStateStrip
import com.qytech.play_queue_example.vm.PlayQueueViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun PlayQueueScreen(
    viewModel: PlayQueueViewModel = hiltViewModel()
) {

    val state by viewModel.uiState.collectAsState()
    // 监听lazyColumn当前的状态用的
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // ui显示框
    val visibleRange by remember {
        derivedStateOf {
            val items = listState.layoutInfo.visibleItemsInfo
            val first = items.firstOrNull()?.index ?: 0
            val last = items.lastOrNull()?.index ?: 0
            first to last
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { visibleRange } //这里转成flow的好处是，可以让他重新计算一次，然后检测是否重复，重复的话过滤掉
            .distinctUntilChanged()
            .collect { (first, last) -> viewModel.onVisibleRangeChanged(first, last) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SegmentStateStrip(
                states = emptyList(),
                totalCount = 0,
                visibleWindow = 0..0,
                selectedSegmentId = "",
            )
            if (state.totalCount == 0) {
                EmptyListMessage()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 关键点：使用 count，而不是 items(state.rows)。
                    // count 可以是 1000000；Compose 只组合屏幕附近的 item。
                    items(
                        count = state.totalCount,
                        key = { index -> "queue-$index" }
                    ) { index ->
                        when (val row = state.rowsByPosition[index]) {
                            is QueueRow.SongRow<*> -> QueueSongRow(
                                song = row.song as QueueSong,
                                onPlay = {

                                }
                            )

                            is QueueRow.PlaceholderRow -> QueuePlaceholderRow(row)
                            is QueueRow.ErrorRow -> QueueErrorRow(
                                row = row,
                                onRetry = { _, _ ->

                                }
                            )

                            null -> QueueLightweightRow(index)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSongRow(song: QueueSong, onPlay: (QueueSong) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(76.dp)
            .clickable { onPlay(song) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (song.isPlaying) Color(0xFFE2F1EB) else Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (song.isPlaying) Color(0xFF176B63) else Color(0xFFECEAE0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (song.isPlaying) "II" else ">",
                    color = if (song.isPlaying) Color.White else Color(0xFF3B3B36),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${song.artist} · ${song.segmentName}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF686862)
                )
            }
            Text(song.durationText, color = Color(0xFF686862))
        }
    }
}

@Composable
private fun QueuePlaceholderRow(row: QueueRow.PlaceholderRow) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(64.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (row.isPageLoading) Color(0xFFE8F0ED) else Color(0xFFF0EFE8)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (row.isPageLoading) "正在加载这一页" else "等待按需加载",
                modifier = Modifier.weight(1f),
                color = Color(0xFF686862)
            )
            Text(
                text = "${row.segmentName} #${row.offsetInSegment + 1} / p${row.page}",
                color = Color(0xFF686862)
            )
        }
    }
}

@Composable
private fun QueueErrorRow(row: QueueRow.ErrorRow, onRetry: (String, Int) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(88.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFECE8))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${row.segmentName} 第 ${row.page} 页加载失败",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    row.message,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF7D392F)
                )
            }
            Button(onClick = { onRetry(row.segmentId, row.page) }) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun QueueLightweightRow(index: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(56.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF4F3EC)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("窗口外轻量占位 #$index", color = Color(0xFF686862))
        }
    }
}

