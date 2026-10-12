package io.github.sammeeeeeeee.higiene.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.Factory
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.Establishment
import io.github.sammeeeeeeee.higiene.data.Rating
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Rating.label(context: Context, welsh: Boolean = false): String = context.getString(
    when (this) {
        Rating.R5 -> R.string.rating_5
        Rating.R4 -> R.string.rating_4
        Rating.R3 -> R.string.rating_3
        Rating.R2 -> R.string.rating_2
        Rating.R1 -> R.string.rating_1
        Rating.R0 -> R.string.rating_0
        Rating.PASS -> R.string.rating_pass
        Rating.PASS_EAT_SAFE -> R.string.rating_pass_eat_safe
        Rating.IMPROVEMENT_REQUIRED -> R.string.rating_improvement_required
        Rating.AWAITING_INSPECTION -> if (welsh) R.string.rating_awaited else R.string.rating_awaiting_inspection
        Rating.AWAITING_PUBLICATION -> R.string.rating_awaiting_publication
        Rating.EXEMPT -> R.string.rating_exempt
        Rating.UNKNOWN -> R.string.rating_unknown
    },
)

/** Short accessible description of a rating, e.g. "Food hygiene rating 5: very good". */
fun Rating.contentDescription(context: Context, welsh: Boolean = false): String =
    if (numeric != null) context.getString(R.string.rating_cd_numeric, numeric, label(context)) else label(context, welsh)

/**
 * Official FSA badge artwork in assets/badges (from the FSA's images.zip), never altered.
 * Mirrors the CSV RatingKey: Scottish businesses get FHIS images, Welsh councils' get bilingual
 * FHRS images (their "awaiting inspection" is the Welsh "rating awaited" sticker). Null if unknown.
 */
fun badgeAsset(e: Establishment, large: Boolean): String? {
    if (e.fhis) {
        val name = when (e.rating) {
            Rating.PASS -> "fhis_pass"
            Rating.PASS_EAT_SAFE -> "fhis_pass_and_eat_safe"
            Rating.IMPROVEMENT_REQUIRED -> "fhis_improvement_required"
            Rating.AWAITING_INSPECTION -> "fhis_awaiting_inspection"
            Rating.AWAITING_PUBLICATION -> "fhis_awaiting_publication"
            Rating.EXEMPT -> "fhis_exempt"
            else -> return null
        }
        return "file:///android_asset/badges/fhis/$name.jpg"
    }
    val lang = if (e.welsh) "cy-gb" else "en-gb"
    val name = when {
        e.rating.numeric != null -> "fhrs_${e.rating.numeric}_$lang"
        e.rating == Rating.EXEMPT -> "fhrs_exempt_$lang"
        e.rating == Rating.AWAITING_PUBLICATION -> "fhrs_awaitingpublication_$lang"
        e.rating == Rating.AWAITING_INSPECTION -> if (e.welsh) "fhrs_ratingawaited_cy-gb" else "fhrs_awaitinginspection_en-gb"
        else -> return null
    }
    return "file:///android_asset/badges/fhrs/${if (large) "large" else "small"}/$name.svg"
}

/** Short, friendly name for an FSA business type (the FSA's own names are slash lists). */
fun typeLabel(context: Context, typeId: Int, fsaName: String): String {
    val res = when (typeId) {
        1 -> R.string.type_restaurant
        7844 -> R.string.type_takeaway
        7843 -> R.string.type_pub
        7841 -> R.string.type_other_catering
        7846 -> R.string.type_mobile
        4613 -> R.string.type_shop
        7840 -> R.string.type_supermarket
        7842 -> R.string.type_hotel
        5 -> R.string.type_care
        7845 -> R.string.type_school
        7839 -> R.string.type_manufacturer
        7 -> R.string.type_distributor
        7838 -> R.string.type_farm
        14 -> R.string.type_importer
        else -> return fsaName
    }
    return context.getString(res)
}

fun typeIcon(typeId: Int): ImageVector = when (typeId) {
    1 -> Icons.Rounded.Restaurant
    7844 -> Icons.Rounded.Fastfood
    7843 -> Icons.Rounded.LocalBar
    7841 -> Icons.Rounded.RestaurantMenu
    7846 -> Icons.Rounded.DeliveryDining
    7840 -> Icons.Rounded.ShoppingCart
    7842 -> Icons.Rounded.Hotel
    5 -> Icons.Rounded.LocalHospital
    7845 -> Icons.Rounded.School
    7839 -> Icons.Rounded.Factory
    7 -> Icons.Rounded.LocalShipping
    7838 -> Icons.Rounded.Agriculture
    14 -> Icons.Rounded.DirectionsBoat
    else -> Icons.Rounded.Storefront
}

data class ScoreRow(val heading: Int, val score: Int, val descriptor: Int, val step: Int)

/**
 * FSA score descriptors (lower is better). Hygiene and structural use 0–25 in steps of 5;
 * confidence in management uses 0, 5, 10, 20, 30. Source: FSA ScoreDescriptors lookup data.
 */
fun scoreRows(e: Establishment): List<ScoreRow> {
    if (e.rating.numeric == null) return emptyList()
    val rows = ArrayList<ScoreRow>()
    fun std(score: Int): Pair<Int, Int>? = when (score) {
        0 -> R.string.score_very_good to 0
        5 -> R.string.score_good to 1
        10 -> R.string.score_generally_satisfactory to 2
        15 -> R.string.score_improvement_necessary to 3
        20 -> R.string.score_major_improvement to 4
        25 -> R.string.score_urgent_improvement to 5
        else -> null
    }
    fun conf(score: Int): Pair<Int, Int>? = when (score) {
        0 -> R.string.score_very_good to 0
        5 -> R.string.score_good to 1
        10 -> R.string.score_generally_satisfactory to 2
        20 -> R.string.score_major_improvement to 4
        30 -> R.string.score_urgent_improvement to 5
        else -> null
    }
    e.hygiene?.let { s -> std(s)?.let { rows += ScoreRow(R.string.score_hygiene, s, it.first, it.second) } }
    e.structural?.let { s -> std(s)?.let { rows += ScoreRow(R.string.score_structural, s, it.first, it.second) } }
    e.confidence?.let { s -> conf(s)?.let { rows += ScoreRow(R.string.score_confidence, s, it.first, it.second) } }
    return rows
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

fun LocalDate.pretty(): String = format(dateFormat)

fun formatDistance(context: Context, metres: Double?, miles: Boolean): String? {
    if (metres == null) return null
    return if (miles) {
        val mi = metres / 1609.344
        when {
            mi < 0.1 -> context.getString(R.string.distance_yards, (metres * 1.0936133).toInt().coerceAtLeast(1))
            mi < 10 -> context.getString(R.string.distance_miles, String.format(Locale.UK, "%.1f", mi))
            else -> context.getString(R.string.distance_miles, String.format(Locale.UK, "%.0f", mi))
        }
    } else {
        when {
            metres < 1000 -> context.getString(R.string.distance_m, metres.toInt())
            metres < 10_000 -> context.getString(R.string.distance_km, String.format(Locale.UK, "%.1f", metres / 1000))
            else -> context.getString(R.string.distance_km, String.format(Locale.UK, "%.0f", metres / 1000))
        }
    }
}

/** The FSA's right-to-reply text is HTML that has been entity-escaped twice. */
fun replyText(raw: String): String {
    @Suppress("DEPRECATION")
    val once = android.text.Html.fromHtml(raw).toString()
    @Suppress("DEPRECATION")
    val twice = android.text.Html.fromHtml(once).toString()
    return twice.replace(' ', ' ').lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
}
