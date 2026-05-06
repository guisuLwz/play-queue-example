package com.qytech.play_queue.data

data class QueueMutationResult(
    val firstInsertedPosition: Int?,
    val autoPlayPosition: Int?
) {
    val inserted: Boolean get() = firstInsertedPosition != null

    companion object {
        val Noop = QueueMutationResult(
            firstInsertedPosition = null,
            autoPlayPosition = null
        )
    }
}