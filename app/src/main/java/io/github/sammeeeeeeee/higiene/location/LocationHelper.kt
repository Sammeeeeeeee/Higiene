package io.github.sammeeeeeeee.higiene.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

/**
 * One-shot device location using the platform LocationManager (no Google Play Services).
 * The position is only used on the phone to sort businesses by distance.
 */
object LocationHelper {
    private val executor = Executors.newSingleThreadExecutor()

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasFine(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(context: Context, timeoutMs: Long = 15_000): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (hasFine(context) && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            if (android.os.Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) add(0, LocationManager.FUSED_PROVIDER)
        }
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 2 * 60_000 }
            .minByOrNull { it.accuracy }
        if (recent != null) return recent
        if (providers.isEmpty()) return null
        // Ask every enabled provider at once and take the first fix: indoors GPS may never answer
        // while the network provider replies in about a second.
        val fresh = withTimeoutOrNull(timeoutMs) {
            val fixes = Channel<Location>(Channel.UNLIMITED)
            val signals = providers.map { provider ->
                CancellationSignal().also { signal ->
                    runCatching {
                        LocationManagerCompat.getCurrentLocation(lm, provider, signal, executor) { loc -> if (loc != null) fixes.trySend(loc) }
                    }
                }
            }
            try {
                fixes.receive()
            } finally {
                signals.forEach { it.cancel() }
            }
        }
        if (fresh != null) return fresh
        // Last resort: a cached fix, but only a reasonably recent one.
        return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 30 * 60_000 }
            .maxByOrNull { it.time }
    }

    fun isLocationEnabled(context: Context): Boolean =
        context.getSystemService(LocationManager::class.java)?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false
}
