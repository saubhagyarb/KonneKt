package com.saubh.deskbuddy.share

import android.net.Uri

/** Content handed in by the system share sheet, held until a session is connected. */
sealed class PendingShare {
    data class Text(val text: String) : PendingShare()
    data class File(val uri: Uri, val displayName: String? = null) : PendingShare()
}
