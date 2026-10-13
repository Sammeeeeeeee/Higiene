package io.github.sammeeeeeeee.higiene.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.sammeeeeeeee.higiene.data.Brands
import io.github.sammeeeeeeee.higiene.data.Establishment
import io.github.sammeeeeeeee.higiene.ui.badgeAsset
import io.github.sammeeeeeeee.higiene.ui.contentDescription
import io.github.sammeeeeeeee.higiene.ui.theme.Fsa
import io.github.sammeeeeeeee.higiene.ui.theme.LocalDisplay
import io.github.sammeeeeeeee.higiene.ui.theme.LocalHero
import io.github.sammeeeeeeee.higiene.ui.typeIcon

/** The official FSA rating image, unaltered. */
@Composable
fun RatingBadge(e: Establishment, large: Boolean, modifier: Modifier = Modifier, height: Dp = if (large) 72.dp else 40.dp) {
    val context = LocalContext.current
    val cd = e.rating.contentDescription(context, e.welsh)
    val asset = badgeAsset(e, large)
    if (asset == null) {
        Tag(cd, modifier)
        return
    }
    AsyncImage(
        model = asset,
        contentDescription = cd,
        contentScale = ContentScale.Fit,
        alignment = if (large) Alignment.CenterStart else Alignment.CenterEnd,
        // Badge shapes differ (FHRS small ~1.8:1, "exempt" ~2.8:1, FHIS ~2:1), so fit inside a fixed box.
        modifier = if (large) modifier.fillMaxWidth().height(height) else modifier.height(height).width(height * 2.2f),
    )
}

/**
 * Chain logo (Wikimedia, then Facebook) in a circle, falling back to a business-type icon.
 * [brand] is null for independents or when logos are switched off.
 */
@Composable
fun BusinessLogo(typeId: Int, brand: Brands.Brand?, modifier: Modifier = Modifier, size: Dp = 48.dp, onDark: Boolean = false) {
    val urls = brand?.logoUrls.orEmpty()
    var attempt by remember(brand?.qid) { mutableIntStateOf(0) }
    val showLogo = attempt < urls.size
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    showLogo -> Color.White
                    onDark -> Fsa.Lime
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
            )
            .border(1.dp, if (onDark) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (showLogo) {
            AsyncImage(
                model = urls[attempt],
                contentDescription = brand?.name,
                contentScale = ContentScale.Fit,
                onError = { attempt++ },
                modifier = Modifier.size(size * 0.74f),
            )
        } else {
            Icon(
                typeIcon(typeId),
                contentDescription = null,
                tint = if (onDark) Fsa.Ink else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.48f),
            )
        }
    }
}

/** Filled bright pill: the one "do this next" action on a screen. */
@Composable
fun PrimaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 52.dp).pressScale(interaction, 0.94f),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Fsa.Lime, contentColor = Fsa.Ink),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, onDark: Boolean = false) {
    val color = if (onDark) LocalHero.current.body else MaterialTheme.colorScheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    OutlinedButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 52.dp).pressScale(interaction, 0.94f),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Underlined text action, the quiet partner of a filled pill. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline),
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .tactileClick(pressed = 0.94f, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 2.dp),
    )
}

/** Pill chip: selected = bright green with a check that slides in; otherwise a soft grey pill. */
@Composable
fun PillChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val bg by animateColorAsState(if (selected) Fsa.Lime else MaterialTheme.colorScheme.surfaceVariant, label = "chipBg")
    val fg by animateColorAsState(if (selected) Fsa.Ink else MaterialTheme.colorScheme.onSurface, label = "chipFg")
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(bg)
            .tactileClick(pressed = 0.92f, haptic = HapticFeedbackType.SegmentTick, role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = text + if (selected) ", on" else ", off" }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        AnimatedVisibility(selected && icon == null, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
            Row {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

/** Small status tag: pale green wash with ink text, or ink with bright green text when [strong]. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, strong: Boolean = false, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(CircleShape)
            .background(if (strong) Fsa.Ink else MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (strong) Fsa.Lime else MaterialTheme.colorScheme.onPrimaryContainer
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Rounded card on the soft grey surface. Large radius by default (Wise "large card"). */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = color, contentColor = contentColor, shape = shape, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Inverted ink card with a bright headline, for the section that deserves emphasis. */
@Composable
fun DarkCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val hero = LocalHero.current
    InfoCard(modifier, color = hero.background, contentColor = hero.body, padding = 24.dp) {
        Text(title, style = LocalDisplay.current.section, color = hero.headline)
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = LocalDisplay.current.section, color = MaterialTheme.colorScheme.onSurface, modifier = modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** A pressable rounded tile: the list unit across the app. */
@Composable
fun Tile(onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceVariant, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .tactileClick(pressed = 0.97f, onClick = onClick),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

/** A tappable heading that reveals [content] below it. */
@Composable
fun Expandable(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .tactileClick { open = !open }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.rotate(turn))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) { content() }
        }
    }
}
