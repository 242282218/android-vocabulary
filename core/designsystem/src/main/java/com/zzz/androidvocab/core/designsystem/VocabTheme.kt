package com.zzz.androidvocab.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzz.androidvocab.core.model.ThemeMode

private val VocabShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = VocabControlShape,
        medium = VocabCardShape,
        large = VocabCardShape,
        extraLarge = VocabHeroShape,
    )

private val VocabTypography =
    Typography(
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 46.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 38.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 27.sp,
                lineHeight = 34.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                letterSpacing = 0.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                letterSpacing = 0.3.sp,
            ),
    )

@Composable
fun VocabTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = shouldUseDarkTheme(themeMode)
    val colors = if (dark) darkVocabColorScheme() else lightVocabColorScheme()
    val extendedColors = if (dark) DarkVocabExtendedColors else LightVocabExtendedColors
    CompositionLocalProvider(LocalVocabExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = VocabTypography,
            shapes = VocabShapes,
            content = content,
        )
    }
}

@Composable
private fun shouldUseDarkTheme(themeMode: ThemeMode): Boolean =
    when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

private fun darkVocabColorScheme(): ColorScheme =
    darkColorScheme(
        primary = VocabColors.Primary,
        onPrimary = Color.White,
        primaryContainer = VocabColors.PrimaryDarkSoft,
        onPrimaryContainer = Color(0xFFD8E3FF),
        background = VocabColors.DarkBackground,
        onBackground = VocabColors.TextPrimaryDark,
        surface = VocabColors.DarkSurface,
        onSurface = VocabColors.TextPrimaryDark,
        surfaceVariant = VocabColors.DarkSurfaceMuted,
        onSurfaceVariant = VocabColors.TextSecondaryDark,
        outline = VocabColors.DarkOutline,
        outlineVariant = VocabColors.DarkOutline,
        secondary = VocabColors.Success,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF1D3629),
        onSecondaryContainer = Color(0xFFDFF0E5),
        tertiary = VocabColors.Warning,
        tertiaryContainer = Color(0xFF3D2E14),
        onTertiaryContainer = Color(0xFFF6E3BE),
        error = VocabColors.Danger,
        errorContainer = Color(0xFF441A1D),
        onErrorContainer = Color(0xFFFFD9D6),
    )

private fun lightVocabColorScheme(): ColorScheme =
    lightColorScheme(
        primary = VocabColors.Primary,
        onPrimary = Color.White,
        primaryContainer = VocabColors.PrimarySoft,
        onPrimaryContainer = Color(0xFF173680),
        background = VocabColors.LightBackground,
        onBackground = VocabColors.TextPrimaryLight,
        surface = VocabColors.LightSurface,
        onSurface = VocabColors.TextPrimaryLight,
        surfaceVariant = VocabColors.LightSurfaceMuted,
        onSurfaceVariant = VocabColors.TextSecondaryLight,
        outline = VocabColors.LightOutline,
        outlineVariant = VocabColors.LightOutline,
        secondary = VocabColors.Success,
        onSecondary = Color.White,
        secondaryContainer = VocabColors.SuccessSoft,
        onSecondaryContainer = Color(0xFF1E3F28),
        tertiary = VocabColors.Warning,
        tertiaryContainer = VocabColors.WarningSoft,
        onTertiaryContainer = Color(0xFF4A330B),
        error = VocabColors.Danger,
        errorContainer = VocabColors.DangerSoft,
        onErrorContainer = Color(0xFF621919),
    )

object VocabThemeExtras {
    val colors: VocabExtendedColors
        @Composable get() = LocalVocabExtendedColors.current
}
