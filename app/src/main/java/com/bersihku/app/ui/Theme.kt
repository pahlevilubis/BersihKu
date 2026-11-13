package com.bersihku.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bersihku.app.data.FileType

val Mint = Color(0xFF5EE6B5)
val AmberSoft = Color(0xFFFFC46B)
val Danger = Color(0xFFFF6B6B)
val SystemGray = Color(0xFF52615D)

private val BersihKuColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0F3B2F),
    onPrimaryContainer = Color(0xFFB8F5DD),
    secondary = AmberSoft,
    onSecondary = Color(0xFF3A2600),
    secondaryContainer = Color(0xFF3A2C10),
    onSecondaryContainer = Color(0xFFFFE2B5),
    error = Danger,
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF4A1515),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0C1412),
    onBackground = Color(0xFFE6EEEB),
    surface = Color(0xFF0C1412),
    onSurface = Color(0xFFE6EEEB),
    surfaceVariant = Color(0xFF1E2A27),
    onSurfaceVariant = Color(0xFFA3B3AE),
    surfaceContainerLowest = Color(0xFF080E0C),
    surfaceContainerLow = Color(0xFF111B18),
    surfaceContainer = Color(0xFF15211E),
    surfaceContainerHigh = Color(0xFF1B2825),
    surfaceContainerHighest = Color(0xFF22302C),
    outline = Color(0xFF41524D),
    outlineVariant = Color(0xFF26332F),
)

fun typeColor(t: FileType): Color = when (t) {
    FileType.FOTO -> Color(0xFF5EE6B5)
    FileType.VIDEO -> Color(0xFF6BB8FF)
    FileType.AUDIO -> Color(0xFFC59BFF)
    FileType.DOKUMEN -> Color(0xFFFFC46B)
    FileType.APK -> Color(0xFFFF8A65)
    FileType.ARSIP -> Color(0xFFF48FB1)
    FileType.LAINNYA -> Color(0xFF90A4AE)
}

@Composable
fun BersihKuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BersihKuColors, content = content)
}
