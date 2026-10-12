package io.github.sammeeeeeeee.higiene

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.github.sammeeeeeeee.higiene.work.Notifications
import io.github.sammeeeeeeee.higiene.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HigieneApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Notifications.createChannels(this)
        scope.launch {
            runCatching { graph.authorities.ensureLoaded() }
            // Keep the background refresh in line with settings once data exists.
            if (graph.repo.hasData()) {
                val s = graph.settings.current()
                Scheduler.schedule(this@HigieneApp, s.refresh, s.wifiOnly)
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { graph.http }))
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
