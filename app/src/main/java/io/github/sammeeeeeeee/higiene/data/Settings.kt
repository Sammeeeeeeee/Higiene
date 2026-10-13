package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class RefreshInterval(val days: Long?) { DAILY(1), WEEKLY(7), MANUAL(null) }

data class UserSettings(
    val refresh: RefreshInterval = RefreshInterval.DAILY,
    val wifiOnly: Boolean = true,
    val osmDetails: Boolean = true,
    val brandLogos: Boolean = true,
    val miles: Boolean = true,
    /** Last place the list was centred on. Stored only on this phone. */
    val lastCentre: Centre? = null,
    /** The user's own Google Maps Platform key; blank = no Google cards. */
    val googleKey: String = "",
    /** The user's own Mapillary client token; blank = no Mapillary photos. */
    val mapillaryToken: String = "",
)

private val Context.store by preferencesDataStore(name = "settings")

class Settings(private val context: Context) {
    private object K {
        val refresh = stringPreferencesKey("refresh")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val osm = booleanPreferencesKey("osm_details")
        val logos = booleanPreferencesKey("brand_logos")
        val miles = booleanPreferencesKey("miles")
        val lat = doublePreferencesKey("centre_lat")
        val lon = doublePreferencesKey("centre_lon")
        val label = stringPreferencesKey("centre_label")
        val gps = booleanPreferencesKey("centre_gps")
        val approx = booleanPreferencesKey("centre_approx")
        val googleKey = stringPreferencesKey("google_key")
        val mapillary = stringPreferencesKey("mapillary_token")
    }

    val flow: Flow<UserSettings> = context.store.data.map { p ->
        val lat = p[K.lat]
        val lon = p[K.lon]
        UserSettings(
            refresh = p[K.refresh]?.let { runCatching { RefreshInterval.valueOf(it) }.getOrNull() } ?: RefreshInterval.DAILY,
            wifiOnly = p[K.wifiOnly] ?: true,
            osmDetails = p[K.osm] ?: true,
            brandLogos = p[K.logos] ?: true,
            miles = p[K.miles] ?: true,
            lastCentre = if (lat != null && lon != null) Centre(lat, lon, p[K.label] ?: "", p[K.gps] ?: false, p[K.approx] ?: false) else null,
            googleKey = p[K.googleKey].orEmpty(),
            mapillaryToken = p[K.mapillary].orEmpty(),
        )
    }

    suspend fun current(): UserSettings = flow.first()

    suspend fun setRefresh(v: RefreshInterval) = context.store.edit { it[K.refresh] = v.name }
    suspend fun setWifiOnly(v: Boolean) = context.store.edit { it[K.wifiOnly] = v }
    suspend fun setOsmDetails(v: Boolean) = context.store.edit { it[K.osm] = v }
    suspend fun setBrandLogos(v: Boolean) = context.store.edit { it[K.logos] = v }
    suspend fun setMiles(v: Boolean) = context.store.edit { it[K.miles] = v }
    suspend fun setGoogleKey(v: String) = context.store.edit { it[K.googleKey] = v.trim() }
    suspend fun setMapillaryToken(v: String) = context.store.edit { it[K.mapillary] = v.trim() }

    suspend fun setLastCentre(c: Centre) = context.store.edit {
        it[K.lat] = c.lat
        it[K.lon] = c.lon
        it[K.label] = c.label
        it[K.gps] = c.fromGps
        it[K.approx] = c.approximate
    }
}
