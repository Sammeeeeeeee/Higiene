package io.github.sammeeeeeeee.higiene.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
fun BusinessLogo(typeId: Int, brand: Brands.Brand?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val urls = brand?.logoUrls.orEmpty()
    var attempt by remember(brand?.qid) { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (attempt < urls.size) Color.White else MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (attempt < urls.size) {
            AsyncImage(
                model = urls[attempt],
                contentDescription = brand?.name,
                contentScale = ContentScale.Fit,
                onError = { attempt++ },
                modifier = Modifier.size(size * 0.78f),
            )
        } else {
            Icon(typeIcon(typeId), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** Filled bright pill: the one "do this next" action on a screen. */
@Composable
fun PrimaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Fsa.Lime, contentColor = Fsa.Ink),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 11.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, onDark: Boolean = false) {
    val color = if (onDark) LocalHero.current.body else MaterialTheme.colorScheme.onSurface
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Underlined text action, the quiet partner of a filled pill. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

/** Pill chip: selected = bright green fill with ink text, otherwise a soft grey pill. */
@Composable
fun PillChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Fsa.Lime else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) Fsa.Ink else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { contentDescription = text + if (selected) ", selected" else "" },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Small status tag: pale green wash with ink text. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (strong) Fsa.Lime else MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(if (strong) Fsa.Ink else MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Card on the grey surface with 10dp corners. */
@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = modifier.padding(top = 8.dp))
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
