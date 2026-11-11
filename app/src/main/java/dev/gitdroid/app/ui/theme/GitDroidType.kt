package dev.gitdroid.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography.
 *
 * Two families, with a hard rule: **the system sans carries all UI text and
 * monospace is for code only.** Mixing a serif or a decorative face into the
 * chrome is what makes developer tools look like toys.
 *
 * Hierarchy comes from size and colour, not from a zoo of weights — the
 * system font only reliably ships two.
 */
@Immutable
data class GitDroidType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val titleSmall: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val labelSmall: TextStyle,
    val code: TextStyle,
    val codeSmall: TextStyle,
) {
    /** The single gateway for monospace styles. */
    fun mono(size: Int = 13, weight: FontWeight = FontWeight.Normal): TextStyle = code.copy(
        fontSize = size.sp,
        fontWeight = weight,
    )

    companion object {
        private val Sans = FontFamily.SansSerif
        private val Mono = FontFamily.Monospace

        private val TrimmedLineHeight = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        )

        val Default = GitDroidType(
            display = TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.4).sp,
                lineHeightStyle = TrimmedLineHeight,
            ),
            headline = TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                lineHeight = 27.sp,
                letterSpacing = (-0.2).sp,
                lineHeightStyle = TrimmedLineHeight,
            ),
            title = TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                letterSpacing = (-0.1).sp,
                lineHeightStyle = TrimmedLineHeight,
            ),
            titleSmall = TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                lineHeightStyle = TrimmedLineHeight,
            ),
            body = TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                lineHeightStyle = TrimmedLineHeight,
            ),
            bodySmall = TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                lineHeightStyle = TrimmedLineHeight,
            ),
            label = TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                letterSpacing = 0.1.sp,
                lineHeightStyle = TrimmedLineHeight,
            ),
            labelSmall = TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                letterSpacing = 0.3.sp,
                lineHeightStyle = TrimmedLineHeight,
            ),
            code = TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                lineHeightStyle = TrimmedLineHeight,
            ),
            codeSmall = TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                lineHeightStyle = TrimmedLineHeight,
            ),
        )
    }
}

/** The Material typography, mapped from [GitDroidType]. */
val gitDroidTypography = Typography(
    displayLarge = GitDroidType.Default.display,
    displayMedium = GitDroidType.Default.display,
    displaySmall = GitDroidType.Default.headline,
    headlineLarge = GitDroidType.Default.headline,
    headlineMedium = GitDroidType.Default.headline,
    headlineSmall = GitDroidType.Default.title,
    titleLarge = GitDroidType.Default.title,
    titleMedium = GitDroidType.Default.titleSmall,
    titleSmall = GitDroidType.Default.label,
    bodyLarge = GitDroidType.Default.body,
    bodyMedium = GitDroidType.Default.body,
    bodySmall = GitDroidType.Default.bodySmall,
    labelLarge = GitDroidType.Default.label,
    labelMedium = GitDroidType.Default.label,
    labelSmall = GitDroidType.Default.labelSmall,
)
