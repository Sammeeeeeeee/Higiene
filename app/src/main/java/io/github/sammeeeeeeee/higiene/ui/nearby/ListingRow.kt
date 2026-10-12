package io.github.sammeeeeeeee.higiene.ui.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.Brands
import io.github.sammeeeeeeee.higiene.data.Establishment
import io.github.sammeeeeeeee.higiene.ui.components.BusinessLogo
import io.github.sammeeeeeeee.higiene.ui.components.RatingBadge
import io.github.sammeeeeeeee.higiene.ui.components.Tag
import io.github.sammeeeeeeee.higiene.ui.components.Tile
import io.github.sammeeeeeeee.higiene.ui.contentDescription
import io.github.sammeeeeeeee.higiene.ui.formatDistance
import io.github.sammeeeeeeee.higiene.ui.pretty
import io.github.sammeeeeeeee.higiene.ui.sharedElement
import io.github.sammeeeeeeee.higiene.ui.typeLabel

@Composable
fun ListingRow(row: Row, miles: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    EstablishmentRow(row.listing.est, row.listing.distanceM, row.brand, miles, onClick, modifier)
}

/** A business as a pressable tile: logo, name, distance pill, inspection date, official badge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EstablishmentRow(
    e: Establishment,
    distanceM: Double?,
    brand: Brands.Brand?,
    miles: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shareLogo: Boolean = true,
) {
    val context = LocalContext.current
    val distance = formatDistance(context, distanceM, miles)
    val inspected = e.ratingDate?.let { stringResource(R.string.inspected_short, it.pretty()) } ?: stringResource(R.string.not_yet_inspected)
    val type = typeLabel(context, e.typeId, e.typeName)
    val cd = listOfNotNull(e.name, e.rating.contentDescription(context, e.welsh), distance, type, inspected).joinToString(", ")
    Tile(onClick = onClick, modifier = modifier.semantics(mergeDescendants = true) { contentDescription = cd }) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BusinessLogo(e.typeId, brand, modifier = if (shareLogo) Modifier.sharedElement("logo-${e.id}") else Modifier)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    if (distance != null) Tag(distance, strong = true, icon = Icons.Rounded.NearMe)
                    if (type.isNotEmpty()) {
                        Text(type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(inspected, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                val tags = buildList {
                    if (e.newRatingPending) add(stringResource(R.string.tag_new_rating_pending))
                    if (e.loc?.approximate == true) add(stringResource(R.string.tag_approx_position))
                    if (e.lat == null) add(stringResource(R.string.tag_no_position))
                }
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                        tags.forEach { Tag(it) }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            RatingBadge(e, large = false, height = 38.dp)
        }
    }
}
