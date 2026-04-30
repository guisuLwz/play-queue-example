package com.qytech.play_queue_example.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qytech.play_queue_example.state.QueueSegmentLoadState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SegmentStateStrip(
    states: List<QueueSegmentLoadState>,
    totalCount: Int,
    visibleWindow: IntRange,
    selectedSegmentId: String?,
    onDeleteSegment: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = "逻辑总量 $totalCount，内存窗口 ${visibleWindow.first}..${visibleWindow.last}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF686862)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            states.forEach { state ->
                var menuExpanded by remember(state.segmentId) { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    onDeleteSegment(state.segmentId)
                                }
                            ),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.segmentId == selectedSegmentId || state.isSelected) {
                                Color(0xFFE2F1EB)
                            } else {
                                Color.White
                            }
                        )
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                text = state.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "缓存 ${state.cachedCount}/${state.totalCount ?: "?"} · 每页 ${state.pageSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF686862)
                            )
                            if (state.isLoading) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}