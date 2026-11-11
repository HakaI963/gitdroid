package dev.gitdroid.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * The motion vocabulary.
 *
 * The principles are the ones that make an interface feel *alive but calm*:
 *
 * * Feedback (press, icon swap) is under 160ms — too slow and it feels broken.
 * * Structural changes (sheets, cards morphing) take 240–340ms, long enough
 *   to see where the thing came from.
 * * Springs carry the layout; tweens carry the colour.
 *
 * Spring parameters follow the same shape as the DSH-red motion system this
 * app takes inspiration from: a short, barely-perceptible bounce for press
 * landings, and a long-tailed glide for anything that follows the finger.
 */
@Immutable
data class Motion(
    val pressMillis: Int,
    val quickMillis: Int,
    val standardMillis: Int,
    val sheetMillis: Int,
    val slowMillis: Int,
) {
    // ── Durations ───────────────────────────────────────────────────────────

    /** Press landing — 150ms, bounce 0: a landing that does not wobble. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = 900f)

    /** Cross-fade, icon swap, the copy-done check — 160ms. */
    fun <T> quick(): FiniteAnimationSpec<T> = tween(quickMillis, easing = Std)

    /** Small block expand / collapse, list items in and out — 240ms. */
    fun <T> standard(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 420f)

    /** Capsule morph, drawer landing — 340ms with a touch of overshoot. */
    fun <T> emphasized(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.78f, stiffness = 320f)

    /** Sheet rise and fall — a long-tailed glide. */
    fun <T> sheet(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 260f)

    /** Page-level transitions — 480ms. */
    fun <T> slow(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 200f)

    /** Colour changes. */
    fun <T> color(): FiniteAnimationSpec<T> = tween(standardMillis, easing = Std)

    /** The landing of size and position changes. */
    fun <T> layout(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 380f)

    /** A spring that follows the finger and lands with a little bounce. */
    fun <T> fling(damping: Float = 0.72f, stiffness: Float = 320f): FiniteAnimationSpec<T> =
        spring(dampingRatio = damping, stiffness = stiffness)

    /** The press landing, for callers that want a non-finite spec. */
    fun <T> pressSpec(): AnimationSpec<T> = press()

    companion object {
        /** The standard easing — the M3 curve family. */
        val Std: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /** Enter: quick out, slow stop. */
        val Enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

        /** Exit: slow start, quick out. */
        val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

        /** Glides: things that follow the finger, then land. */
        val Glide: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

        /** The default motion set. */
        val Default = Motion(
            pressMillis = 150,
            quickMillis = 160,
            standardMillis = 240,
            sheetMillis = 500,
            slowMillis = 480,
        )
    }
}

/**
 * The bouncy spring constants Compose ships with.
 *
 * `DampingRatioMediumBouncy` is the one that reads as "alive but not
 * cartoonish" — it is the default for anything that follows a finger.
 */
object BouncySpring {
    val Damping = Spring.DampingRatioMediumBouncy
    val Stiffness = Spring.StiffnessMediumLow
    val GlideDamping = Spring.DampingRatioLowBouncy
}
