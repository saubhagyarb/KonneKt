@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Section header in the emphasized title style, with optional trailing action. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Signature expressive badge: a 9-sided cookie behind an icon or letter. */
@Composable
fun cookieShape(): Shape = MaterialShapes.Cookie9Sided.toShape()

@Composable
fun ShapeAvatar(
    shape: Shape,
    container: Color,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(size).clip(shape).background(container),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
fun LetterAvatar(name: String, size: Dp, shape: Shape, container: Color, onContainer: Color) {
    ShapeAvatar(shape, container, size) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
            style = if (size >= 72.dp) MaterialTheme.typography.displaySmall else if (size >= 56.dp) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleMediumEmphasized,
            color = onContainer,
        )
    }
}

@Composable
fun IconAvatar(icon: ImageVector, size: Dp, shape: Shape, container: Color, onContainer: Color) {
    ShapeAvatar(shape, container, size) {
        Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(size / 2))
    }
}

/** Medium (56dp) filled button with shape morph on press: the one primary action of a card. */
@Composable
fun MediumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
        enabled = enabled,
        contentPadding = ButtonDefaults.MediumContentPadding,
    ) {
        ButtonContent(icon, text)
    }
}

@Composable
fun MediumTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
        enabled = enabled,
        contentPadding = ButtonDefaults.MediumContentPadding,
    ) {
        ButtonContent(icon, text)
    }
}

@Composable
fun MediumOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
        enabled = enabled,
        contentPadding = ButtonDefaults.MediumContentPadding,
    ) {
        ButtonContent(icon, text)
    }
}

@Composable
private fun ButtonContent(icon: ImageVector?, text: String) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLargeEmphasized)
}
