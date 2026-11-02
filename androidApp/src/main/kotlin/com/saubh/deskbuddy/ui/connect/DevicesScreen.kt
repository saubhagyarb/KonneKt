@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.client.DiscoveredDesktop
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.ui.AppViewModelProvider
import com.saubh.deskbuddy.ui.components.ContentMaxWidth
import com.saubh.deskbuddy.ui.components.ForgetPcDialog
import com.saubh.deskbuddy.ui.components.InfoCard
import com.saubh.deskbuddy.ui.components.MediumOutlinedButton
import com.saubh.deskbuddy.ui.components.SectionHeader
import com.saubh.deskbuddy.ui.theme.KonnektIcons

@Composable
fun DevicesRoute(
    snackbarHostState: SnackbarHostState,
    viewModel: DevicesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Tied to the started state, so the costly subnet sweep stops when the app is backgrounded.
    LifecycleStartEffect(Unit) {
        viewModel.onVisibleChanged(true)
        onStopOrDispose { viewModel.onVisibleChanged(false) }
    }
    DevicesScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onConnect = viewModel::connect,
        onReconnect = viewModel::reconnect,
        onForget = viewModel::forget,
        onConnectManual = viewModel::connectManual,
        onSubmitPin = viewModel::submitPin,
        onTrustCertificate = viewModel::trustCertificate,
        onCancel = viewModel::cancel,
    )
}

/** Not connected: paired PCs first (one tap), then PCs found nearby, then connect-by-address. */
@Composable
fun DevicesScreen(
    state: DevicesUiState,
    snackbarHostState: SnackbarHostState,
    onConnect: (DiscoveredDesktop) -> Unit,
    onReconnect: (SavedDesktop) -> Unit,
    onForget: (SavedDesktop) -> Unit,
    onConnectManual: (String) -> Unit,
    onSubmitPin: (String) -> Unit,
    onTrustCertificate: () -> Unit,
    onCancel: () -> Unit,
) {
    var forgetting by remember { mutableStateOf<SavedDesktop?>(null) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = { Text(stringResource(R.string.devices_subtitle)) },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val column = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
            state.connectingTo?.let { target ->
                item(key = "connecting", contentType = "connecting-banner") { ConnectingBanner(target, onCancel, column) }
            }
            if (state.saved.isEmpty()) {
                item(key = "start", contentType = "get-started") { GetStartedCard(column) }
            } else {
                item(key = "saved-header", contentType = "section-header") {
                    SectionHeader(stringResource(R.string.devices_your_pcs), column.padding(top = 8.dp), stringResource(R.string.devices_your_pcs_hint))
                }
                items(state.saved, key = { "s:" + it.host + it.token }, contentType = { "saved-pc" }) { pc ->
                    SavedPcCard(pc, onConnect = { onReconnect(pc) }, onForget = { forgetting = pc }, modifier = column.animateItem())
                }
            }
            item(key = "nearby-header", contentType = "section-header") {
                SectionHeader(stringResource(R.string.devices_nearby), column.padding(top = 8.dp)) {
                    if (state.searching) LoadingIndicator(Modifier.size(32.dp))
                }
            }
            if (state.nearby.isEmpty()) {
                item(key = "nearby-empty", contentType = "empty-state") {
                    InfoCard(
                        text = stringResource(R.string.devices_searching_hint),
                        title = stringResource(R.string.devices_searching),
                        icon = KonnektIcons.Wifi,
                        modifier = column,
                    )
                }
            } else {
                items(state.nearby, key = { "n:" + it.host }, contentType = { "nearby-pc" }) { desktop ->
                    NearbyPcCard(desktop, onClick = { onConnect(desktop) }, modifier = column.animateItem())
                }
            }
            item(key = "manual", contentType = "manual-connect") {
                MediumOutlinedButton(
                    text = stringResource(R.string.devices_enter_ip),
                    onClick = { showManual = true },
                    icon = KonnektIcons.Edit,
                    modifier = column.padding(top = 8.dp),
                )
            }
        }
    }

    if (showManual) {
        ManualConnectSheet(
            onConnect = { host ->
                showManual = false
                onConnectManual(host)
            },
            onDismiss = { showManual = false },
        )
    }
    state.pairingAttemptsLeft?.let { attempts ->
        PinDialog(attemptsLeft = attempts, onSubmit = onSubmitPin, onDismiss = onCancel)
    }
    state.certificateToVerify?.let { verification ->
        CertificateTrustDialog(
            desktopName = verification.target,
            fingerprint = verification.fingerprint,
            onTrust = onTrustCertificate,
            onCancel = onCancel,
        )
    }
    forgetting?.let { pc ->
        ForgetPcDialog(pc.name, onConfirm = { onForget(pc); forgetting = null }, onDismiss = { forgetting = null })
    }
}
