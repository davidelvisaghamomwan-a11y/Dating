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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import kotlinx.coroutines.CoroutineStart
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
        var committed = false
        try {
            val targetX = if (direction == SwipeDirection.RIGHT) flyDistancePx else -flyDistancePx
            val targetY = offset.value.y.coerceIn(-120f, 180f) + 90f
            offset.animateTo(
                targetValue = Offset(targetX, targetY),
                animationSpec = tween(durationMillis = 230)
            )
            committed = true
            onCommitSwipe(direction)
        } finally {
            if (!committed) {
                isAnimatingOut = false
            }
        }
    }

    suspend fun springBack() {
        if (isAnimatingOut) return
        offset.animateTo(
            targetValue = Offset.Zero,
            animationSpec = spring(
                dampingRatio = 0.66f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
}

/**
 * Tinder-style 3-card stacked swipe deck driven by Compose's Foundation gesture detector
 * (`detectDragGestures`) and `VelocityTracker` for responsive flicks, 2D drag tilt,
 * LIKE/PASS stamps, directional edge glow, and smooth background card elevation.
 */
@Composable
fun <T> SwipeCardDeck(
    topItem: T,
    topKey: Any,
    nextItem: T?,
    thirdItem: T? = null,
    onSwiped: (T, SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    controllerOut: ((SwipeCardController) -> Unit)? = null,
    onDragProgressChanged: ((SwipeDirection?, Float) -> Unit)? = null,
    cardContent: @Composable (item: T, isTopCard: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val latestTopItem by rememberUpdatedState(topItem)
    val latestOnSwiped by rememberUpdatedState(onSwiped)
    val latestOnDragProgress by rememberUpdatedState(onDragProgressChanged)

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
                onCommitSwipe = { dir -> latestOnSwiped(latestTopItem, dir) }
            )
        }
        LaunchedEffect(controller) {
            controllerOut?.invoke(controller)
        }

        val currentX = offset.value.x
        val dragFraction = (abs(currentX) / thresholdPx).coerceIn(0f, 1f)
        val activeDragDirection = when {
            currentX > stampRevealStartPx -> SwipeDirection.RIGHT
            currentX < -stampRevealStartPx -> SwipeDirection.LEFT
            else -> null
        }

        LaunchedEffect(activeDragDirection, dragFraction, topKey) {
            latestOnDragProgress?.invoke(activeDragDirection, dragFraction)
        }

        var crossedThresholdHaptic by remember(topKey) { mutableStateOf(false) }
        var dragTiltSign by remember(topKey) { mutableFloatStateOf(1f) }

        // Third card in deck peeks softly beneath the second card
        if (thirdItem != null) {
            val thirdScale = 0.87f + (0.06f * dragFraction)
            val thirdAlpha = 0.44f + (0.28f * dragFraction)
            val thirdTranslationY = with(density) { (22.dp - (10.dp * dragFraction)).toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .graphicsLayer {
                        scaleX = thirdScale
                        scaleY = thirdScale
                        alpha = thirdAlpha
                        translationY = thirdTranslationY
                    }
            ) {
                cardContent(thirdItem, false)
            }
        }

        // Background second card smoothly scales and rises as top card is dragged away
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

        val glowStrokeWidthPx = with(density) { 3.5.dp.toPx() }
        val glowCornerRadiusPx = with(density) { 28.dp.toPx() }

        // Top interactive card with synchronous 2D drag + velocity gesture detector
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
                .drawWithContent {
                    drawContent()
                    if (activeDragDirection != null && dragFraction > 0.05f) {
                        val glowColor = if (activeDragDirection == SwipeDirection.RIGHT) {
                            EmeraldMint
                        } else {
                            CoralRose
                        }
                        drawRoundRect(
                            color = glowColor.copy(alpha = 0.72f * dragFraction),
                            cornerRadius = CornerRadius(glowCornerRadiusPx, glowCornerRadiusPx),
                            style = Stroke(width = glowStrokeWidthPx)
                        )
                    }
                }
                .pointerInput(topKey, controller) {
                    val velocityTracker = VelocityTracker()
                    var currentDrag = Offset.Zero
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            if (controller.isAnimatingOut) return@detectDragGestures
                            velocityTracker.resetTracking()
                            currentDrag = offset.value
                            crossedThresholdHaptic = false
                            dragTiltSign = if (startOffset.y > size.height * 0.65f) 0.75f else 1f
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                offset.stop()
                            }
                        },
                        onDragCancel = {
                            crossedThresholdHaptic = false
                            scope.launch { controller.springBack() }
                        },
                        onDragEnd = {
                            if (controller.isAnimatingOut) return@detectDragGestures
                            val vx = velocityTracker.calculateVelocity().x
                            val curX = currentDrag.x
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
                            currentDrag = Offset(
                                x = currentDrag.x + dragAmount.x,
                                y = (currentDrag.y + dragAmount.y * 0.55f)
                                    .coerceIn(-160f, 160f)
                            )
                            if (!crossedThresholdHaptic && abs(currentDrag.x) >= thresholdPx) {
                                crossedThresholdHaptic = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (crossedThresholdHaptic && abs(currentDrag.x) < thresholdPx * 0.85f) {
                                crossedThresholdHaptic = false
                            }
                            val snapped = currentDrag
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                offset.snapTo(snapped)
                            }
                        }
                    )
                }
        ) {
            cardContent(topItem, true)

            if (activeDragDirection == SwipeDirection.RIGHT) {
                SwipeDecisionStamp(
                    text = stringResource(R.string.swipe_like_label),
                    color = EmeraldMint,
                    alpha = dragFraction,
                    tiltDegrees = -14f,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 24.dp, top = 76.dp)
                        .testTag("swipe_like_stamp")
                )
            } else if (activeDragDirection == SwipeDirection.LEFT) {
                SwipeDecisionStamp(
                    text = stringResource(R.string.swipe_pass_label),
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
