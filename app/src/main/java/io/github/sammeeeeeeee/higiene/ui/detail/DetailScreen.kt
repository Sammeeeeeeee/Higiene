package io.github.sammeeeeeeee.higiene.ui.detail

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.Establishment
import io.github.sammeeeeeeee.higiene.data.OsmClient
import io.github.sammeeeeeeee.higiene.data.OsmDetails
import io.github.sammeeeeeeee.higiene.data.OsmResult
import io.github.sammeeeeeeee.higiene.data.Postcodes
import io.github.sammeeeeeeee.higiene.ui.components.BusinessLogo
import io.github.sammeeeeeeee.higiene.ui.components.Hairline
import io.github.sammeeeeeeee.higiene.ui.components.InfoCard
import io.github.sammeeeeeeee.higiene.ui.components.PrimaryPill
import io.github.sammeeeeeeee.higiene.ui.components.RatingBadge
import io.github.sammeeeeeeee.higiene.ui.components.SectionTitle
import io.github.sammeeeeeeee.higiene.ui.components.Tag
import io.github.sammeeeeeeee.higiene.ui.components.TextLink
import io.github.sammeeeeeeee.higiene.ui.formatDistance
import io.github.sammeeeeeeee.higiene.ui.label
import io.github.sammeeeeeeee.higiene.ui.pretty
import io.github.sammeeeeeeee.higiene.ui.replyText
import io.github.sammeeeeeeee.higiene.ui.scoreRows
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
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val e = state.est
    Column(Modifier.fillMaxSize()) {
        val hero = LocalHero.current
        Column(
            Modifier
                .fillMaxWidth()
                .background(hero.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp, end = 4.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = hero.body) }
                Spacer(Modifier.weight(1f))
                if (e != null) {
                    IconButton(onClick = { share(context, e) }) { Icon(Icons.Rounded.Share, stringResource(R.string.share), tint = hero.body) }
                    IconButton(onClick = {
                        val on = vm.toggleFavourite()
                        if (on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Icon(
                            if (state.favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            stringResource(if (state.favourite) R.string.unfavourite else R.string.favourite),
                            tint = if (state.favourite) hero.accent else hero.body,
                        )
                    }
                }
            }
            if (e != null) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BusinessLogo(e.typeId, state.osmBrand ?: state.brand, size = 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.typeName, style = MaterialTheme.typography.labelMedium, color = hero.body.copy(alpha = 0.8f))
                        state.distanceM?.let {
                            Text(formatDistance(context, it, settings.miles) ?: "", style = MaterialTheme.typography.labelMedium, color = hero.body.copy(alpha = 0.8f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(e.name, style = LocalDisplay.current.title, color = hero.headline, modifier = Modifier.padding(horizontal = 12.dp))
                e.address?.let { addr ->
                    Spacer(Modifier.height(8.dp))
                    Text(listOfNotNull(addr, e.postcode).joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = hero.body, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            e == null -> Text(stringResource(R.string.detail_missing), Modifier.padding(24.dp))
            else -> Body(state, e, settings.osmDetails, onOpen, onOpenAddress, onLoadOsm = { vm.loadOsm() })
        }
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
    onLoadOsm: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        // Rating -------------------------------------------------------------------------------
        RatingBadge(e, large = true, height = 80.dp)
        Text(e.rating.label(context, e.welsh), style = MaterialTheme.typography.headlineSmall)
        Text(
            e.ratingDate?.let { stringResource(R.string.inspected_on, it.pretty()) } ?: stringResource(R.string.not_yet_inspected),
            style = MaterialTheme.typography.bodyMedium,
        )
        state.dataInfo?.importedAt?.let { at ->
            Text(
                stringResource(R.string.data_as_of, Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().pretty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (e.newRatingPending) {
            InfoCard {
                Text(stringResource(R.string.new_rating_pending_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.new_rating_pending_body), style = MaterialTheme.typography.bodyMedium)
            }
        }

        val rows = scoreRows(e)
        if (rows.isNotEmpty()) {
            SectionTitle(stringResource(R.string.breakdown_title))
            rows.forEach { r -> ScoreBar(stringResource(r.heading), stringResource(r.descriptor), r.step) }
            Text(stringResource(R.string.breakdown_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (e.rating.numeric != null) {
            Text(stringResource(R.string.breakdown_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        e.reply?.let { raw ->
            val text = remember(raw) { replyText(raw) }
            if (text.isNotBlank()) {
                SectionTitle(stringResource(R.string.reply_title))
                InfoCard { Text(text, style = MaterialTheme.typography.bodyMedium) }
            }
        }

        // Actions ------------------------------------------------------------------------------
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            PrimaryPill(stringResource(R.string.directions), onClick = { directions(context, e) }, icon = Icons.Rounded.Directions)
            TextLink(stringResource(R.string.fsa_page), onClick = { open(context, Uri.parse(e.fhrsUrl)) })
        }
        if (e.loc?.approximate == true) {
            Text(stringResource(R.string.position_approx), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (e.lat == null) {
            Text(stringResource(R.string.position_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Who's really cooking? ----------------------------------------------------------------
        if (state.sameSite.isNotEmpty() || state.samePostcode.isNotEmpty()) {
            SectionTitle(stringResource(R.string.whos_cooking_title))
            if (state.sameSite.isNotEmpty()) {
                Text(pluralStringResource(R.plurals.same_site, state.sameSite.size, state.sameSite.size), style = MaterialTheme.typography.bodyMedium)
                state.sameSite.take(12).forEach { MiniRow(it, onOpen) }
                if (state.sameSite.size > 12) e.pcKey?.let { pc -> TextLink(stringResource(R.string.see_all), onClick = { onOpenAddress(pc) }) }
            }
            if (state.samePostcode.isNotEmpty()) {
                e.pcKey?.let { pc ->
                    TextLink(
                        pluralStringResource(R.plurals.same_postcode, state.samePostcode.size, state.samePostcode.size, Postcodes.format(pc)),
                        onClick = { onOpenAddress(pc) },
                    )
                }
            }
        }

        // Council -------------------------------------------------------------------------------
        SectionTitle(stringResource(R.string.council_title))
        InfoCard {
            Text(state.authority?.name ?: stringResource(R.string.council_unknown), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.council_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.authority?.email?.let { email -> TextLink(email, onClick = { open(context, Uri.parse("mailto:$email")) }) }
            state.authority?.url?.let { url -> TextLink(url.removePrefix("http://").removePrefix("https://"), onClick = { open(context, Uri.parse(url)) }) }
        }

        // OpenStreetMap ------------------------------------------------------------------------
        if (osmEnabled) {
            SectionTitle(stringResource(R.string.osm_title))
            when (val osm = state.osm) {
                null -> if (state.osmLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                is OsmResult.Found -> OsmCard(osm.details)
                OsmResult.NotMapped -> Text(stringResource(R.string.osm_not_mapped), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OsmResult.Throttled -> Text(stringResource(R.string.osm_throttled, OsmClient.DAILY_CAP), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                is OsmResult.Failed -> Column {
                    Text(stringResource(R.string.osm_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextLink(stringResource(R.string.try_again), onClick = onLoadOsm)
                }
            }
        }

        // History -------------------------------------------------------------------------------
        if (state.history.isNotEmpty()) {
            SectionTitle(stringResource(R.string.history_title))
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

        Hairline()
        Text(
            stringResource(R.string.detail_footer, e.id.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ScoreBar(heading: String, descriptor: String, step: Int) {
    // step 0 = very good ... 5 = urgent improvement necessary
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "$heading: $descriptor" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(heading, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(descriptor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 5 downTo 0) {
                val filled = 5 - step >= 5 - i
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(if (filled) barColor(step) else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
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
            .clip(MaterialTheme.shapes.small)
            .clickable { onOpen(e.id) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.titleSmall)
            Text(e.typeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RatingBadge(e, large = false, height = 32.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OsmCard(d: OsmDetails) {
    val context = LocalContext.current
    InfoCard {
        d.openingHours?.let { oh ->
            Text(stringResource(R.string.osm_hours), style = MaterialTheme.typography.titleSmall)
            Text(oh.split(';').joinToString("\n") { it.trim() }, style = MaterialTheme.typography.bodyMedium)
        }
        if (d.cuisine.isNotEmpty()) {
            Text(stringResource(R.string.osm_cuisine), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.cuisine.forEach { Tag(it.replaceFirstChar { c -> c.uppercase() }) }
            }
        }
        val facts = buildList {
            if (d.diets.isNotEmpty()) add(stringResource(R.string.osm_diets, d.diets.joinToString(", ")))
            d.wheelchair?.let { add(stringResource(R.string.osm_wheelchair, it)) }
            d.takeaway?.let { add(stringResource(R.string.osm_takeaway, it)) }
            d.delivery?.let { add(stringResource(R.string.osm_delivery, it)) }
            d.outdoorSeating?.let { add(stringResource(R.string.osm_outdoor, it)) }
        }
        facts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        d.website?.let { w -> TextLink(w.removePrefix("https://").removePrefix("http://").removeSuffix("/"), onClick = { open(context, Uri.parse(if (w.startsWith("http")) w else "https://$w")) }) }
        d.menu?.let { m -> TextLink(stringResource(R.string.osm_menu), onClick = { open(context, Uri.parse(m)) }) }
        d.phone?.let { p -> TextLink(p, onClick = { open(context, Uri.parse("tel:" + p.replace(" ", "")), Intent.ACTION_DIAL) }) }
        d.email?.let { m -> TextLink(m, onClick = { open(context, Uri.parse("mailto:$m")) }) }
        if (d.openingHours == null && d.cuisine.isEmpty() && facts.isEmpty() && d.website == null && d.phone == null) {
            Text(stringResource(R.string.osm_sparse), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            stringResource(R.string.osm_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { open(context, Uri.parse("https://www.openstreetmap.org/copyright")) },
        )
        TextLink(stringResource(R.string.osm_view), onClick = { open(context, Uri.parse(d.osmUrl)) })
    }
}

private fun open(context: Context, uri: Uri, action: String = Intent.ACTION_VIEW) {
    try {
        context.startActivity(Intent(action, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app, Toast.LENGTH_SHORT).show()
    }
}

private fun directions(context: Context, e: Establishment) {
    val uri = if (e.lat != null && e.lon != null && e.loc?.approximate != true) {
        Uri.parse("geo:${e.lat},${e.lon}?q=${e.lat},${e.lon}(${Uri.encode(e.name)})")
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
    context.startActivity(Intent.createChooser(send, null))
}
