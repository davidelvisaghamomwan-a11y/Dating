package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class SwipeDirection {
    LEFT,
    RIGHT
}

@Stable
class SwipeCardController internal constructor(
    internal val offset: Animatable<Offset, AnimationVector2D>,
    private val flyDistancePx: Float,
    private val onCommitSwipe: (SwipeDirection) -> Unit
) {
    var isAnimatingOut by mutableStateOf(false)
        internal set

    suspend fun animateSwipe(direction: SwipeDirection) {
        if (isAnimatingOut) return
        isAnimatingOut = true
        val targetX = if (direction == SwipeDirection.RIGHT) flyDistancePx else -flyDistancePx
        val targetY = offset.value.y.coerceIn(-120f, 180f) + 90f
        offset.animateTo(
            targetValue = Offset(targetX, targetY),
            animationSpec = tween(durationMillis = 240)
        )
        onCommitSwipe(direction)
    }

    suspend fun springBack() {
        if (isAnimatingOut) return
        offset.animateTo(
            targetValue = Offset.Zero,
            animationSpec = spring(
                dampingRatio = 0.68f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
}

/**
 * Tinder-style 2-card swipe deck driven by Compose's Foundation gesture detector
 * (`detectDragGestures`) and `VelocityTracker` for responsive flicks, 2D drag tilt,
 * LIKE/PASS stamps, and smooth background card elevation.
 */
@Composable
fun <T> SwipeCardDeck(
    topItem: T,
    topKey: Any,
    nextItem: T?,
    onSwiped: (T, SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    controllerOut: ((SwipeCardController) -> Unit)? = null,
    cardContent: @Composable (item: T, isTopCard: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val widthPx = with(density) { maxWidth.toPx().coerceAtLeast(300f) }
        val thresholdPx = max(widthPx * 0.28f, with(density) { 96.dp.toPx() })
        val stampRevealStartPx = with(density) { 18.dp.toPx() }
        val velocityThresholdPx = with(density) { 750.dp.toPx() }
        val flyDistancePx = widthPx * 1.45f

        val offset = remember(topKey) { Animatable(Offset.Zero, Offset.VectorConverter) }
        val controller = remember(topKey, flyDistancePx) {
            SwipeCardController(
                offset = offset,
                flyDistancePx = flyDistancePx,
                onCommitSwipe = { dir -> onSwiped(topItem, dir) }
            )
        }
        LaunchedEffect(controller) {
            controllerOut?.invoke(controller)
        }

        val dragFraction = (abs(offset.value.x) / thresholdPx).coerceIn(0f, 1f)
        var crossedThresholdHaptic by remember(topKey) { mutableStateOf(false) }
        var dragTiltSign by remember(topKey) { mutableFloatStateOf(1f) }

        // Background card smoothly scales and rises as top card is dragged away
        if (nextItem != null) {
            val backScale = 0.93f + (0.07f * dragFraction)
            val backAlpha = 0.72f + (0.28f * dragFraction)
            val backTranslationY = with(density) { (12.dp * (1f - dragFraction)).toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .graphicsLayer {
                        scaleX = backScale
                        scaleY = backScale
                        alpha = backAlpha
                        translationY = backTranslationY
                    }
            ) {
                cardContent(nextItem, false)
            }
        }

        // Top interactive card with 2D drag + velocity gesture detector
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("swipe_deck_top_card")
                .offset {
                    IntOffset(
                        x = offset.value.x.roundToInt(),
                        y = offset.value.y.roundToInt()
                    )
                }
                .graphicsLayer {
                    val normalizedX = (offset.value.x / widthPx).coerceIn(-1.2f, 1.2f)
                    rotationZ = normalizedX * 15f * dragTiltSign
                }
                .pointerInput(topKey, controller) {
                    val velocityTracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            velocityTracker.resetTracking()
                            crossedThresholdHaptic = false
                            dragTiltSign = if (startOffset.y > size.height * 0.65f) 0.75f else 1f
                        },
                        onDragCancel = {
                            crossedThresholdHaptic = false
                            scope.launch { controller.springBack() }
                        },
                        onDragEnd = {
                            val vx = velocityTracker.calculateVelocity().x
                            val curX = offset.value.x
                            val targetDirection = when {
                                curX > thresholdPx || (vx > velocityThresholdPx && curX > stampRevealStartPx) ->
                                    SwipeDirection.RIGHT
                                curX < -thresholdPx || (vx < -velocityThresholdPx && curX < -stampRevealStartPx) ->
                                    SwipeDirection.LEFT
                                else -> null
                            }
                            scope.launch {
                                if (targetDirection != null) {
                                    controller.animateSwipe(targetDirection)
                                } else {
                                    controller.springBack()
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (controller.isAnimatingOut) return@detectDragGestures
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val nextOffset = Offset(
                                x = offset.value.x + dragAmount.x,
                                y = (offset.value.y + dragAmount.y * 0.55f)
                                    .coerceIn(-160f, 160f)
                            )
                            if (!crossedThresholdHaptic && abs(nextOffset.x) >= thresholdPx) {
                                crossedThresholdHaptic = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (crossedThresholdHaptic && abs(nextOffset.x) < thresholdPx * 0.85f) {
                                crossedThresholdHaptic = false
                            }
                            scope.launch {
                                offset.snapTo(nextOffset)
                            }
                        }
                    )
                }
        ) {
            cardContent(topItem, true)

            if (offset.value.x > stampRevealStartPx) {
                SwipeDecisionStamp(
                    text = "LIKE",
                    color = EmeraldMint,
                    alpha = dragFraction,
                    tiltDegrees = -14f,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 24.dp, top = 76.dp)
                        .testTag("swipe_like_stamp")
                )
            } else if (offset.value.x < -stampRevealStartPx) {
                SwipeDecisionStamp(
                    text = "PASS",
                    color = CoralRose,
                    alpha = dragFraction,
                    tiltDegrees = 14f,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 24.dp, top = 76.dp)
                        .testTag("swipe_pass_stamp")
                )
            }
        }
    }
}

@Composable
private fun SwipeDecisionStamp(
    text: String,
    color: Color,
    alpha: Float,
    tiltDegrees: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            rotationZ = tiltDegrees
            val popScale = 0.85f + (0.15f * alpha)
            scaleX = popScale
            scaleY = popScale
        },
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.22f),
        border = BorderStroke(3.dp, color)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
        )
    }
}
