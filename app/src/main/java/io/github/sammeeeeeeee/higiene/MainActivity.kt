package io.github.sammeeeeeeee.higiene

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.sammeeeeeeee.higiene.ui.AppNav
import io.github.sammeeeeeeee.higiene.ui.theme.HigieneTheme
import io.github.sammeeeeeeee.higiene.work.Notifications

class MainActivity : ComponentActivity() {
    private var pending by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hero bands are always dark, so status bar icons are always light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pending = establishmentFrom(intent)
        setContent {
            HigieneTheme {
                AppNav(openEstablishment = pending, onOpenedEstablishment = { pending = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pending = establishmentFrom(intent)
    }

    /** The business a rating-change notification points at, once (not again when relaunched from Recents). */
    private fun establishmentFrom(intent: Intent?): Long? {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        val id = intent.getLongExtra(Notifications.EXTRA_ESTABLISHMENT, -1L).takeIf { it > 0 } ?: return null
        intent.removeExtra(Notifications.EXTRA_ESTABLISHMENT)
        setIntent(intent)
        return id
    }
}
