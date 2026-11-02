package com.saubh.deskbuddy.share

import java.util.concurrent.atomic.AtomicLong

private val inboxItemIdSequence = AtomicLong()

private fun nextInboxItemId(): Long = inboxItemIdSequence.incrementAndGet()

sealed class InboxItem {
    abstract val id: Long
    abstract val receivedAt: Long

    data class Text(
        val text: String,
        override val receivedAt: Long,
        override val id: Long = nextInboxItemId(),
    ) : InboxItem()

    data class File(
        val name: String,
        val sizeBytes: Long,
        val location: String,
        override val receivedAt: Long,
        override val id: Long = nextInboxItemId(),
    ) : InboxItem()
}

data class TransferProgress(
    val name: String,
    val doneBytes: Long,
    val totalBytes: Long,
    val outgoing: Boolean,
) {
    val fraction: Float get() = if (totalBytes > 0) doneBytes.toFloat() / totalBytes else 0f
}
