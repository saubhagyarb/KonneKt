package com.saubh.deskbuddy.share

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.FileCancelCommand
import com.saubh.deskbuddy.protocol.FileChunkCommand
import com.saubh.deskbuddy.protocol.FileCompleteCommand
import com.saubh.deskbuddy.protocol.FileOfferCommand
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.session.RemoteSession
import com.saubh.deskbuddy.transfer.FileChunkCodec
import com.saubh.deskbuddy.transfer.FileNames
import com.saubh.deskbuddy.transfer.TransferIds
import com.saubh.deskbuddy.transfer.TransferTracker
import com.saubh.deskbuddy.ui.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Sends files picked on the phone and receives files pushed by the desktop. */
class FileTransferController(
    private val session: RemoteSession,
    private val resolver: ContentResolver,
    private val store: ReceivedFileStore,
    private val cacheDir: File,
    private val scope: CoroutineScope,
    private val onReceived: (InboxItem.File) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _progress = MutableStateFlow<TransferProgress?>(null)
    val progress: StateFlow<TransferProgress?> = _progress.asStateFlow()

    private var sendJob: Job? = null

    // Receive-side state; touched only by the single collector coroutine below.
    private val tracker = TransferTracker()
    private val targets = HashMap<String, ReceivedFileStore.Target>()

    init {
        scope.launch(Dispatchers.IO) {
            session.incoming.filterIsInstance<Push>().collect { onPushed(it.command) }
        }
    }

    fun sendFile(uri: Uri) {
        if (sendJob?.isActive == true) return
        sendJob = scope.launch(Dispatchers.IO) {
            try {
                sendNow(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // disconnected mid-transfer; the session already reported it
            } catch (e: Exception) {
                session.reportError(ErrorKind.FILE_UNREADABLE)
            } finally {
                _progress.value = null
            }
        }
    }

    fun cancelSend() {
        sendJob?.cancel()
        sendJob = null
        _progress.value = null
    }

    /** Copies share-sheet content into the cache so the URI grant lifetime no longer matters. */
    suspend fun stashForLater(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            if (querySize(uri)?.let { it > Protocol.MAX_FILE_TRANSFER_BYTES } == true) return@runCatching null
            val name = FileNames.sanitize(queryName(uri) ?: "shared")
            val dir = File(cacheDir, "pending_share").apply { mkdirs() }
            val file = File(dir, "${now()}_$name")
            try {
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(Protocol.FILE_CHUNK_BYTES)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            if (copied > Protocol.MAX_FILE_TRANSFER_BYTES - read) throw IOException("Shared file is too large")
                            output.write(buffer, 0, read)
                            copied += read
                        }
                    }
                } ?: return@runCatching null
            } catch (error: Exception) {
                file.delete()
                throw error
            }
            Uri.fromFile(file)
        }.getOrNull()
    }

    private suspend fun sendNow(uri: Uri) {
        val name = queryName(uri) ?: "file"
        val size = querySize(uri) ?: measure(uri)
        if (size > Protocol.MAX_FILE_TRANSFER_BYTES) {
            session.reportError(ErrorKind.TRANSFER_FAILED)
            return
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val id = TransferIds.next()
        if (!session.send(FileOfferCommand(id, name, size, mime))) return
        _progress.value = TransferProgress(name, 0, size, outgoing = true)
        try {
            val stream = resolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open $uri")
            stream.use {
                val buffer = ByteArray(Protocol.FILE_CHUNK_BYTES)
                var index = 0
                var sent = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    if (!session.send(FileChunkCommand(id, index++, FileChunkCodec.encode(chunk)))) {
                        throw IOException("disconnected")
                    }
                    sent += read
                    report(TransferProgress(name, sent, size, outgoing = true))
                }
            }
            session.send(FileCompleteCommand(id))
        } catch (e: CancellationException) {
            session.send(FileCancelCommand(id))
            throw e
        } catch (e: Exception) {
            session.send(FileCancelCommand(id))
            throw e
        }
    }

    private fun onPushed(command: Command) {
        when (command) {
            is FileOfferCommand -> onOffer(command)
            is FileChunkCommand -> onChunk(command)
            is FileCompleteCommand -> onComplete(command.transferId)
            is FileCancelCommand -> abort(command.transferId)
            else -> Unit
        }
    }

    private fun onOffer(offer: FileOfferCommand) {
        if (tracker.offer(offer) !is TransferTracker.Result.Accepted) {
            session.reportError(ErrorKind.TRANSFER_FAILED)
            return
        }
        val target = runCatching { store.open(offer.name, offer.mimeType) }.getOrNull()
        if (target == null) {
            tracker.cancel(offer.transferId)
            session.reportError(ErrorKind.TRANSFER_FAILED)
            return
        }
        targets[offer.transferId] = target
        _progress.value = TransferProgress(offer.name, 0, offer.sizeBytes, outgoing = false)
    }

    private fun onChunk(chunk: FileChunkCommand) {
        val target = targets[chunk.transferId] ?: return
        val bytes = runCatching { FileChunkCodec.decode(chunk.data) }.getOrNull()
        val result = bytes?.let { tracker.chunk(chunk.transferId, chunk.index, it.size) }
        val accepted = result as? TransferTracker.Result.Accepted
        if (bytes == null || accepted == null) {
            abort(chunk.transferId)
            session.reportError(ErrorKind.TRANSFER_FAILED)
            return
        }
        val written = runCatching { target.stream.write(bytes) }.isSuccess
        if (!written) {
            abort(chunk.transferId)
            session.reportError(ErrorKind.TRANSFER_FAILED)
            return
        }
        val incoming = accepted.incoming
        report(TransferProgress(incoming.offer.name, incoming.receivedBytes, incoming.offer.sizeBytes, outgoing = false))
    }

    private fun onComplete(transferId: String) {
        val target = targets.remove(transferId) ?: return
        val result = tracker.complete(transferId)
        _progress.value = null
        if (result is TransferTracker.Result.Accepted) {
            target.finish()
            val offer = result.incoming.offer
            onReceived(InboxItem.File(offer.name, offer.sizeBytes, target.location, now()))
        } else {
            target.abort()
            session.reportError(ErrorKind.TRANSFER_FAILED)
        }
    }

    private fun abort(transferId: String) {
        tracker.cancel(transferId)
        targets.remove(transferId)?.abort()
        _progress.value = null
    }

    /** Per-chunk progress re-renders the Share tab; only whole-percent changes are worth showing. */
    private fun report(progress: TransferProgress) {
        val shown = _progress.value
        if (shown == null || shown.name != progress.name || shown.outgoing != progress.outgoing ||
            percentOf(shown) != percentOf(progress)
        ) {
            _progress.value = progress
        }
    }

    private fun percentOf(p: TransferProgress): Long = if (p.totalBytes > 0) p.doneBytes * 100 / p.totalBytes else 0

    private fun queryName(uri: Uri): String? =
        queryString(uri, OpenableColumns.DISPLAY_NAME) ?: uri.lastPathSegment

    private fun querySize(uri: Uri): Long? =
        queryString(uri, OpenableColumns.SIZE)?.toLongOrNull()?.takeIf { it >= 0 }

    private fun queryString(uri: Uri, column: String): String? {
        val cursor = runCatching { resolver.query(uri, arrayOf(column), null, null, null) }.getOrNull()
            ?: return null
        cursor.use { c ->
            if (!c.moveToFirst() || c.isNull(0)) return null
            return c.getString(0)
        }
    }

    /** Size for providers that do not report one: a full read, then the real send re-reads. */
    private fun measure(uri: Uri): Long {
        val stream = resolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open $uri")
        return stream.use {
            val buffer = ByteArray(Protocol.FILE_CHUNK_BYTES)
            var total = 0L
            while (true) {
                val read = it.read(buffer)
                if (read <= 0) break
                if (total > Protocol.MAX_FILE_TRANSFER_BYTES - read) return Protocol.MAX_FILE_TRANSFER_BYTES + 1
                total += read
            }
            total
        }
    }
}
