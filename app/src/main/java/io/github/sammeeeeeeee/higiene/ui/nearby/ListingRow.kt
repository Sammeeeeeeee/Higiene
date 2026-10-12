package io.github.sammeeeeeeee.higiene.ui.nearby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.Establishment
import io.github.sammeeeeeeee.higiene.ui.components.BusinessLogo
import io.github.sammeeeeeeee.higiene.ui.components.RatingBadge
import io.github.sammeeeeeeee.higiene.ui.components.Tag
import io.github.sammeeeeeeee.higiene.ui.contentDescription
import io.github.sammeeeeeeee.higiene.ui.formatDistance
import io.github.sammeeeeeeee.higiene.ui.pretty

@Composable
fun ListingRow(row: Row, miles: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    EstablishmentRow(row.listing.est, row.listing.distanceM, row.brand, miles, onClick, modifier)
}

@Composable
fun EstablishmentRow(
    e: Establishment,
    distanceM: Double?,
    brand: io.github.sammeeeeeeee.higiene.data.Brands.Brand?,
    miles: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val distance = formatDistance(context, distanceM, miles)
    val inspected = e.ratingDate?.let { stringResource(R.string.inspected_short, it.pretty()) } ?: stringResource(R.string.not_yet_inspected)
    val meta = listOfNotNull(distance, e.typeName.ifEmpty { null }).joinToString(" · ")
    val cd = listOfNotNull(e.name, e.rating.contentDescription(context, e.welsh), distance, e.typeName, inspected).joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = cd }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BusinessLogo(e.typeId, brand)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(inspected, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            val tags = buildList {
                if (e.newRatingPending) add(stringResource(R.string.tag_new_rating_pending))
                if (e.loc?.approximate == true) add(stringResource(R.string.tag_approx_position))
                if (e.lat == null) add(stringResource(R.string.tag_no_position))
            }
            if (tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    tags.forEach { Tag(it) }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        RatingBadge(e, large = false)
    }
}
