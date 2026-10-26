package com.gluconote.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** Shared Material-ish motion tokens for page / content swaps. */
object Motion {
    const val EnterMs = 280
    const val ExitMs = 220
    const val ContentMs = 240
    const val FabMs = 220

    val EnterTween = tween<Float>(EnterMs, easing = FastOutSlowInEasing)
    val ExitTween = tween<Float>(ExitMs, easing = FastOutSlowInEasing)
    val ContentTween = tween<Float>(ContentMs, easing = FastOutSlowInEasing)

    /** Peer tab: soft fade-through (no hard cut). */
    fun tabEnter(): EnterTransition =
        fadeIn(animationSpec = EnterTween) +
            scaleIn(initialScale = 0.98f, animationSpec = EnterTween)

    fun tabExit(): ExitTransition =
        fadeOut(animationSpec = ExitTween) +
            scaleOut(targetScale = 1.02f, animationSpec = ExitTween)

    /** Nested push (e.g. Add Record): slight shared-axis X + fade. */
    fun forwardEnter(): EnterTransition =
        fadeIn(animationSpec = EnterTween) +
            slideInHorizontally(animationSpec = tween(EnterMs, easing = FastOutSlowInEasing)) { it / 6 }

    fun forwardExit(): ExitTransition =
        fadeOut(animationSpec = ExitTween) +
            slideOutHorizontally(animationSpec = tween(ExitMs, easing = FastOutSlowInEasing)) { -it / 14 }

    fun popEnter(): EnterTransition =
        fadeIn(animationSpec = EnterTween) +
            slideInHorizontally(animationSpec = tween(EnterMs, easing = FastOutSlowInEasing)) { -it / 14 }

    fun popExit(): ExitTransition =
        fadeOut(animationSpec = ExitTween) +
            slideOutHorizontally(animationSpec = tween(ExitMs, easing = FastOutSlowInEasing)) { it / 6 }

    /** Filter / chip driven content swap. */
    fun contentTransform(): ContentTransform =
        (fadeIn(animationSpec = ContentTween) +
            scaleIn(initialScale = 0.985f, animationSpec = ContentTween)) togetherWith
            (fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 1.01f, animationSpec = tween(160, easing = FastOutSlowInEasing)))
}
