package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.FileCancelCommand
import com.saubh.deskbuddy.protocol.FileChunkCommand
import com.saubh.deskbuddy.protocol.FileCompleteCommand
import com.saubh.deskbuddy.protocol.FileOfferCommand
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.transfer.FileChunkCodec
import com.saubh.deskbuddy.transfer.TransferIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Streams a local file to the phone as offer, chunks, then complete pushes. */
class FileSender(private val push: suspend (Message) -> Boolean) {

    data class Progress(val name: String, val sentBytes: Long, val totalBytes: Long)

    suspend fun send(file: Path, onProgress: (Progress) -> Unit = {}): Boolean {
        val id = TransferIds.next()
        val name = file.fileName.toString()
        val size = withContext(Dispatchers.IO) { Files.size(file) }
        if (size > Protocol.MAX_FILE_TRANSFER_BYTES) return false
        val mime = withContext(Dispatchers.IO) { runCatching { Files.probeContentType(file) }.getOrNull() }
            ?: "application/octet-stream"
        if (!push(Push(FileOfferCommand(id, name, size, mime)))) return false
        onProgress(Progress(name, 0, size))
        return try {
            // One IO hop for the whole read loop instead of one per 32 KB chunk.
            withContext(Dispatchers.IO) {
                Files.newInputStream(file).use { input ->
                    val buffer = ByteArray(Protocol.FILE_CHUNK_BYTES)
                    var index = 0
                    var sent = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                        if (!push(Push(FileChunkCommand(id, index++, FileChunkCodec.encode(chunk))))) {
                            throw IOException("phone disconnected")
                        }
                        sent += read
                        onProgress(Progress(name, sent, size))
                    }
                }
            }
            push(Push(FileCompleteCommand(id)))
        } catch (e: CancellationException) {
            push(Push(FileCancelCommand(id)))
            throw e
        } catch (e: Exception) {
            push(Push(FileCancelCommand(id)))
            false
        }
    }
}
