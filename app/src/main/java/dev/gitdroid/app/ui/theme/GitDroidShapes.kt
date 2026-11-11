package dev.gitdroid.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corner-radius vocabulary.
 *
 * Five steps plus a pill. Adjacent steps differ by roughly 1.4x so they are
 * clearly distinct at a glance, and each has one job:
 *
 * | Step | Radius | Used for |
 * |---|---|---|
 * | xs | 6 | inline code, badges, progress caps |
 * | sm | 10 | chips, icon tiles, small buttons |
 * | md | 14 | cards, code wells, text fields |
 * | lg | 20 | sheets, dialogs, prominent panels |
 * | xl | 28 | the composer, the outermost container |
 */
@Immutable
data class GitDroidShapes(
    val xs: Dp = 6.dp,
    val sm: Dp = 10.dp,
    val md: Dp = 14.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
) {
    val radiusXs = RoundedCornerShape(xs)
    val radiusSm = RoundedCornerShape(sm)
    val radiusMd = RoundedCornerShape(md)
    val radiusLg = RoundedCornerShape(lg)
    val radiusXl = RoundedCornerShape(xl)

    /** Cards, code wells, menus. */
    val card = radiusMd

    /** Sheets and dialogs. */
    val sheet = radiusXl

    /** Chips and small tiles. */
    val chip = radiusSm
}

/**
 * The spacing ladder.
 *
 * Everything is a multiple of 4, with 16 as the page gutter — wide enough to
 * breathe on a phone, narrow enough to fit a dense row.
 */
@Immutable
data class GitDroidSpace(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
) {
    /** Page horizontal padding. */
    val gutter: Dp = 16.dp

    /** Between two related rows in a list. */
    val rowGap: Dp = 2.dp

    /** Between two sections. */
    val sectionGap: Dp = 24.dp
}

/** The Material shapes, mapped from the ladder. */
val gitDroidShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
