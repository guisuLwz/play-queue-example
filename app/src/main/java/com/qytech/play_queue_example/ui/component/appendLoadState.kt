package com.qytech.play_queue_example.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState

fun androidx.compose.foundation.lazy.LazyListScope.appendLoadState(
    loadState: LoadState,
    onRetry: () -> Unit,
) {
    when (loadState) {
        is LoadState.Loading -> item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "继续加载...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is LoadState.Error -> item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "加载更多失败",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onRetry) {
                    Text("重试")
                }
            }
        }

        is LoadState.NotLoading -> Unit
    }
}