package io.github.sammeeeeeeee.higiene.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.sammeeeeeeee.higiene.MainActivity
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.FavouriteChange
import io.github.sammeeeeeeee.higiene.data.FsaImporter
import io.github.sammeeeeeeee.higiene.ui.label
import io.github.sammeeeeeeee.higiene.ui.pretty

object Notifications {
    private const val CH_PROGRESS = "refresh"
    private const val CH_CHANGES = "rating_changes"
    const val PROGRESS_ID = 1
    private const val CHANGES_ID = 1000
    const val EXTRA_ESTABLISHMENT = "establishment"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, context.getString(R.string.channel_refresh), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CHANGES, context.getString(R.string.channel_changes), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun progress(context: Context, stage: FsaImporter.Stage?, done: Long, total: Long, postcodes: Boolean = false): Notification {
        val text = when {
            postcodes -> context.getString(R.string.progress_postcodes)
            stage == FsaImporter.Stage.DOWNLOADING && total > 0 ->
                context.getString(R.string.progress_downloading_mb, done / 1_000_000, total / 1_000_000)
            stage == FsaImporter.Stage.IMPORTING -> context.getString(R.string.progress_importing)
            stage == FsaImporter.Stage.LOCATING -> context.getString(R.string.progress_locating)
            else -> context.getString(R.string.progress_checking)
        }
        return NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.progress_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(if (total > 0) 1000 else 0, if (total > 0) (done * 1000 / total).toInt() else 0, total <= 0)
            .build()
    }

    fun ratingChanges(context: Context, changes: List<FavouriteChange>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(context)
        for (c in changes) {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ESTABLISHMENT, c.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(context, c.id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val body = when {
                c.new == null -> context.getString(R.string.change_removed)
                c.new == c.old -> context.getString(R.string.change_reinspected, c.newDate?.pretty() ?: "", c.new.label(context))
                else -> context.getString(R.string.change_body, c.old.label(context), c.new.label(context))
            }
            val n = NotificationCompat.Builder(context, CH_CHANGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.change_title, c.name))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            // Tag by FHRS id so two favourites never replace each other's alert.
            runCatching { nm.notify(c.id.toString(), CHANGES_ID, n) }
        }
    }
}
