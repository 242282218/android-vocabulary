package com.zzz.androidvocab.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object VocabColors {
    // Background & surface
    val LightBackground = Color(0xFFF5F5F7)
    val DarkBackground = Color(0xFF0A0A0E)
    val LightSurface = Color(0xFFFFFFFF)
    val DarkSurface = Color(0xFF16161B)
    val LightSurfaceElevated = Color(0xFFFFFFFF)
    val DarkSurfaceElevated = Color(0xFF1E1E25)
    val LightSurfaceMuted = Color(0xFFEAECEF)
    val DarkSurfaceMuted = Color(0xFF222229)
    val LightOutline = Color(0xFFD5D9E2)
    val DarkOutline = Color(0xFF2E2E38)

    // Primary: refined indigo-blue, deeper than Tailwind defaults
    val Primary = Color(0xFF1D4FD6)
    val PrimarySoft = Color(0xFFDAE3FC)
    val PrimaryDarkSoft = Color(0xFF15357A)

    // Semantic
    val Success = Color(0xFF28845A)
    val SuccessSoft = Color(0xFFD9EDDF)
    val Danger = Color(0xFFD44040)
    val DangerSoft = Color(0xFFF8DCDA)
    val Warning = Color(0xFFBF7D16)
    val WarningSoft = Color(0xFFF4E1BC)

    // Text
    val TextPrimaryLight = Color(0xFF1A1A1F)
    val TextPrimaryDark = Color(0xFFF2F2F5)
    val TextSecondaryLight = Color(0xFF5E5E68)
    val TextSecondaryDark = Color(0xFFA5A9B6)

    // Hero card
    val HeroBackground = Color(0xFFEFF2FA)
    val HeroBackgroundDark = Color(0xFF121520)
    val HeroBorder = Color(0xFFD0D7E6)
    val HeroBorderDark = Color(0xFF272E3E)

    // Error card
    val ErrorCardBackground = Color(0xFFFCEEEF)
    val ErrorCardBackgroundDark = Color(0xFF281215)

    // Heatmap: warm-cool progression for activity visualization
    val HeatmapLevel1 = Color(0xFFE0E4EC)
    val HeatmapLevel2 = Color(0xFFC2CCDD)
    val HeatmapLevel3 = Color(0xFF7EA99A)
    val HeatmapLevel4 = Color(0xFF28845A)
    val HeatmapLevel1Dark = Color(0xFF1F2228)
    val HeatmapLevel2Dark = Color(0xFF243040)
    val HeatmapLevel3Dark = Color(0xFF2D4D42)
    val HeatmapLevel4Dark = Color(0xFF52A068)
}

@Immutable
data class VocabExtendedColors(
    val heroBackground: Color,
    val heroBorder: Color,
    val errorCardBackground: Color,
    val heatmapLevel1: Color,
    val heatmapLevel2: Color,
    val heatmapLevel3: Color,
    val heatmapLevel4: Color,
)

internal val LightVocabExtendedColors =
    VocabExtendedColors(
        heroBackground = VocabColors.HeroBackground,
        heroBorder = VocabColors.HeroBorder,
        errorCardBackground = VocabColors.ErrorCardBackground,
        heatmapLevel1 = VocabColors.HeatmapLevel1,
        heatmapLevel2 = VocabColors.HeatmapLevel2,
        heatmapLevel3 = VocabColors.HeatmapLevel3,
        heatmapLevel4 = VocabColors.HeatmapLevel4,
    )

internal val DarkVocabExtendedColors =
    VocabExtendedColors(
        heroBackground = VocabColors.HeroBackgroundDark,
        heroBorder = VocabColors.HeroBorderDark,
        errorCardBackground = VocabColors.ErrorCardBackgroundDark,
        heatmapLevel1 = VocabColors.HeatmapLevel1Dark,
        heatmapLevel2 = VocabColors.HeatmapLevel2Dark,
        heatmapLevel3 = VocabColors.HeatmapLevel3Dark,
        heatmapLevel4 = VocabColors.HeatmapLevel4Dark,
    )

internal val LocalVocabExtendedColors =
    staticCompositionLocalOf {
        LightVocabExtendedColors
    }
