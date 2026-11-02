package com.saubh.deskbuddy.transfer

import com.saubh.deskbuddy.protocol.FileOfferCommand
import com.saubh.deskbuddy.protocol.Protocol

/**
 * Pure bookkeeping for incoming file transfers: enforces one offer per id,
 * strictly ordered chunk indices, and a size check on completion. Callers
 * do the actual I/O.
 */
class TransferTracker {

    data class Incoming(val offer: FileOfferCommand, val nextIndex: Int, val receivedBytes: Long)

    sealed class Result {
        data class Accepted(val incoming: Incoming) : Result()
        data class Rejected(val reason: Reason) : Result()
    }

    enum class Reason { UNKNOWN_TRANSFER, DUPLICATE_TRANSFER, OUT_OF_ORDER, SIZE_MISMATCH, TOO_LARGE, TOO_MANY_TRANSFERS }

    private val active = LinkedHashMap<String, Incoming>()

    val activeIds: Set<String> get() = active.keys

    operator fun get(transferId: String): Incoming? = active[transferId]

    fun offer(offer: FileOfferCommand): Result {
        if (offer.transferId in active) return Result.Rejected(Reason.DUPLICATE_TRANSFER)
        if (offer.sizeBytes < 0) return Result.Rejected(Reason.SIZE_MISMATCH)
        if (offer.sizeBytes > Protocol.MAX_FILE_TRANSFER_BYTES) return Result.Rejected(Reason.TOO_LARGE)
        if (active.size >= Protocol.MAX_CONCURRENT_FILE_TRANSFERS) return Result.Rejected(Reason.TOO_MANY_TRANSFERS)
        val reserved = active.values.sumOf { it.offer.sizeBytes }
        if (offer.sizeBytes > Protocol.MAX_ACTIVE_FILE_TRANSFER_BYTES - reserved) return Result.Rejected(Reason.TOO_LARGE)
        val incoming = Incoming(offer, nextIndex = 0, receivedBytes = 0)
        active[offer.transferId] = incoming
        return Result.Accepted(incoming)
    }

    fun chunk(transferId: String, index: Int, byteCount: Int): Result {
        val current = active[transferId] ?: return Result.Rejected(Reason.UNKNOWN_TRANSFER)
        if (index != current.nextIndex) {
            active.remove(transferId)
            return Result.Rejected(Reason.OUT_OF_ORDER)
        }
        val total = current.receivedBytes + byteCount
        if (total > current.offer.sizeBytes) {
            active.remove(transferId)
            return Result.Rejected(Reason.TOO_LARGE)
        }
        val updated = current.copy(nextIndex = index + 1, receivedBytes = total)
        active[transferId] = updated
        return Result.Accepted(updated)
    }

    /** Removes the transfer either way; success only if every byte arrived. */
    fun complete(transferId: String): Result {
        val current = active.remove(transferId) ?: return Result.Rejected(Reason.UNKNOWN_TRANSFER)
        return if (current.receivedBytes == current.offer.sizeBytes) {
            Result.Accepted(current)
        } else {
            Result.Rejected(Reason.SIZE_MISMATCH)
        }
    }

    fun cancel(transferId: String): Incoming? = active.remove(transferId)
}
