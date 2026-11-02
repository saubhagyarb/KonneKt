@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.ui.theme.KonnektIcons

private const val PIN_LENGTH = 6

/** PIN entry as six large cells; the invisible text field underneath receives the digits. */
@Composable
fun PinDialog(
    attemptsLeft: Int,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = { Icon(KonnektIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.pin_title), style = MaterialTheme.typography.headlineSmallEmphasized) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.pin_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BasicTextField(
                    value = pin,
                    onValueChange = { entered ->
                        if (entered.length <= PIN_LENGTH && entered.all(Char::isDigit)) pin = entered
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.Transparent),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                    modifier = Modifier.focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            PinCells(pin)
                            Box(Modifier.size(1.dp)) { innerTextField() }
                        }
                    },
                )
                if (attemptsLeft < Protocol.MAX_PIN_ATTEMPTS) {
                    Text(
                        pluralStringResource(R.plurals.attempts_left, attemptsLeft, attemptsLeft),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(pin)
                    pin = ""
                },
                shapes = ButtonDefaults.shapes(),
                enabled = pin.length == PIN_LENGTH,
            ) {
                Text(stringResource(R.string.pair))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PinCells(pin: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().widthIn(max = 320.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(PIN_LENGTH) { index ->
            val active = index == pin.length
            val shape = MaterialTheme.shapes.medium
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(shape)
                    .background(if (index < pin.length) scheme.primaryContainer else scheme.surfaceContainerHighest)
                    .border(if (active) 2.dp else 0.dp, if (active) scheme.primary else Color.Transparent, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    pin.getOrNull(index)?.toString() ?: "",
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = scheme.onPrimaryContainer,
                )
            }
        }
    }
}
