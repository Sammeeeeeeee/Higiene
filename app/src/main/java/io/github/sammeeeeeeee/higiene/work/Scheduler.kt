package io.github.sammeeeeeeee.higiene.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.sammeeeeeeee.higiene.data.RefreshInterval
import java.util.concurrent.TimeUnit

object Scheduler {
    const val PERIODIC = "refresh-periodic"
    const val NOW = "refresh-now"

    fun schedule(context: Context, interval: RefreshInterval, wifiOnly: Boolean) {
        val wm = WorkManager.getInstance(context)
        val days = interval.days
        if (days == null) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(days, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Refresh started by the user. [anyNetwork] lets them use mobile data once. */
    fun refreshNow(context: Context, wifiOnly: Boolean, anyNetwork: Boolean = false, force: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly && !anyNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(RefreshWorker.KEY_USER to true, RefreshWorker.KEY_FORCE to force))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }
}
