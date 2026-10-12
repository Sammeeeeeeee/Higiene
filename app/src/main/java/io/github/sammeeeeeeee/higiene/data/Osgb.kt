package io.github.sammeeeeeeee.higiene.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * British National Grid (OSGB36) easting/northing to WGS84 latitude/longitude.
 *
 * Inverse Transverse Mercator on the Airy 1830 ellipsoid, then a 7-parameter Helmert shift to
 * WGS84. Formulae and constants from Ordnance Survey, "A guide to coordinate systems in Great
 * Britain" (annexes B, C, E). Helmert accuracy is about 5 m, ample for postcode centroids.
 */
object Osgb {
    // Airy 1830
    private const val A = 6377563.396
    private const val B = 6356256.909
    // National Grid projection
    private const val F0 = 0.9996012717
    private val LAT0 = Math.toRadians(49.0)
    private val LON0 = Math.toRadians(-2.0)
    private const val N0 = -100000.0
    private const val E0 = 400000.0

    /** OSGB36 geodetic latitude/longitude in degrees for a grid reference. */
    fun gridToOsgb36(easting: Double, northing: Double): DoubleArray {
        val e2 = 1 - (B * B) / (A * A)
        val n = (A - B) / (A + B)
        val n2 = n * n
        val n3 = n2 * n

        var lat = LAT0
        var m = 0.0
        do {
            lat = (northing - N0 - m) / (A * F0) + lat
            val ma = (1 + n + 5.0 / 4 * n2 + 5.0 / 4 * n3) * (lat - LAT0)
            val mb = (3 * n + 3 * n2 + 21.0 / 8 * n3) * sin(lat - LAT0) * cos(lat + LAT0)
            val mc = (15.0 / 8 * n2 + 15.0 / 8 * n3) * sin(2 * (lat - LAT0)) * cos(2 * (lat + LAT0))
            val md = 35.0 / 24 * n3 * sin(3 * (lat - LAT0)) * cos(3 * (lat + LAT0))
            m = B * F0 * (ma - mb + mc - md)
        } while (northing - N0 - m >= 0.00001)

        val sinLat = sin(lat)
        val nu = A * F0 / sqrt(1 - e2 * sinLat * sinLat)
        val rho = A * F0 * (1 - e2) / (1 - e2 * sinLat * sinLat).pow(1.5)
        val eta2 = nu / rho - 1

        val tanLat = tan(lat)
        val tan2 = tanLat * tanLat
        val tan4 = tan2 * tan2
        val tan6 = tan4 * tan2
        val secLat = 1 / cos(lat)
        val nu3 = nu * nu * nu
        val nu5 = nu3 * nu * nu
        val nu7 = nu5 * nu * nu

        val vii = tanLat / (2 * rho * nu)
        val viii = tanLat / (24 * rho * nu3) * (5 + 3 * tan2 + eta2 - 9 * tan2 * eta2)
        val ix = tanLat / (720 * rho * nu5) * (61 + 90 * tan2 + 45 * tan4)
        val x = secLat / nu
        val xi = secLat / (6 * nu3) * (nu / rho + 2 * tan2)
        val xii = secLat / (120 * nu5) * (5 + 28 * tan2 + 24 * tan4)
        val xiia = secLat / (5040 * nu7) * (61 + 662 * tan2 + 1320 * tan4 + 720 * tan6)

        val de = easting - E0
        val de2 = de * de
        val de3 = de2 * de
        val de4 = de3 * de
        val de5 = de4 * de
        val de6 = de5 * de
        val de7 = de6 * de

        val outLat = lat - vii * de2 + viii * de4 - ix * de6
        val outLon = LON0 + x * de - xi * de3 + xii * de5 - xiia * de7
        return doubleArrayOf(Math.toDegrees(outLat), Math.toDegrees(outLon))
    }

    /** WGS84 latitude/longitude in degrees for a British National Grid reference. */
    fun gridToWgs84(easting: Double, northing: Double): DoubleArray {
        val osgb = gridToOsgb36(easting, northing)
        return helmertOsgb36ToWgs84(osgb[0], osgb[1])
    }

    private fun helmertOsgb36ToWgs84(latDeg: Double, lonDeg: Double): DoubleArray {
        // Cartesian on Airy 1830 (height 0).
        val lat = Math.toRadians(latDeg)
        val lon = Math.toRadians(lonDeg)
        val e2 = 1 - (B * B) / (A * A)
        val nu = A / sqrt(1 - e2 * sin(lat) * sin(lat))
        val x1 = nu * cos(lat) * cos(lon)
        val y1 = nu * cos(lat) * sin(lon)
        val z1 = (1 - e2) * nu * sin(lat)

        // OSGB36 -> WGS84: the published WGS84 -> OSGB36 parameters with signs reversed.
        val tx = 446.448
        val ty = -125.157
        val tz = 542.060
        val s = -20.4894e-6
        val rx = Math.toRadians(0.1502 / 3600)
        val ry = Math.toRadians(0.2470 / 3600)
        val rz = Math.toRadians(0.8421 / 3600)

        val x2 = tx + (1 + s) * x1 - rz * y1 + ry * z1
        val y2 = ty + rz * x1 + (1 + s) * y1 - rx * z1
        val z2 = tz - ry * x1 + rx * y1 + (1 + s) * z1

        // Back to geodetic on WGS84.
        val a = 6378137.0
        val b = 6356752.3142
        val e2w = 1 - (b * b) / (a * a)
        val p = sqrt(x2 * x2 + y2 * y2)
        var phi = atan2(z2, p * (1 - e2w))
        var prev: Double
        do {
            prev = phi
            val nuw = a / sqrt(1 - e2w * sin(phi) * sin(phi))
            phi = atan2(z2 + e2w * nuw * sin(phi), p)
        } while (kotlin.math.abs(phi - prev) > 1e-12)
        val lam = atan2(y2, x2)
        return doubleArrayOf(Math.toDegrees(phi), Math.toDegrees(lam))
    }
}
