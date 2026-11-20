package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.motion.EasySpendMotion
import com.example.ui.motion.SpringAnimatedVisibility
import com.example.ui.motion.TonalIconBadge
import com.example.ui.motion.springClickable
import com.example.ui.theme.semanticColors

/**
 * Directional spring-physics module transition container.
 * Gives categorized tabs/modules physical spatial continuity with gentle parallax slide,
 * subtle scale bloom, and smooth container height interpolation.
 */
@Composable
fun SilkyModuleTransition(
    targetIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    AnimatedContent(
        targetState = targetIndex,
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState >= initialState) 1 else -1
            val enter = slideInHorizontally(
                animationSpec = EasySpendMotion.SpatialOffsetSpring
            ) { width -> (width * 0.18f).toInt() * direction } +
                fadeIn(tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.975f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                )
            val exit = slideOutHorizontally(
                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
            ) { width -> -(width * 0.14f).toInt() * direction } +
                fadeOut(tween(durationMillis = 150, easing = FastOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.98f,
                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                )
            (enter togetherWith exit).using(
                SizeTransform(clip = false) { _, _ -> EasySpendMotion.SpatialSizeSpring }
            )
        },
        label = "silkyModuleTransition"
    ) { index ->
        content(index)
    }
}

/**
 * Swiss-minimal collapsible module card with spring-driven expand/collapse,
 * rotating chevron indicator, live status pill, and subtle border elevation shift.
 */
@Composable
fun ModularAccordionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    headerTestTag: String? = null,
    statusBadge: String? = null,
    statusBadgeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
    statusBadgeContentColor: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    val haptic = LocalHapticFeedback.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = EasySpendMotion.SnappySpring,
        label = "accordionChevronRotation"
    )
    val borderColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        } else {
            semantic.hairline
        },
        animationSpec = tween(durationMillis = 200),
        label = "accordionBorderColor"
    )
    val containerColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = tween(durationMillis = 200),
        label = "accordionContainerColor"
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = BorderStroke(0.85.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = EasySpendMotion.SpatialSizeSpring)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = androidx.compose.material3.ripple()
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle()
                    }
                    .let { mod -> if (headerTestTag != null) mod.testTag(headerTestTag) else mod }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                TonalIconBadge(
                    icon = icon,
                    containerColor = iconContainerColor,
                    iconTint = iconTint,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!statusBadge.isNullOrBlank()) {
                    AnimatedStatusPill(
                        text = statusBadge,
                        containerColor = statusBadgeContainerColor,
                        contentColor = statusBadgeContentColor
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起$title" else "展开$title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }

            SpringAnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(thickness = 0.75.dp, color = semantic.hairline)
                    content()
                }
            }
        }
    }
}

/**
 * Tactile bento-style action tile with spring press physics, tonal icon badge,
 * title, and optional micro-caption. Ideal for grouped toolboxes & module hubs.
 */
@Composable
fun ModularActionTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    testTag: String? = null,
    enabled: Boolean = true,
    accentContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
    accentIconTint: Color = MaterialTheme.colorScheme.primary,
    highlighted: Boolean = false
) {
    val semantic = MaterialTheme.semanticColors
    val tileContainer by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(180),
        label = "actionTileBg"
    )
    val tileBorder by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        } else {
            semantic.hairline
        },
        animationSpec = tween(180),
        label = "actionTileBorder"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tileContainer,
        border = BorderStroke(0.75.dp, tileBorder),
        modifier = modifier
            .heightIn(min = 66.dp)
            .clip(RoundedCornerShape(14.dp))
            .springClickable(enabled = enabled, pressedScale = 0.96f, onClick = onClick)
            .let { mod -> if (testTag != null) mod.testTag(testTag) else mod }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            TonalIconBadge(
                icon = icon,
                containerColor = accentContainerColor,
                iconTint = accentIconTint,
                size = 32.dp,
                iconSize = 16.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Animated pill badge whose text smoothly rolls vertically when updated.
 */
@Composable
fun AnimatedStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (slideInVertically(EasySpendMotion.SpatialOffsetSpring) { it / 2 } + fadeIn(tween(180))) togetherWith
                    (slideOutVertically(tween(140)) { -it / 2 } + fadeOut(tween(120)))
            },
            label = "animatedStatusPill"
        ) { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Clean Swiss typographic eyebrow row separating categorized modules.
 */
@Composable
fun ModularSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!badge.isNullOrBlank()) {
                AnimatedStatusPill(
                    text = badge,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}
