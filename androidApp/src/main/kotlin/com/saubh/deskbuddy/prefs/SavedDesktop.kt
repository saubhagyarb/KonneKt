package com.saubh.deskbuddy.prefs

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A PC this phone has paired with. [id] is the PC's stable id (empty for PCs running an older
 * DeskBuddy); [host] is the last address that worked; [lastUsedAt] orders auto-connect.
 */
@Serializable
data class SavedDesktop(
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
    val id: String = "",
    val lastUsedAt: Long = 0L,
    /** SHA-256 of the desktop TLS certificate, stored only after the user confirms its fingerprint. */
    val certificateFingerprint: String = "",
) {
    /** Same PC, even if its address changed: by id when both sides know it, else by name. */
    fun isSamePc(otherId: String?, otherName: String?): Boolean = when {
        id.isNotEmpty() && !otherId.isNullOrEmpty() -> id == otherId
        else -> otherName != null && name.equals(otherName, ignoreCase = true)
    }
}

/** Pure list operations and JSON encoding for the paired-PC list, newest first. */
object SavedDesktops {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedDesktop.serializer())

    fun encode(desktops: List<SavedDesktop>): String = json.encodeToString(serializer, desktops)

    fun decode(text: String?): List<SavedDesktop> =
        text?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    /** Replaces the entry for the same PC (or same address) and keeps the list newest first. */
    fun upsert(list: List<SavedDesktop>, desktop: SavedDesktop): List<SavedDesktop> =
        (list.filterNot { it.isSamePc(desktop.id, desktop.name) || it.host == desktop.host } + desktop)
            .sortedByDescending { it.lastUsedAt }

    fun remove(list: List<SavedDesktop>, desktop: SavedDesktop): List<SavedDesktop> =
        list.filterNot { it.host == desktop.host && it.token == desktop.token }
}
