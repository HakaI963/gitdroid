package dev.gitdroid.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours Material 3 does not model.
 *
 * Material's [ColorScheme] covers surfaces, containers and the primary trio,
 * but a developer tool needs a few extra roles: the well behind code, the
 * rule colour for hairlines, and the per-status tones that GitHub uses for
 * workflow conclusions.
 */
@Immutable
data class GitDroidColors(
    val pageTop: Color,
    val pageBottom: Color,
    val rule: Color,
    val outline: Color,
    val codeWell: Color,
    val codeWellBorder: Color,
    val scrim: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val dusk: Color,
    val duskContainer: Color,
    val onDuskContainer: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val glassTint: Color,
)

val LocalGitDroidColors = staticCompositionLocalOf {
    darkGitDroidColors()
}

/** The dark scheme — the default. */
fun darkGitDroidColors(): GitDroidColors = GitDroidColors(
    pageTop = NightTop,
    pageBottom = NightBottom,
    rule = RuleDark,
    outline = OutlineDark,
    codeWell = CodeWellDark,
    codeWellBorder = RuleDark,
    scrim = ScrimDark,
    success = SuccessDark,
    successContainer = SuccessContainerDark,
    onSuccessContainer = Color(0xFF9BE7AC),
    warning = WarningDark,
    warningContainer = WarningContainerDark,
    onWarningContainer = Color(0xFFF5D999),
    danger = DangerDark,
    dangerContainer = DangerContainerDark,
    onDangerContainer = Color(0xFFFFC1BC),
    info = InfoDark,
    infoContainer = InfoContainerDark,
    onInfoContainer = Color(0xFFAFCBFF),
    dusk = DuskDark,
    duskContainer = DuskContainerDark,
    onDuskContainer = Color(0xFFDED5F0),
    textTertiary = PaperFaint,
    textDisabled = PaperGhost,
    glassTint = Color(0x14FFFFFF),
)

/** The light scheme. */
fun lightGitDroidColors(): GitDroidColors = GitDroidColors(
    pageTop = MistTop,
    pageBottom = MistBottom,
    rule = RuleLight,
    outline = OutlineLight,
    codeWell = CodeWellLight,
    codeWellBorder = RuleLight,
    scrim = ScrimLight,
    success = SuccessLight,
    successContainer = SuccessContainerLight,
    onSuccessContainer = OnGreenContainerLight,
    warning = WarningLight,
    warningContainer = WarningContainerLight,
    onWarningContainer = Color(0xFF5C3D00),
    danger = DangerLight,
    dangerContainer = DangerContainerLight,
    onDangerContainer = Color(0xFF7A1218),
    info = InfoLight,
    infoContainer = InfoContainerLight,
    onInfoContainer = Color(0xFF0B3A78),
    dusk = DuskLight,
    duskContainer = DuskContainerLight,
    onDuskContainer = Color(0xFF3B2C54),
    textTertiary = InkFaint,
    textDisabled = InkGhost,
    glassTint = Color(0x0A0B152D),
)

/** The dark Material scheme, built by hand rather than from a seed. */
fun gitDroidDarkScheme(): ColorScheme = darkColorScheme(
    primary = GreenDark,
    onPrimary = Color(0xFF04260D),
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = OnGreenContainerDark,
    secondary = BlueDark,
    onSecondary = Color(0xFF04223F),
    secondaryContainer = BlueContainerDark,
    onSecondaryContainer = Color(0xFFAFCBFF),
    tertiary = DuskDark,
    onTertiary = Color(0xFF241B36),
    tertiaryContainer = DuskContainerDark,
    onTertiaryContainer = Color(0xFFDED5F0),
    error = DangerDark,
    onError = Color(0xFF3D1418),
    errorContainer = DangerContainerDark,
    onErrorContainer = Color(0xFFFFC1BC),
    background = NightBottom,
    onBackground = Paper,
    surface = NightLow,
    onSurface = Paper,
    surfaceVariant = NightHigh,
    onSurfaceVariant = PaperMuted,
    surfaceContainerLowest = NightLowest,
    surfaceContainerLow = NightLow,
    surfaceContainer = NightContainer,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightHighest,
    surfaceTint = Color.Transparent,
    inverseSurface = Paper,
    inverseOnSurface = NightBottom,
    outline = OutlineDark,
    outlineVariant = RuleDark,
    scrim = ScrimDark,
)

/** The light Material scheme. */
fun gitDroidLightScheme(): ColorScheme = lightColorScheme(
    primary = GreenLight,
    onPrimary = Color.White,
    primaryContainer = GreenContainerLight,
    onPrimaryContainer = OnGreenContainerLight,
    secondary = BlueLight,
    onSecondary = Color.White,
    secondaryContainer = BlueContainerLight,
    onSecondaryContainer = Color(0xFF0B3A78),
    tertiary = DuskLight,
    onTertiary = Color.White,
    tertiaryContainer = DuskContainerLight,
    onTertiaryContainer = Color(0xFF3B2C54),
    error = DangerLight,
    onError = Color.White,
    errorContainer = DangerContainerLight,
    onErrorContainer = Color(0xFF7A1218),
    background = MistBottom,
    onBackground = Ink,
    surface = MistLow,
    onSurface = Ink,
    surfaceVariant = MistHigh,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = MistLowest,
    surfaceContainerLow = MistLow,
    surfaceContainer = MistContainer,
    surfaceContainerHigh = MistHigh,
    surfaceContainerHighest = MistHighest,
    surfaceTint = Color.Transparent,
    inverseSurface = Ink,
    inverseOnSurface = MistBottom,
    outline = OutlineLight,
    outlineVariant = RuleLight,
    scrim = ScrimLight,
)
