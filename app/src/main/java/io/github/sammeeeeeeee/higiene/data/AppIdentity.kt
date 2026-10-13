package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * The app's package name and signing-certificate SHA-1. Google checks both (sent as the
 * X-Android-Package and X-Android-Cert headers) when the user restricts their key to this app.
 */
data class AppIdentity(val packageName: String, val certSha1: String?) {
    /** The colon-separated form the Google Cloud console shows. */
    val certSha1Colons: String? get() = certSha1?.chunked(2)?.joinToString(":")

    val headers: Map<String, String>
        get() = buildMap {
            put("X-Android-Package", packageName)
            certSha1?.let { put("X-Android-Cert", it) }
        }

    companion object {
        fun of(context: Context): AppIdentity {
            val pm = context.packageManager
            val cert: ByteArray? = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
                }
            }.getOrNull()
            val sha1 = cert?.let { MessageDigest.getInstance("SHA-1").digest(it).joinToString("") { b -> "%02X".format(b) } }
            return AppIdentity(context.packageName, sha1)
        }
    }
}
