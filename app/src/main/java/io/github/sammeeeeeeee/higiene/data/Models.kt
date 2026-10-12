package io.github.sammeeeeeeee.higiene.data

import java.time.LocalDate

/** Normalised rating. FHRS (England, Wales, NI) uses 0–5; FHIS (Scotland) uses Pass / Improvement Required. */
enum class Rating(val code: String, val numeric: Int?) {
    R0("0", 0), R1("1", 1), R2("2", 2), R3("3", 3), R4("4", 4), R5("5", 5),
    PASS("PASS", null),
    PASS_EAT_SAFE("PASS_EAT_SAFE", null),
    IMPROVEMENT_REQUIRED("IMPROVEMENT_REQUIRED", null),
    AWAITING_INSPECTION("AWAITING_INSPECTION", null),
    AWAITING_PUBLICATION("AWAITING_PUBLICATION", null),
    EXEMPT("EXEMPT", null),
    UNKNOWN("UNKNOWN", null);

    val isFhis get() = this == PASS || this == PASS_EAT_SAFE || this == IMPROVEMENT_REQUIRED

    /** True when the business has an actual inspection result (not exempt/awaiting). */
    val isResult get() = numeric != null || isFhis

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: String?): Rating = byCode[code] ?: UNKNOWN

        /** Maps the CSV RatingValue column. FHIS values contain spaces; FHRS ones don't. */
        fun fromCsv(value: String): Rating = when (value.trim().lowercase().replace(" ", "")) {
            "0" -> R0
            "1" -> R1
            "2" -> R2
            "3" -> R3
            "4" -> R4
            "5" -> R5
            "pass" -> PASS
            "passandeatsafe" -> PASS_EAT_SAFE
            "improvementrequired" -> IMPROVEMENT_REQUIRED
            "awaitinginspection" -> AWAITING_INSPECTION
            "awaitingpublication" -> AWAITING_PUBLICATION
            "exempt" -> EXEMPT
            else -> UNKNOWN
        }
    }
}

/** Where a business's map position came from. */
enum class LocSource(val code: Int) {
    /** Published by the FSA. */
    FSA(0),
    /** Centre of its postcode, from OS Code-Point Open. */
    POSTCODE_OS(1),
    /** Average of other FSA-located businesses sharing its postcode (used for NI and retired postcodes). */
    POSTCODE_FSA(2);

    val approximate get() = this != FSA

    companion object {
        fun from(code: Int?): LocSource? = entries.firstOrNull { it.code == code }
    }
}

data class Establishment(
    val id: Long,
    val name: String,
    val address: String?,
    val pcKey: String?,
    val typeId: Int,
    val typeName: String,
    val laCode: Int,
    val rating: Rating,
    val ratingDate: LocalDate?,
    val hygiene: Int?,
    val structural: Int?,
    val confidence: Int?,
    val lat: Double?,
    val lon: Double?,
    val loc: LocSource?,
    val newRatingPending: Boolean,
    val reply: String?,
    /** Published by a Welsh council (bilingual badge). */
    val welsh: Boolean,
    /** Scottish Food Hygiene Information Scheme. */
    val fhis: Boolean,
    val siteKey: Long?,
) {
    val fhrsUrl get() = "https://ratings.food.gov.uk/business/$id"
    val postcode: String? get() = pcKey?.let { Postcodes.format(it) }
}

data class Listing(val est: Establishment, val distanceM: Double?)

data class BusinessType(val id: Int, val name: String)

data class LocalAuthority(val code: Int, val name: String, val email: String?, val url: String?)

data class HistoryEntry(
    val seenAtMillis: Long,
    val oldRating: Rating?,
    val newRating: Rating?,
    val oldDate: LocalDate?,
    val newDate: LocalDate?,
)

/** Ratings change seen during a refresh for a favourite business. */
data class FavouriteChange(val id: Long, val name: String, val old: Rating, val new: Rating?, val newDate: LocalDate?)

enum class SortOrder { DISTANCE, RATING, RECENT, NAME }

data class Filters(
    val typeIds: Set<Int> = emptySet(),
    /** Minimum FHRS rating 0..5, or null for any. Scottish "Improvement required" is hidden when set. */
    val minRating: Int? = null,
    val hideExempt: Boolean = false,
    val hideAwaiting: Boolean = false,
    /** Only businesses inspected within this many months, or null for any. */
    val inspectedWithinMonths: Int? = null,
    val sort: SortOrder = SortOrder.DISTANCE,
) {
    val active get() = typeIds.isNotEmpty() || minRating != null || hideExempt || hideAwaiting || inspectedWithinMonths != null
}

/** A place to measure distances from. */
data class Centre(val lat: Double, val lon: Double, val label: String, val fromGps: Boolean, val approximate: Boolean = false)
