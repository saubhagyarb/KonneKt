@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.apps

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.apps.RunningAppsState
import com.saubh.deskbuddy.protocol.AppWindow
import com.saubh.deskbuddy.protocol.BrowserTab
import com.saubh.deskbuddy.protocol.RunningApp
import com.saubh.deskbuddy.ui.components.ConfirmDialog
import com.saubh.deskbuddy.ui.components.ContentMaxWidth
import com.saubh.deskbuddy.ui.components.InfoCard
import com.saubh.deskbuddy.ui.components.ScreenPadding
import com.saubh.deskbuddy.ui.components.SectionHeader
import com.saubh.deskbuddy.ui.theme.KonnektIcons
import java.util.Date

/** What the user asked to close, held until they confirm. */
private sealed interface CloseRequest {
    data class App(val app: RunningApp) : CloseRequest
    data class Window(val window: AppWindow) : CloseRequest
    data class Tab(val window: AppWindow, val tab: BrowserTab) : CloseRequest
}

@Composable
fun OpenWindowsPane(
    state: RunningAppsState,
    onRefresh: () -> Unit,
    onCloseApp: (RunningApp) -> Unit,
    onCloseWindow: (AppWindow) -> Unit,
    onCloseTab: (AppWindow, BrowserTab) -> Unit,
) {
    var pending by remember { mutableStateOf<CloseRequest?>(null) }
    val context = LocalContext.current
    val updated = remember(state.updatedAtMs) {
        if (state.loaded) DateFormat.getTimeFormat(context).format(Date(state.updatedAtMs)) else null
    }
    val refreshCd = stringResource(R.string.cd_refresh_windows)
    val column = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "header", contentType = "section-header") {
            SectionHeader(
                text = stringResource(R.string.open_title),
                supporting = updated?.let { stringResource(R.string.open_updated_at, it) } ?: stringResource(R.string.open_subtitle),
                modifier = column,
            ) {
                if (state.loading) {
                    LoadingIndicator(Modifier.size(40.dp))
                } else {
                    FilledTonalIconButton(
                        onClick = onRefresh,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.semantics { contentDescription = refreshCd },
                    ) { Icon(KonnektIcons.Refresh, contentDescription = null) }
                }
            }
        }
        when {
            state.apps.isNotEmpty() -> items(state.apps, key = { it.id }, contentType = { "running-app" }) { app ->
                RunningAppCard(
                    app = app,
                    onCloseApp = { pending = CloseRequest.App(app) },
                    onCloseWindow = { pending = CloseRequest.Window(it) },
                    onCloseTab = { window, tab -> pending = CloseRequest.Tab(window, tab) },
                    modifier = column.animateItem(),
                )
            }
            !state.loaded && state.loading -> item(key = "loading", contentType = "empty-state") {
                InfoCard(stringResource(R.string.open_loading), column, loading = true)
            }
            state.loaded -> item(key = "empty", contentType = "empty-state") {
                InfoCard(stringResource(R.string.open_empty), column, icon = KonnektIcons.CheckCircle)
            }
            else -> item(key = "idle", contentType = "empty-state") {
                InfoCard(stringResource(R.string.open_idle), column, icon = KonnektIcons.Info)
            }
        }
    }

    pending?.let { request -> CloseConfirmDialog(request, onCloseApp, onCloseWindow, onCloseTab, onDone = { pending = null }) }
}

@Composable
private fun CloseConfirmDialog(
    request: CloseRequest,
    onCloseApp: (RunningApp) -> Unit,
    onCloseWindow: (AppWindow) -> Unit,
    onCloseTab: (AppWindow, BrowserTab) -> Unit,
    onDone: () -> Unit,
) {
    val (title, text) = when (request) {
        is CloseRequest.App -> stringResource(R.string.close_app_title, request.app.name) to
            pluralStringResource(R.plurals.close_app_text, request.app.windows.size, request.app.windows.size)
        is CloseRequest.Window -> stringResource(R.string.close_window_title) to request.window.title
        is CloseRequest.Tab -> stringResource(R.string.close_tab_title) to request.tab.title
    }
    ConfirmDialog(
        icon = KonnektIcons.Close,
        title = title,
        text = text,
        confirmLabel = stringResource(R.string.close),
        onConfirm = {
            when (request) {
                is CloseRequest.App -> onCloseApp(request.app)
                is CloseRequest.Window -> onCloseWindow(request.window)
                is CloseRequest.Tab -> onCloseTab(request.window, request.tab)
            }
            onDone()
        },
        onDismiss = onDone,
    )
}
