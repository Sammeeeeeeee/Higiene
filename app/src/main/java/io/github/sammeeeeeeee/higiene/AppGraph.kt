package io.github.sammeeeeeeee.higiene

import android.content.Context
import io.github.sammeeeeeeee.higiene.data.Authorities
import io.github.sammeeeeeeee.higiene.data.Brands
import io.github.sammeeeeeeee.higiene.data.CodePointImporter
import io.github.sammeeeeeeee.higiene.data.Db
import io.github.sammeeeeeeee.higiene.data.FsaImporter
import io.github.sammeeeeeeee.higiene.data.OsmClient
import io.github.sammeeeeeeee.higiene.data.PostcodeIndex
import io.github.sammeeeeeeee.higiene.data.Repository
import io.github.sammeeeeeeee.higiene.data.Settings
import io.github.sammeeeeeeee.higiene.data.UserDb
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Hand-wired app dependencies. */
class AppGraph(context: Context) {
    private val app = context.applicationContext

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    val db = Db(app)
    val userDb = UserDb(app)
    private val postcodeFile = File(app.filesDir, "postcodes.bin")

    @Volatile
    private var index: PostcodeIndex? = null

    @Volatile
    private var indexStamp = -1L

    /** Opens (or re-opens after an update) the offline postcode index; null until first downloaded. */
    fun postcodeIndex(): PostcodeIndex? {
        val stamp = if (postcodeFile.exists()) postcodeFile.lastModified() xor postcodeFile.length() else 0L
        if (stamp != indexStamp) {
            synchronized(this) {
                if (stamp != indexStamp) {
                    index = if (stamp == 0L) null else PostcodeIndex.open(postcodeFile)
                    indexStamp = stamp
                }
            }
        }
        return index
    }

    val settings = Settings(app)
    val repo = Repository(db, userDb, ::postcodeIndex)
    val fsa = FsaImporter(db, http, ::postcodeIndex, File(app.cacheDir, "fsa"), favouriteIds = userDb::favouriteIds)
    val codePoint = CodePointImporter(db, http, postcodeFile)
    val authorities = Authorities(app, db, http)
    val osm = OsmClient(http, repo)
    val brands = Brands(app, http)

    companion object {
        const val USER_AGENT = "Higiene/${BuildConfig.VERSION_NAME} (+https://github.com/Sammeeeeeeee/Higiene) okhttp"
    }
}
