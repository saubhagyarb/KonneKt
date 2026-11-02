@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/**
 * KonneKt M3 Expressive theme shared by the phone and desktop apps: brand color scheme,
 * restrained system motion, the extended shape scale and emphasized type styles.
 */
@Composable
fun KonnektTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) KonnektColors.Dark else KonnektColors.Light,
        motionScheme = MotionScheme.standard(),
        shapes = Shapes(),
        typography = Typography(),
        content = content,
    )
}
