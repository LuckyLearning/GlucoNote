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
    /** 旧页淡出要比新页淡入快，缩短「两层都半透明」的空档，避免透视出底色 */
    const val ExitFastMs = 140
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
        fadeIn(animationSpec = tween(EnterMs, delayMillis = 40, easing = FastOutSlowInEasing)) +
            slideInHorizontally(animationSpec = tween(EnterMs, easing = FastOutSlowInEasing)) { it / 6 }

    fun forwardExit(): ExitTransition =
        fadeOut(animationSpec = tween(ExitFastMs, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(animationSpec = tween(ExitMs, easing = FastOutSlowInEasing)) { -it / 14 }

    fun popEnter(): EnterTransition =
        fadeIn(animationSpec = tween(EnterMs, delayMillis = 40, easing = FastOutSlowInEasing)) +
            slideInHorizontally(animationSpec = tween(EnterMs, easing = FastOutSlowInEasing)) { -it / 14 }

    fun popExit(): ExitTransition =
        fadeOut(animationSpec = tween(ExitFastMs, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(animationSpec = tween(ExitMs, easing = FastOutSlowInEasing)) { it / 6 }

    /** Filter / chip driven content swap. */
    fun contentTransform(): ContentTransform =
        (fadeIn(animationSpec = ContentTween) +
            scaleIn(initialScale = 0.985f, animationSpec = ContentTween)) togetherWith
            (fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 1.01f, animationSpec = tween(160, easing = FastOutSlowInEasing)))

    /**
     * 向导步骤转场（共享轴 X）。
     *
     * 只做「短位移 + 淡入」，刻意不用 scale：整屏缩放会让文字有轻微呼吸感，
     * 长者会觉得"画面在抖"。旧内容退得比新内容快（90ms vs 180ms），
     * 压缩两层都半透明的空档，避免透视出底色。
     */
    const val WizardInMs = 180
    const val WizardOutMs = 90

    fun wizardForward(): ContentTransform =
        (slideInHorizontally(tween(WizardInMs, easing = FastOutSlowInEasing)) { it / 12 } +
            fadeIn(tween(WizardInMs, easing = FastOutSlowInEasing))) togetherWith
            (slideOutHorizontally(tween(WizardOutMs, easing = FastOutSlowInEasing)) { -it / 24 } +
                fadeOut(tween(WizardOutMs, easing = FastOutSlowInEasing)))

    /** 后退：方向整体反过来，动画本身不换，保持"同一个动作倒放"的心智 */
    fun wizardBackward(): ContentTransform =
        (slideInHorizontally(tween(WizardInMs, easing = FastOutSlowInEasing)) { -it / 12 } +
            fadeIn(tween(WizardInMs, easing = FastOutSlowInEasing))) togetherWith
            (slideOutHorizontally(tween(WizardOutMs, easing = FastOutSlowInEasing)) { it / 24 } +
                fadeOut(tween(WizardOutMs, easing = FastOutSlowInEasing)))
}
