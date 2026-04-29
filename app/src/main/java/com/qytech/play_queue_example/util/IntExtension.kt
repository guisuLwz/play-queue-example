package com.qytech.play_queue_example.util

fun Int.toDurationText(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "%d:%02d".format(minutes, seconds)
}