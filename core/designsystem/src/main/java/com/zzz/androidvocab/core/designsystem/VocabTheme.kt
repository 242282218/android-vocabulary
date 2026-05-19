package com.zzz.androidvocab.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zzz.androidvocab.core.model.ThemeMode

private val VocabTypography =
    Typography(
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 46.sp,
                lineHeight = 50.sp,
                letterSpacing = (-0.4).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 38.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.2).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 27.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.1).sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                lineHeight = 27.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 23.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.1.sp,
            ),
    )

@Composable
fun VocabTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark =
        when (themeMode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    val colors =
        if (dark) {
            darkColorScheme(
                primary = VocabColors.Primary,
                onPrimary = Color.White,
                primaryContainer = VocabColors.PrimaryDarkSoft,
                onPrimaryContainer = Color(0xFFF6D8C9),
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
                secondaryContainer = Color(0xFF2F4933),
                onSecondaryContainer = Color(0xFFE2F0DE),
                tertiary = VocabColors.Warning,
                tertiaryContainer = Color(0xFF4B3518),
                onTertiaryContainer = Color(0xFFF8E5BE),
                error = VocabColors.Danger,
                errorContainer = Color(0xFF5C2A22),
                onErrorContainer = Color(0xFFF6D5CC),
            )
        } else {
            lightColorScheme(
                primary = VocabColors.Primary,
                onPrimary = Color.White,
                primaryContainer = VocabColors.PrimarySoft,
                onPrimaryContainer = Color(0xFF5B2B1D),
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
                onSecondaryContainer = Color(0xFF284A2F),
                tertiary = VocabColors.Warning,
                tertiaryContainer = VocabColors.WarningSoft,
                onTertiaryContainer = Color(0xFF51340E),
                error = VocabColors.Danger,
                errorContainer = VocabColors.DangerSoft,
                onErrorContainer = Color(0xFF5C2A22),
            )
        }
    MaterialTheme(colorScheme = colors, typography = VocabTypography, content = content)
}
