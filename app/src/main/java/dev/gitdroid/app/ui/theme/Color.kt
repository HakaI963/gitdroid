package dev.gitdroid.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The GitDroid palette.
 *
 * The idea is a *deep-sea slate* dark theme with a GitHub-green accent:
 * dark enough to read code in for hours, but never pure black — pure black
 * makes bright text swim after a long session. The light theme is a matching
 * "morning mist" rather than a stark white, so the two feel like the same
 * product seen under different light.
 *
 * Rules borrowed from the DSH-red palette this project takes inspiration from:
 *
 * * Roughly 70% neutral surfaces, 25% text and hairlines, at most 5% accent.
 * * The accent appears only on primary actions, selected states, links and
 *   progress — so it always means "this is the thing you can act on".
 * * Every text colour clears 4.5:1 against the surface it sits on.
 */

// ── Dark: deep-sea slate ───────────────────────────────────────────────────

val NightTop = Color(0xFF1B1F28)
val NightBottom = Color(0xFF15181F)
val NightLowest = Color(0xFF111419)
val NightLow = Color(0xFF1F232C)
val NightContainer = Color(0xFF252A34)
val NightHigh = Color(0xFF2C323D)
val NightHighest = Color(0xFF343A46)

val Paper = Color(0xFFE6E9F0)
val PaperMuted = Color(0xFFA8AFBD)
val PaperFaint = Color(0xFF828A99)
val PaperGhost = Color(0xFF5B6270)

val RuleDark = Color(0xFF2C323D)
val OutlineDark = Color(0xFF5F6878)

// ── Light: morning mist ────────────────────────────────────────────────────

val MistTop = Color(0xFFF1F5FD)
val MistBottom = Color(0xFFFAFBFD)
val MistLowest = Color(0xFFFFFFFF)
val MistLow = Color(0xFFF8FAFD)
val MistContainer = Color(0xFFF0F3F9)
val MistHigh = Color(0xFFE8ECF4)
val MistHighest = Color(0xFFE0E5EF)

val Ink = Color(0xFF1B2029)
val InkMuted = Color(0xFF4E5665)
val InkFaint = Color(0xFF6C7482)
val InkGhost = Color(0xFF9AA1AE)

val RuleLight = Color(0xFFE2E6EF)
val OutlineLight = Color(0xFF9BA3B1)

// ── Accent: GitHub green, tuned for both schemes ───────────────────────────

/** The primary accent. Dark mode uses a brighter green so it stays legible. */
val GreenLight = Color(0xFF1F883D)
val GreenDark = Color(0xFF3FB950)
val GreenContainerLight = Color(0xFFD8F3DE)
val GreenContainerDark = Color(0xFF16351F)
val OnGreenContainerLight = Color(0xFF0A2E13)
val OnGreenContainerDark = Color(0xFF9BE7AC)

/** Secondary — the "link blue" used for URLs and informational accents. */
val BlueLight = Color(0xFF0969DA)
val BlueDark = Color(0xFF58A6FF)
val BlueContainerLight = Color(0xFFDDEBFF)
val BlueContainerDark = Color(0xFF12233F)

/** Tertiary — a dusk violet for "agent" surfaces (the MCP half of the app). */
val DuskLight = Color(0xFF6B578F)
val DuskDark = Color(0xFFBEAEDE)
val DuskContainerLight = Color(0xFFEFE8FA)
val DuskContainerDark = Color(0xFF2E2545)

// ── Semantic status colours ────────────────────────────────────────────────

val SuccessLight = Color(0xFF1A7F37)
val SuccessDark = Color(0xFF3FB950)
val SuccessContainerLight = Color(0xFFD8F3DE)
val SuccessContainerDark = Color(0xFF16351F)

val WarningLight = Color(0xFF9A6700)
val WarningDark = Color(0xFFD29922)
val WarningContainerLight = Color(0xFFFFF3D6)
val WarningContainerDark = Color(0xFF3A2E12)

val DangerLight = Color(0xFFCF222E)
val DangerDark = Color(0xFFF85149)
val DangerContainerLight = Color(0xFFFFE7E7)
val DangerContainerDark = Color(0xFF3D1418)

val InfoLight = Color(0xFF0969DA)
val InfoDark = Color(0xFF58A6FF)
val InfoContainerLight = Color(0xFFDDEBFF)
val InfoContainerDark = Color(0xFF12233F)

/** Neutral fill used behind code and diff blocks. */
val CodeWellDark = Color(0xFF0E1116)
val CodeWellLight = Color(0xFFF6F8FA)

/** The scrim behind sheets and dialogs. */
val ScrimDark = Color(0xCC05070C)
val ScrimLight = Color(0x66101828)
