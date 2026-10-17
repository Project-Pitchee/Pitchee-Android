package io.rovly.pitchee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFFD96A3E),
    onPrimary = Color(0xFF2E0D00),
    primaryContainer = Color(0xFFFFDCCF),
    onPrimaryContainer = Color(0xFF3A1000),
    inversePrimary = PitcheeColorTokens.DarkToolbarCollapsedBackground,
    secondary = Color(0xFF3F6C68),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC0ECE6),
    onSecondaryContainer = Color(0xFF00201E),
    tertiary = Color(0xFF745B18),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE49A),
    onTertiaryContainer = Color(0xFF251A00),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF211A17),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF211A17),
    surfaceVariant = Color(0xFFF4DED5),
    onSurfaceVariant = Color(0xFF53433D),
    surfaceDim = Color(0xFFE6D7D2),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFF8EAE5),
    surfaceContainerHigh = Color(0xFFF2E2DC),
    surfaceContainerHighest = Color(0xFFECDDD6),
    inverseSurface = Color(0xFF382E2B),
    inverseOnSurface = Color(0xFFFFEDE7),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFD8C2BA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = PitcheeColorTokens.DarkToolbarCollapsedBackground,
    onPrimary = PitcheeColorTokens.DarkToolbarCollapsedContent,
    primaryContainer = Color(0xFF7E2E10),
    onPrimaryContainer = Color(0xFFFFDCCF),
    inversePrimary = Color(0xFFCD5B2E),
    secondary = Color(0xFFA5CCC7),
    onSecondary = Color(0xFF0C3734),
    secondaryContainer = Color(0xFF264F4C),
    onSecondaryContainer = Color(0xFFC0ECE6),
    tertiary = Color(0xFFE3C56D),
    onTertiary = Color(0xFF3D2F00),
    tertiaryContainer = Color(0xFF584700),
    onTertiaryContainer = Color(0xFFFFE49A),
    background = Color(0xFF1B120F),
    onBackground = PitcheeColorTokens.DarkToolbarContent,
    surface = Color(0xFF1B120F),
    onSurface = PitcheeColorTokens.DarkToolbarContent,
    surfaceVariant = Color(0xFF53433D),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceDim = Color(0xFF1B120F),
    surfaceBright = Color(0xFF443733),
    surfaceContainerLowest = Color(0xFF150D0B),
    surfaceContainerLow = Color(0xFF241916),
    surfaceContainer = Color(0xFF281D1A),
    surfaceContainerHigh = PitcheeColorTokens.DarkToolbarBackground,
    surfaceContainerHighest = Color(0xFF3E312D),
    inverseSurface = PitcheeColorTokens.DarkToolbarContent,
    inverseOnSurface = Color(0xFF382E2B),
    outline = Color(0xFFA08C84),
    outlineVariant = Color(0xFF53433D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

private val PitcheeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(PitcheeShapeTokens.CardRadius),
    extraLarge = RoundedCornerShape(28.dp),
)

object PitcheeShapeTokens {
    /** Card / panel radius used across the app (dashboard cards, result cards). */
    val CardRadius = 16.dp
    val PlayerCollapsedRadius = 26.dp
    val PlayerExpandedRadius = CardRadius
}

object PitcheeColorTokens {
    val Feminine = Color(0xFFFFB6C1)
    val FeminineContent = Color(0xFF3A1F2A)
    val Masculine = Color(0xFF6495ED)
    val MasculineContent = Color(0xFF102443)
    val SpectrumPink = Color(0xFFE75480)
    val Success = Color(0xFF1F6F43)
    val SuccessContainer = Color(0xFFD9EBDD)
    val SuccessContainerDark = Color(0xFF133D29)
    val SuccessContentDark = Color(0xFFB8E6C6)
    val Danger = Color(0xFF8E1B2E)
    val DangerContainer = Color(0xFFF4D8DD)
    val DangerContainerDark = Color(0xFF5A1723)
    val DangerContentDark = Color(0xFFFFD6DC)
    val Warning = Color(0xFFF08C32)
    val Info = Color(0xFF3B82F6)
    val InsightTeal = Color(0xFF25A6A0)
    val InsightPurple = Color(0xFF8B6FD6)
    val InsightGreen = Color(0xFF2F8F5B)
    val InsightRed = Color(0xFFC34A4A)
    val InsightAmber = Color(0xFFC98A3C)
    val SpectrumBackground = Color(0xFF111827)
    val SpectrumBlue = Color(0xFF2357D8)
    val SpectrumCyan = Color(0xFF19B9C8)
    val SpectrumHigh = Color(0xFFFFD9A0)
    val SpectrumAxis = Color(0xFFB9C2D0)
    val DarkToolbarBackground = Color(0xFF332724)
    val DarkToolbarContent = Color(0xFFF1DFD8)
    val DarkToolbarCollapsedBackground = Color(0xFFFFB59A)
    val DarkToolbarCollapsedContent = Color(0xFF5A1B00)
    val PlayerBackgroundLight = Color(0xFFE4F0FF)
    val PlayerBackgroundDark = Color(0xFF172A46)
    val PlayerContentLight = Color(0xFF16345C)
    val PlayerContentDark = Color(0xFFEAF3FF)
    val PlayerAccentLight = Color(0xFF3B82F6)
    val PlayerAccentDark = Color(0xFF7DB3FF)
    val PlayerSurfaceDark = Color(0xFF253247)
    val WaveformLight = Color(0xFFD6E6FF)
    val WaveformDark = Color(0xFF20344F)
    val WaveformMuted = Color(0xFF8C98AA)
    val WaveformMutedSoft = Color(0xFF7C8799)
    val NeutralScore = Color(0xFF9CA3AF)
    val ScoreLow = Color(0xFFD55362)
    val ScoreLowMid = Color(0xFFE47A36)
    val ScoreMid = Color(0xFFD9B53D)
    val ScoreHighMid = Color(0xFF57A773)
    val ScoreHigh = Color(0xFF168C80)
    val QuadrantEunuch = Color(0xFF7B1F2D)
    val QuadrantMale = Color(0xFF244B9B)
    val QuadrantFemale = Color(0xFF9B2353)
    val QuadrantNeutralFeminine = Color(0xFF146B66)
}
@Immutable
data class PitcheeExtendedColors(
    val feminine: Color,
    val feminineContent: Color,
    val masculine: Color,
    val masculineContent: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val info: Color,
    val spectrumBackground: Color,
    val spectrumBlue: Color,
    val spectrumCyan: Color,
    val spectrumPink: Color,
    val spectrumHigh: Color,
    val playerBackground: Color,
    val playerContent: Color,
    val playerAccent: Color,
    val playerAccentContent: Color,
    val insightTeal: Color,
    val insightPurple: Color,
    val insightGreen: Color,
    val insightRed: Color,
)

private val LocalPitcheeExtendedColors = staticCompositionLocalOf<PitcheeExtendedColors> {
    error("PitcheeExtendedColors not provided")
}

val MaterialTheme.pitcheeColors: PitcheeExtendedColors
    @Composable get() = LocalPitcheeExtendedColors.current

val Typography.metricHero: TextStyle
    get() = displayMedium.copy(
        fontSize = 44.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.Bold,
    )

val Typography.metricLarge: TextStyle
    get() = headlineMedium.copy(
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
    )

val Typography.metricMedium: TextStyle
    get() = headlineSmall.copy(
        fontSize = 28.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
    )

private val PitcheeTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

private val LightExtendedColors = PitcheeExtendedColors(
    feminine = PitcheeColorTokens.Feminine,
    feminineContent = PitcheeColorTokens.FeminineContent,
    masculine = PitcheeColorTokens.Masculine,
    masculineContent = PitcheeColorTokens.MasculineContent,
    success = PitcheeColorTokens.Success,
    danger = PitcheeColorTokens.Danger,
    warning = PitcheeColorTokens.Warning,
    info = PitcheeColorTokens.Info,
    spectrumBackground = PitcheeColorTokens.SpectrumBackground,
    spectrumBlue = PitcheeColorTokens.SpectrumBlue,
    spectrumCyan = PitcheeColorTokens.SpectrumCyan,
    spectrumPink = PitcheeColorTokens.SpectrumPink,
    spectrumHigh = PitcheeColorTokens.SpectrumHigh,
    playerBackground = PitcheeColorTokens.PlayerBackgroundLight,
    playerContent = PitcheeColorTokens.PlayerContentLight,
    playerAccent = PitcheeColorTokens.Info,
    playerAccentContent = Color.White,
    insightTeal = PitcheeColorTokens.InsightTeal,
    insightPurple = PitcheeColorTokens.InsightPurple,
    insightGreen = PitcheeColorTokens.InsightGreen,
    insightRed = PitcheeColorTokens.InsightRed,
)

private val DarkExtendedColors = LightExtendedColors.copy(
    playerBackground = PitcheeColorTokens.PlayerBackgroundDark,
    playerContent = PitcheeColorTokens.PlayerContentDark,
    playerAccent = PitcheeColorTokens.PlayerAccentDark,
    playerAccentContent = PitcheeColorTokens.MasculineContent,
)

@Composable
fun PitcheeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalPitcheeExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PitcheeTypography,
            shapes = PitcheeShapes,
            content = content,
        )
    }
}
