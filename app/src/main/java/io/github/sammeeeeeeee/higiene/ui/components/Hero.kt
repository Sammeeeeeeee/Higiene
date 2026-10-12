package io.github.sammeeeeeeee.higiene.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.ui.theme.LocalDisplay
import io.github.sammeeeeeeee.higiene.ui.theme.LocalHero

val HeroShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)

/**
 * The dark band at the top of every screen: big block headline in bright green, optional back
 * button and actions, and room for extra content (search, tags).
 */
@Composable
fun HeroHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    bottomPadding: Int = 24,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val hero = LocalHero.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(hero.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomPadding.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                HeroIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, hero.body)
                Spacer(Modifier.weight(1f))
                actions()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DisplayTitle(title, Modifier.weight(1f).padding(top = if (onBack == null) 12.dp else 0.dp))
            if (onBack == null) actions()
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = hero.body.copy(alpha = 0.85f))
        }
        content()
    }
}

/** Heavy display headline that shrinks to fit two lines. */
@Composable
fun DisplayTitle(text: String, modifier: Modifier = Modifier) {
    val hero = LocalHero.current
    BasicText(
        text.uppercase(),
        style = LocalDisplay.current.title.copy(color = hero.headline),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = 40.sp, stepSize = 2.sp),
        modifier = modifier,
    )
}

/** Fades and lifts its content in, [index] steps after the screen appears. */
@Composable
fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 420, delayMillis = 60 * index.coerceAtMost(8), easing = FastOutSlowInEasing))
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 28.dp.toPx()
        },
    ) { content() }
}

/** Pulsing placeholder tiles shown while results load. */
@Composable
fun SkeletonTiles(count: Int = 6, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    val base = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { this.alpha = alpha }
                    .clip(RoundedCornerShape(24.dp))
                    .background(base)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                    Box(Modifier.fillMaxWidth(0.45f).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.width(76.dp).height(38.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

/** Springy one-shot scale "pop", e.g. when a favourite is added. */
@Composable
fun rememberPop(trigger: Any?): Float {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == true) {
            scale.snapTo(0.7f)
            scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    return scale.value
}
