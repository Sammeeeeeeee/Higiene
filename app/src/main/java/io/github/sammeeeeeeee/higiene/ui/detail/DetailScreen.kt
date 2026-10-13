package io.github.sammeeeeeeee.higiene.ui.detail

import androidx.compose.ui.semantics.Role
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.Establishment
import io.github.sammeeeeeeee.higiene.data.OsmDetails
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.automirrored.rounded.Accessible
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import io.github.sammeeeeeeee.higiene.ui.components.Expandable
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.sammeeeeeeee.higiene.HigieneApp
import io.github.sammeeeeeeee.higiene.data.GooglePlace
import io.github.sammeeeeeeee.higiene.data.StreetPhoto
import io.github.sammeeeeeeee.higiene.ui.theme.LocalIsDark
import kotlin.math.roundToInt
import io.github.sammeeeeeeee.higiene.data.Postcodes
import io.github.sammeeeeeeee.higiene.ui.components.BusinessLogo
import io.github.sammeeeeeeee.higiene.ui.components.DarkCard
import io.github.sammeeeeeeee.higiene.ui.components.HeroIconButton
import io.github.sammeeeeeeee.higiene.ui.components.HeroShape
import io.github.sammeeeeeeee.higiene.ui.components.InfoCard
import io.github.sammeeeeeeee.higiene.ui.components.OutlinePill
import io.github.sammeeeeeeee.higiene.ui.components.PrimaryPill
import io.github.sammeeeeeeee.higiene.ui.components.RatingBadge
import io.github.sammeeeeeeee.higiene.ui.components.Reveal
import io.github.sammeeeeeeee.higiene.ui.components.SectionTitle
import io.github.sammeeeeeeee.higiene.ui.components.Tag
import io.github.sammeeeeeeee.higiene.ui.components.TextLink
import io.github.sammeeeeeeee.higiene.ui.components.rememberPop
import io.github.sammeeeeeeee.higiene.ui.components.tactileClick
import io.github.sammeeeeeeee.higiene.ui.formatDistance
import io.github.sammeeeeeeee.higiene.ui.label
import io.github.sammeeeeeeee.higiene.ui.pretty
import io.github.sammeeeeeeee.higiene.ui.replyText
import io.github.sammeeeeeeee.higiene.ui.scoreRows
import io.github.sammeeeeeeee.higiene.ui.sharedElement
import io.github.sammeeeeeeee.higiene.ui.typeIcon
import io.github.sammeeeeeeee.higiene.ui.typeLabel
import io.github.sammeeeeeeee.higiene.ui.theme.Fsa
import io.github.sammeeeeeeee.higiene.ui.theme.LocalDisplay
import io.github.sammeeeeeeee.higiene.ui.theme.LocalHero
import java.time.Instant
import java.time.ZoneId

@Composable
fun DetailScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onOpenAddress: (String) -> Unit, vm: DetailViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val e = state.est
    val hero = LocalHero.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Hero -------------------------------------------------------------------------------------
        Column(
            Modifier
                .fillMaxWidth()
                .clip(HeroShape)
                .background(hero.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                HeroIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, hero.body)
                Spacer(Modifier.weight(1f))
                if (e != null) {
                    HeroIconButton(Icons.Rounded.Share, stringResource(R.string.share), { share(context, e) }, hero.body)
                    Spacer(Modifier.width(10.dp))
                }
                // Also shown for a favourite that has left the FSA data, so it can be removed.
                if (e != null || state.favourite) {
                    val pop = rememberPop(state.favourite)
                    HeroIconButton(
                        if (state.favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        stringResource(if (state.favourite) R.string.unfavourite else R.string.favourite),
                        onClick = {
                            val on = vm.toggleFavourite()
                            haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            if (on && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        tint = if (state.favourite) hero.accent else hero.body,
                        modifier = Modifier.scale(pop),
                    )
                }
            }
            if (e != null) {
                BusinessLogo(e.typeId, state.osmBrand ?: state.brand, size = 64.dp, onDark = true, modifier = Modifier.sharedElement("logo-${e.id}"))
                Text(e.name.uppercase(), style = LocalDisplay.current.title, color = hero.headline)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e.typeName.isNotEmpty()) HeroTag(typeLabel(context, e.typeId, e.typeName), typeIcon(e.typeId))
                    formatDistance(context, state.distanceM, settings.miles)?.let { HeroTag(it, Icons.Rounded.NearMe) }
                }
                e.address?.let { addr ->
                    Text(listOfNotNull(addr, e.postcode).joinToString(", "), style = MaterialTheme.typography.bodyLarge, color = hero.body.copy(alpha = 0.9f))
                }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Fsa.Green) }
            e == null -> Text(stringResource(R.string.detail_missing), Modifier.padding(24.dp))
            else -> Body(state, e, settings.osmDetails, onOpen, onOpenAddress, onPhotoFailed = vm::photoFailed, onGooglePhotoFailed = vm::googlePhotoFailed)
        }
    }
}

@Composable
private fun HeroTag(text: String, icon: ImageVector? = null) {
    val hero = LocalHero.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = hero.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = hero.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Body(
    state: DetailState,
    e: Establishment,
    osmEnabled: Boolean,
    onOpen: (Long) -> Unit,
    onOpenAddress: (String) -> Unit,
    onPhotoFailed: () -> Unit,
    onGooglePhotoFailed: () -> Unit,
) {
    val context = LocalContext.current
    val onFailed = onPhotoFailed
    Column(
        Modifier
            .fillMaxWidth()
            .offset(y = (-44).dp)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Rating card, lifted over the hero --------------------------------------------------------
        Reveal(0) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RatingBadge(e, large = true, height = 84.dp)
                    Text(e.rating.label(context, e.welsh), style = LocalDisplay.current.section)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            e.ratingDate?.let { stringResource(R.string.inspected_on, it.pretty()) } ?: stringResource(R.string.not_yet_inspected),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    state.dataInfo?.importedAt?.let { at ->
                        Text(
                            stringResource(R.string.data_as_of, Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().pretty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (e.newRatingPending) {
                        Tag(stringResource(R.string.new_rating_pending_title), strong = true)
                        Text(stringResource(R.string.new_rating_pending_body), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Actions ----------------------------------------------------------------------------------
        Reveal(1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                PrimaryPill(stringResource(R.string.directions), onClick = { directions(context, e) }, icon = Icons.Rounded.Directions)
                OutlinePill(stringResource(R.string.fsa_page), onClick = { open(context, Uri.parse(e.fhrsUrl)) }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
            }
        }
        when {
            e.loc?.approximate == true -> Note(stringResource(R.string.position_approx))
            e.lat == null -> Note(stringResource(R.string.position_none))
        }

        // Street photo, with the user's own Google key or Mapillary token ----------------------------
        state.photo?.let { photo ->
            Reveal(2) { PhotoCard(photo, e.name, onFailed) }
        }

        // Breakdown --------------------------------------------------------------------------------
        val rows = scoreRows(e)
        if (rows.isNotEmpty()) {
            Reveal(2) {
                InfoCard {
                    Text(stringResource(R.string.breakdown_title), style = LocalDisplay.current.section)
                    rows.forEachIndexed { i, r -> ScoreBar(stringResource(r.heading), stringResource(r.descriptor), r.step, delay = 200 + i * 140) }
                    Text(stringResource(R.string.breakdown_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (e.rating.numeric != null) {
            Note(stringResource(R.string.breakdown_unavailable))
        }

        // Right to reply ---------------------------------------------------------------------------
        e.reply?.let { raw ->
            val text = remember(raw) { replyText(raw) }
            if (text.isNotBlank()) {
                Reveal(3) {
                    InfoCard {
                        Icon(Icons.Rounded.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.reply_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Who's really cooking? --------------------------------------------------------------------
        if (state.sameSite.isNotEmpty() || state.samePostcode.isNotEmpty()) {
            Reveal(4) {
                DarkCard(stringResource(R.string.whos_cooking_title).uppercase()) {
                    if (state.sameSite.isNotEmpty()) {
                        Text(pluralStringResource(R.plurals.same_site, state.sameSite.size, state.sameSite.size), style = MaterialTheme.typography.bodyMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.sameSite.take(12).forEach { MiniRow(it, onOpen) }
                        }
                    } else {
                        Text(stringResource(R.string.whos_cooking_none_here), style = MaterialTheme.typography.bodyMedium)
                    }
                    e.pcKey?.let { pc ->
                        val more = state.samePostcode.size
                        if (more > 0 || state.sameSite.size > 12) {
                            TextLink(
                                if (more > 0) pluralStringResource(R.plurals.same_postcode, more, more, Postcodes.format(pc)) else stringResource(R.string.see_all),
                                onClick = { onOpenAddress(pc) },
                                color = Fsa.Lime,
                            )
                        }
                    }
                }
            }
        }

        // Google, with the user's own key ------------------------------------------------------------
        state.google?.let { g ->
            Reveal(5) { GoogleCard(g, state.googlePhoto, e.name, onGooglePhotoFailed) }
        }

        // OpenStreetMap: shown only when it has details for this business --------------------------
        val osm = state.osm
        if (osmEnabled && osm != null && osm.hasDetails) {
            Reveal(5) {
                InfoCard {
                    Text(stringResource(R.string.osm_title), style = LocalDisplay.current.section)
                    OsmContent(osm)
                }
            }
        }

        // History ------------------------------------------------------------------------------------
        if (state.history.isNotEmpty()) {
            Reveal(7) {
                InfoCard {
                    Text(stringResource(R.string.history_title), style = LocalDisplay.current.section)
                    state.history.forEach { h ->
                        val seen = Instant.ofEpochMilli(h.seenAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().pretty()
                        val text = when {
                            h.newRating == null -> stringResource(R.string.history_removed, seen)
                            h.oldRating != h.newRating -> stringResource(R.string.history_changed, seen, h.oldRating?.label(context) ?: "?", h.newRating.label(context))
                            else -> stringResource(R.string.history_reinspected, seen, h.newDate?.pretty() ?: "?")
                        }
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(R.string.history_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text(
            stringResource(R.string.detail_footer, e.id.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

/** Six capsules that fill one after another (0 = very good … 5 = urgent improvement). */
@Composable
private fun ScoreBar(heading: String, descriptor: String, step: Int, delay: Int) {
    val fill = remember { Animatable(0f) }
    LaunchedEffect(step) { fill.animateTo((6 - step).toFloat(), tween(durationMillis = 650, delayMillis = delay)) }
    val color = barColor(step)
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "$heading: $descriptor" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(heading, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(descriptor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0 until 6) {
                val amount = (fill.value - i).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = amount
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            }
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        }
    }
}

private fun barColor(step: Int): Color = when (step) {
    0, 1 -> Fsa.Lime
    2 -> Fsa.LightGreen
    3 -> Color(0xFFE0A800)
    else -> Fsa.Alarm
}

@Composable
private fun MiniRow(e: Establishment, onOpen: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .tactileClick { onOpen(e.id) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LocalHero.current.body)
            Text(typeLabel(LocalContext.current, e.typeId, e.typeName), style = MaterialTheme.typography.bodySmall, color = LocalHero.current.body.copy(alpha = 0.75f))
        }
        Spacer(Modifier.width(8.dp))
        RatingBadge(e, large = false, height = 32.dp)
    }
}

@Composable
private fun ContactRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .tactileClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A street-level photo. Google's images are never written to disk (its terms allow keeping only the
 * panorama ID) and carry its "Google Maps" credit; Mapillary photos credit their author (CC BY-SA 4.0).
 */
@Composable
private fun PhotoCard(photo: StreetPhoto, name: String, onFailed: () -> Unit) {
    val context = LocalContext.current
    val identity = remember { (context.applicationContext as HigieneApp).graph.identity }
    val request = remember(photo.imageUrl) {
        ImageRequest.Builder(context).data(photo.imageUrl).apply {
            if (photo is StreetPhoto.Google) {
                httpHeaders(NetworkHeaders.Builder().apply { identity.headers.forEach { (k, v) -> set(k, v) } }.build())
                diskCachePolicy(CachePolicy.DISABLED)
            }
        }.crossfade(true).build()
    }
    val viewUrl = when (photo) {
        is StreetPhoto.Google -> photo.viewUrl
        is StreetPhoto.Mapillary -> photo.viewUrl
    }
    InfoCard(padding = 8.dp) {
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.photo_desc, name),
            contentScale = ContentScale.Crop,
            onError = { onFailed() },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
                .tactileClick { open(context, Uri.parse(viewUrl)) },
        )
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            when (photo) {
                is StreetPhoto.Google -> {
                    GoogleMapsCredit()
                    val meta = listOfNotNull(photo.copyright, photo.date?.let { stringResource(R.string.photo_taken, it) }).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                is StreetPhoto.Mapillary -> Text(
                    photo.author?.let { stringResource(R.string.photo_mapillary_by, it) } ?: stringResource(R.string.photo_mapillary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Google's required credit when its content is shown without a Google map: the words "Google Maps", 12–16sp, regular weight. */
@Composable
private fun GoogleMapsCredit() {
    Text(
        "Google Maps",
        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = if (LocalIsDark.current) Color.White else Color(0xFF5E5E5E),
        maxLines = 1,
        softWrap = false,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoogleCard(g: GooglePlace, photo: GooglePhotoShown? = null, name: String = "", onPhotoFailed: () -> Unit = {}) {
    val context = LocalContext.current
    InfoCard {
        // Google's own warning comes first, before the rating it may concern.
        g.alert?.let { AlertBox(it) }
        photo?.let { GooglePhotoView(it, name, onPhotoFailed) }
        if (g.permanentlyClosed) Tag(stringResource(R.string.google_closed_permanently), strong = true)
        g.movedPlaceId?.let { moved ->
            Tag(stringResource(R.string.google_moved), strong = true)
            TextLink(stringResource(R.string.google_moved_link), onClick = {
                open(context, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(name) + "&query_place_id=" + Uri.encode(moved)))
            })
        }
        g.rating?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%.1f".format(java.util.Locale.UK, r), style = LocalDisplay.current.section)
                Spacer(Modifier.width(8.dp))
                Stars(r)
                g.ratingCount?.let { n ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        pluralStringResource(R.plurals.google_reviews, n, java.text.NumberFormat.getIntegerInstance(java.util.Locale.UK).format(n)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val price = listOfNotNull(
            g.priceLevel?.let { if (it == 0) stringResource(R.string.google_price_free) else "£".repeat(it) },
            g.priceRange,
        ).joinToString(" · ")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!g.permanentlyClosed) g.openNow?.let { open -> Tag(stringResource(if (open) R.string.google_open_now else R.string.google_closed_now), strong = open) }
            if (price.isNotEmpty()) Tag(stringResource(R.string.google_price, price))
            if (g.serviceAreaOnly) Tag(stringResource(R.string.google_service_area))
        }
        if (g.hours.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.osm_hours), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(g.hours.joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (g.extraHours.isNotEmpty()) {
            Expandable(stringResource(R.string.google_more_hours, g.extraHours.joinToString(", ") { hoursLabel(context, it.type).lowercase() })) {
                g.extraHours.forEach { h ->
                    Text(hoursLabel(context, h.type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(h.lines.joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        g.access?.takeIf { it.known }?.let { AccessRow(it) }
        g.phone?.let { p -> ContactRow(Icons.Rounded.Phone, p) { open(context, Uri.parse("tel:" + p.replace(" ", "")), Intent.ACTION_DIAL) } }
        g.website?.let { w -> ContactRow(Icons.Rounded.Public, w.removePrefix("https://").removePrefix("http://").removeSuffix("/")) { open(context, Uri.parse(w)) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (g.reviewsUri ?: g.mapsUri)?.let { u -> TextLink(stringResource(if (g.reviewsUri != null) R.string.google_read_reviews else R.string.google_open_maps), onClick = { open(context, Uri.parse(u)) }) }
            g.writeReviewUri?.let { u -> TextLink(stringResource(R.string.google_write_review), onClick = { open(context, Uri.parse(u)) }) }
        }
        // Google's terms require the words "Google Maps" with its data when no Google map is shown,
        // and a credit for any data provider it names. Kept small, at the end.
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoogleMapsCredit()
            g.attributions.forEach { a ->
                Spacer(Modifier.width(10.dp))
                SmallCredit(stringResource(R.string.google_data_from, a.provider), a.uri)
            }
        }
    }
}

/** A small, quiet credit line that opens [uri] when tapped. */
@Composable
private fun SmallCredit(text: String, uri: String?) {
    val context = LocalContext.current
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (uri != null) Modifier.clip(RoundedCornerShape(6.dp)).tactileClick(haptic = null, role = Role.Button) { open(context, Uri.parse(uri)) } else Modifier,
    )
}

/** Google's consumer alert, word for word, with its own link. */
@Composable
private fun AlertBox(a: GooglePlace.Alert) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFF4D6))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color(0xFF8A5A00), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(a.title ?: stringResource(R.string.google_alert), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF3D2A00))
            Text(a.text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3D2A00))
            a.linkUri?.let { u -> TextLink(a.linkTitle ?: stringResource(R.string.google_alert_more), onClick = { open(context, Uri.parse(u)) }, color = Color(0xFF6B4500)) }
        }
    }
}

/** A Google photo of the business. Never cached; credited to its author with a link to it on Google Maps. */
@Composable
private fun GooglePhotoView(p: GooglePhotoShown, name: String, onFailed: () -> Unit) {
    val context = LocalContext.current
    val request = remember(p.uri) {
        ImageRequest.Builder(context).data(p.uri).diskCachePolicy(CachePolicy.DISABLED).crossfade(true).build()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.google_photo_desc, name),
            contentScale = ContentScale.Crop,
            onError = { onFailed() },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
                .then(if (p.photo.mapsUri != null) Modifier.tactileClick { open(context, Uri.parse(p.photo.mapsUri)) } else Modifier),
        )
        p.photo.authors.firstOrNull()?.let { a -> SmallCredit(stringResource(R.string.google_photo_by, a.name), a.uri) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccessRow(a: GooglePlace.Access) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.AutoMirrored.Rounded.Accessible, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.google_access), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    R.string.google_access_entrance to a.entrance,
                    R.string.google_access_toilet to a.toilet,
                    R.string.google_access_seating to a.seating,
                    R.string.google_access_parking to a.parking,
                ).forEach { (label, v) ->
                    if (v == null) return@forEach
                    // The tick or cross is only a picture: say yes or no for screen readers too.
                    val said = stringResource(if (v) R.string.google_access_yes else R.string.google_access_no, stringResource(label))
                    Tag(
                        stringResource(label),
                        modifier = Modifier.clearAndSetSemantics { contentDescription = said },
                        icon = if (v) Icons.Rounded.Check else Icons.Rounded.Close,
                        strong = v,
                    )
                }
            }
        }
    }
}

private fun hoursLabel(context: Context, type: String): String = context.getString(
    when (type) {
        "KITCHEN" -> R.string.hours_kitchen
        "DELIVERY" -> R.string.hours_delivery
        "TAKEOUT" -> R.string.hours_takeaway
        "PICKUP" -> R.string.hours_pickup
        "DRIVE_THROUGH" -> R.string.hours_drive_through
        "BREAKFAST" -> R.string.hours_breakfast
        "BRUNCH" -> R.string.hours_brunch
        "LUNCH" -> R.string.hours_lunch
        "DINNER" -> R.string.hours_dinner
        else -> R.string.hours_happy_hour
    },
)

/** Five stars, filled to the nearest half. */
@Composable
private fun Stars(rating: Double) {
    val halves = (rating * 2).roundToInt().coerceIn(0, 10)
    Row {
        for (i in 1..5) {
            val icon = when {
                halves >= i * 2 -> Icons.Rounded.Star
                halves == i * 2 - 1 -> Icons.AutoMirrored.Rounded.StarHalf
                else -> Icons.Rounded.StarOutline
            }
            Icon(icon, contentDescription = null, tint = Color(0xFFF4B400), modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OsmContent(d: OsmDetails) {
    val context = LocalContext.current
    d.openingHours?.let { oh ->
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(stringResource(R.string.osm_hours), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(oh.split(';').joinToString("\n") { it.trim() }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (d.cuisine.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            d.cuisine.forEach { Tag(it.replaceFirstChar { c -> c.uppercase() }) }
            d.diets.forEach { Tag(it.replaceFirstChar { c -> c.uppercase() }, strong = true) }
        }
    }
    val facts = buildList {
        if (d.cuisine.isEmpty() && d.diets.isNotEmpty()) add(stringResource(R.string.osm_diets, d.diets.joinToString(", ")))
        d.wheelchair?.let { add(stringResource(R.string.osm_wheelchair, it)) }
        d.takeaway?.let { add(stringResource(R.string.osm_takeaway, it)) }
        d.delivery?.let { add(stringResource(R.string.osm_delivery, it)) }
        d.outdoorSeating?.let { add(stringResource(R.string.osm_outdoor, it)) }
    }
    facts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    d.website?.let { w -> ContactRow(Icons.Rounded.Public, w.removePrefix("https://").removePrefix("http://").removeSuffix("/")) { open(context, Uri.parse(w)) } }
    d.phone?.let { p -> ContactRow(Icons.Rounded.Phone, p) { open(context, Uri.parse("tel:" + p.replace(" ", "")), Intent.ACTION_DIAL) } }
    d.email?.let { m -> ContactRow(Icons.Rounded.Mail, m) { open(context, Uri.parse("mailto:$m")) } }
    d.menu?.let { m -> TextLink(stringResource(R.string.osm_menu), onClick = { open(context, Uri.parse(m)) }) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextLink(stringResource(R.string.osm_attribution), onClick = { open(context, Uri.parse("https://www.openstreetmap.org/copyright")) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        TextLink(stringResource(R.string.osm_view), onClick = { open(context, Uri.parse(d.osmUrl)) })
    }
}

private fun open(context: Context, uri: Uri, action: String = Intent.ACTION_VIEW) {
    try {
        context.startActivity(Intent(action, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app, Toast.LENGTH_SHORT).show()
    } catch (_: RuntimeException) {
        // e.g. FileUriExposedException or SecurityException from a link someone typed into OSM.
        Toast.makeText(context, R.string.no_app, Toast.LENGTH_SHORT).show()
    }
}

private fun directions(context: Context, e: Establishment) {
    val uri = if (e.lat != null && e.lon != null && e.loc?.approximate != true) {
        // Plain decimals: Double.toString gives "5.0E-4" near the Greenwich meridian.
        val ll = String.format(java.util.Locale.US, "%.6f,%.6f", e.lat, e.lon)
        Uri.parse("geo:$ll?q=$ll(${Uri.encode(e.name)})")
    } else {
        Uri.parse("geo:0,0?q=" + Uri.encode(listOfNotNull(e.name, e.address, e.postcode).joinToString(", ")))
    }
    open(context, uri)
}

private fun share(context: Context, e: Establishment) {
    val text = buildString {
        append(e.name)
        e.address?.let { append(", ").append(it) }
        e.postcode?.let { append(", ").append(it) }
        append("\n")
        append(context.getString(R.string.share_rating, e.rating.label(context, e.welsh)))
        e.ratingDate?.let { append(" · ").append(context.getString(R.string.inspected_short, it.pretty())) }
        append("\n").append(e.fhrsUrl)
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app, Toast.LENGTH_SHORT).show()
    }
}
