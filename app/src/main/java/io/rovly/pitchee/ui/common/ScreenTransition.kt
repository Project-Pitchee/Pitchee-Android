package io.rovly.pitchee.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared push / pop transition for every in-app screen.
 *
 * [forward] pushes a child screen on top of its parent, otherwise the screen
 * pops back.
 *
 * [contentZIndex] decides the stacking, and it has to be a value that is unique
 * per navigation step: a pushed screen gets an increasing positive number and a
 * popped one a decreasing negative number. Using a fixed +/-1 looks right the
 * first time, but as soon as two pops happen in a row the incoming and outgoing
 * screens both sit at -1, they tie, and Compose draws the later one (the parent)
 * on top of the screen that is actually leaving. Callers therefore bump a
 * counter on every navigation and pass it in here.
 */
@Composable
internal fun <T> ScreenTransition(
    targetState: T,
    forward: Boolean,
    contentZIndex: Float,
    modifier: Modifier = Modifier,
    label: String = "screen-transition",
    content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            val incoming = slideInHorizontally(
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 300f),
                initialOffsetX = { if (forward) it else -it / 3 },
            )
            val outgoing = slideOutHorizontally(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 340f),
                targetOffsetX = { if (forward) -it / 3 else it },
            )
            // The screen that owns the gesture keeps the top slot: a pushed child
            // slides over its parent, while a popped child slides off on top of
            // it. The caller supplies a strictly ordered value so two screens can
            // never end up with the same zIndex.
            (incoming togetherWith outgoing).apply {
                targetContentZIndex = contentZIndex
            }
        },
        label = label,
        content = content,
    )
}
