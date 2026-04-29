package com.qytech.play_queue_example.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.qytech.play_queue_example.data.QueueAction
import com.qytech.play_queue_example.data.QueueActionLabels

@Composable
fun QueueActionMenu(
    labels: QueueActionLabels,
    onAction: (QueueAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "播放选项",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(labels.playNow) },
                onClick = {
                    expanded = false
                    onAction(QueueAction.PlayNow)
                },
            )
            DropdownMenuItem(
                text = { Text(labels.insertNext) },
                onClick = {
                    expanded = false
                    onAction(QueueAction.InsertNext)
                },
            )
            DropdownMenuItem(
                text = { Text(labels.appendToEnd) },
                onClick = {
                    expanded = false
                    onAction(QueueAction.AppendToEnd)
                },
            )
        }
    }
}