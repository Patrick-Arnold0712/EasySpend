package com.example.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.finance.MoneyFormatter
import com.example.ui.theme.semanticColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Spring-physics motion tokens inspired by "Motion is the Material":
 * physical damping ratios, zero robotic linear snaps, and draw-phase ambient motion.
 */
object EasySpendMotion {
    val SnappySpring = spring<Float>(
        dampingRatio = 0.78f,
        stiffness = 560f
    )
    val FluidSpring = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 340f
    )
    val GentleSpring = spring<Float>(
        dampingRatio = 0.90f,
        stiffness = 220f
    )
    val BouncySpring = spring<Float>(
        dampingRatio = 0.60f,
        stiffness = 440f
    )
    val SpatialSizeSpring = spring<IntSize>(
        dampingRatio = 0.86f,
        stiffness = 380f
    )
    val SpatialOffsetSpring = spring<IntOffset>(
        dampingRatio = 0.86f,
        stiffness = 380f
    )
}

/**
 * Tactile spring press detent with subtle haptic feedback.
 * Scales smoothly to [pressedScale] during press in graphicsLayer (no relayout).
 */
fun Modifier.springClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.972f,
    haptic: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = EasySpendMotion.SnappySpring,
        label = "springPressScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(),
            enabled = enabled
        ) {
            if (haptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onClick()
        }
}

/**
 * Staggered spring entrance animation (kept crisp under 260ms so interactions feel instant).
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val delayMs = (index.coerceIn(0, 6) * 32L)
        if (delayMs > 0L) delay(delayMs)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = EasySpendMotion.FluidSpring
        )
    }
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 20f
            val s = 0.985f + 0.015f * p
            scaleX = s
            scaleY = s
        }
    ) {
        content()
    }
}

/**
 * Smooth spring-interpolated currency display in tabular monospace figures.
 */
@Composable
fun AnimatedMoneyText(
    amountFen: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    prefix: String = ""
) {
    val animatedFen by animateFloatAsState(
        targetValue = amountFen.toFloat(),
        animationSpec = EasySpendMotion.GentleSpring,
        label = "animatedMoneyFen"
    )
    Text(
        text = "$prefix${MoneyFormatter.formatFen(animatedFen.roundToLong())}",
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * Sculptural fintech Hero Card with draw-phase breathing atmospheric radial orbs
 * and a delicate top specular rim highlight. Reads infinite animation exclusively
 * inside drawBehind to maintain zero recomposition overhead and silky 120Hz rendering.
 */
@Composable
fun AtmosphericHeroCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    content: @Composable BoxScope.() -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    val infiniteTransition = rememberInfiniteTransition(label = "heroAtmosphere")
    val driftPhaseState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "heroDriftPhase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        semantic.heroRimHighlight,
                        semantic.heroRimHighlight.copy(alpha = 0.08f)
                    )
                ),
                shape = shape
            )
            .drawBehind {
                // Base directional botanical-obsidian gradient
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(semantic.heroStart, semantic.heroEnd),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                )

                val phase = driftPhaseState.value
                // Orb 1: Luminous emerald-teal breathing aura (upper right)
                val orb1Center = Offset(
                    x = size.width * (0.82f + 0.07f * cos(phase)),
                    y = size.height * (0.18f + 0.08f * sin(phase))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(semantic.heroGlowPrimary, Color.Transparent),
                        center = orb1Center,
                        radius = size.maxDimension * 0.68f
                    ),
                    radius = size.maxDimension * 0.68f,
                    center = orb1Center
                )

                // Orb 2: Subtle champagne/arctic highlight (lower left)
                val orb2Center = Offset(
                    x = size.width * (0.16f + 0.06f * sin(phase * 0.8f)),
                    y = size.height * (0.92f + 0.06f * cos(phase * 0.8f))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(semantic.heroGlowAccent, Color.Transparent),
                        center = orb2Center,
                        radius = size.maxDimension * 0.56f
                    ),
                    radius = size.maxDimension * 0.56f,
                    center = orb2Center
                )
            },
        content = content
    )
}

data class FluidSegmentOption(
    val label: String,
    val testTag: String,
    val icon: ImageVector? = null
)

/**
 * Custom sliding-pill segmented switcher with spring physics thumb & haptic click.
 * Replaces static segmented rows with tactile fluid spatial continuity.
 */
@Composable
fun FluidSegmentedBar(
    options: List<FluidSegmentOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val semantic = MaterialTheme.semanticColors
    val haptic = LocalHapticFeedback.current
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val outerShape = RoundedCornerShape(14.dp)
    val thumbShape = RoundedCornerShape(11.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(outerShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(0.75.dp, semantic.hairline, outerShape)
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * safeIndex,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = 520f
            ),
            label = "fluidSegmentThumbOffset"
        )

        // Sliding elevated pill thumb
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = thumbShape,
            shadowElevation = if (semantic.isDark) 0.dp else 1.5.dp,
            border = BorderStroke(0.6.dp, semantic.hairline.copy(alpha = 0.9f)),
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .fillMaxHeight()
        ) {}

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { idx, opt ->
                val isSelected = idx == safeIndex
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "segmentTextColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 40.dp)
                        .clip(thumbShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(idx)
                            }
                        }
                        .testTag(opt.testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        if (opt.icon != null) {
                            Icon(
                                imageVector = opt.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = opt.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Spring-driven vertical expand/collapse transition.
 */
@Composable
fun SpringAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(animationSpec = EasySpendMotion.SpatialSizeSpring) +
            fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = shrinkVertically(animationSpec = EasySpendMotion.SpatialSizeSpring) +
            fadeOut(animationSpec = tween(durationMillis = 140)),
        content = content
    )
}

/**
 * Clean circular tonal icon badge for crisp CRAP visual rhythm.
 */
@Composable
fun TonalIconBadge(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Staggered breathing 3-dot wave indicator for AI thinking states.
 * Reads animation exclusively in Canvas draw phase (zero recomposition).
 */
@Composable
fun AgentThinkingPulse(
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "agentThinkingPulse")
    val phaseState = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    Canvas(modifier = modifier.size(width = 30.dp, height = 14.dp)) {
        val phase = phaseState.value
        val baseRadius = 2.6.dp.toPx()
        val spacing = size.width / 3f
        val cy = size.height / 2f
        for (i in 0 until 3) {
            val wave = ((sin(phase - i * 0.85f) + 1f) * 0.5f)
            val r = baseRadius * (0.72f + 0.48f * wave)
            val alpha = 0.35f + 0.65f * wave
            val cx = spacing * i + spacing / 2f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = r,
                center = Offset(cx, cy - wave * 2.dp.toPx())
            )
        }
    }
}

/**
 * Subtle pulsing live status indicator dot (draw-phase only).
 */
@Composable
fun LiveStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val pulseState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liveDotPulse"
    )
    Canvas(modifier = modifier.size(size * 1.8f)) {
        val p = pulseState.value
        val coreR = (size / 2f).toPx()
        val haloR = coreR * (1f + 0.9f * p)
        drawCircle(
            color = color.copy(alpha = (1f - p) * 0.35f),
            radius = haloR,
            center = center
        )
        drawCircle(
            color = color,
            radius = coreR,
            center = center
        )
    }
}
