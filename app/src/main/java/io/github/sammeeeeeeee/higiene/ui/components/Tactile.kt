package io.github.sammeeeeeeee.higiene.ui.components

import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sammeeeeeeee.higiene.ui.theme.Fsa

/** Springy press feedback: the element sinks slightly while held and bounces back. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.95f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Clickable with spring press-scale, a soft ripple and a haptic tick. */
fun Modifier.tactileClick(
    pressed: Float = 0.97f,
    haptic: HapticFeedbackType? = HapticFeedbackType.ContextClick,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    this
        .pressScale(interaction, pressed)
        .clickable(interactionSource = interaction, indication = ripple(), role = role) {
            if (haptic != null) haptics.performHapticFeedback(haptic)
            onClick()
        }
}

data class PillItem(val label: String, val icon: ImageVector? = null)

/**
 * Wise-style segmented pill: a rounded track with one bright pill that glides to the selection.
 * Used for the bottom navigation and for one-of-many settings.
 */
@Composable
fun SegmentedPills(
    items: List<PillItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    indicator: Color = Fsa.Lime,
    selectedContent: Color = Fsa.Ink,
    content: Color = MaterialTheme.colorScheme.onSurface,
    vertical: Boolean = false,
    /** False shows icons only (with the label read out instead), for a tucked-away pill. */
    labels: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(track)
            .padding(4.dp),
    ) {
        val itemWidth = maxWidth / items.size
        val x by animateDpAsState(
            targetValue = itemWidth * selected.coerceIn(0, items.lastIndex),
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
            label = "indicator",
        )
        Box(
            Modifier
                .offset(x = x)
                .width(itemWidth)
                .fillMaxHeight()
                .shadow(2.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(indicator),
        )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, item ->
                val isSelected = i == selected
                val color by animateColorAsState(if (isSelected) selectedContent else content, label = "pillText")
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .pressScale(interaction, 0.92f)
                        .selectable(
                            selected = isSelected,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (vertical && item.icon != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(item.icon, contentDescription = if (labels) null else item.label, tint = color, modifier = Modifier.size(22.dp))
                            AnimatedVisibility(labels, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                Text(item.label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.icon != null) {
                                Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                item.label,
                                color = color,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Round icon button on dark hero bands, with a translucent pill behind it. */
@Composable
fun HeroIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .tactileClick(pressed = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun Gap(height: Dp) = Spacer(Modifier.height(height))
