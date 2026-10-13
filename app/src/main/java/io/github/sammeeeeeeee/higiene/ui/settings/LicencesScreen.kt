package io.github.sammeeeeeeee.higiene.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sammeeeeeeee.higiene.HigieneApp
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.Db
import io.github.sammeeeeeeee.higiene.ui.components.HeroHeader
import io.github.sammeeeeeeee.higiene.ui.components.InfoCard
import io.github.sammeeeeeeee.higiene.ui.components.SectionTitle
import io.github.sammeeeeeeee.higiene.ui.theme.LocalDisplay
import io.github.sammeeeeeeee.higiene.ui.theme.LocalHero

@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val hero = LocalHero.current
    val osYear = remember {
        (context.applicationContext as HigieneApp).graph.db.meta(Db.META_CODEPOINT_YEAR) ?: "2026"
    }
    val nsi = remember { runCatching { context.assets.open("licences/nsi-bsd3.txt").bufferedReader().use { it.readText() } }.getOrDefault("") }
    val inter = remember { runCatching { context.assets.open("licences/inter-ofl.txt").bufferedReader().use { it.readText() } }.getOrDefault("") }
    Column(Modifier.fillMaxSize()) {
        HeroHeader(stringResource(R.string.licences_title), onBack = onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.lic_fsa_title))
            InfoCard { Text(stringResource(R.string.lic_fsa_body), style = MaterialTheme.typography.bodyMedium) }
            SectionTitle(stringResource(R.string.lic_os_title))
            InfoCard { Text(stringResource(R.string.lic_os_body, osYear), style = MaterialTheme.typography.bodyMedium) }
            SectionTitle(stringResource(R.string.lic_osm_title))
            InfoCard { Text(stringResource(R.string.lic_osm_body), style = MaterialTheme.typography.bodyMedium) }
            SectionTitle(stringResource(R.string.lic_google_title))
            InfoCard { Text(stringResource(R.string.lic_google_body), style = MaterialTheme.typography.bodyMedium) }
            SectionTitle(stringResource(R.string.lic_mapillary_title))
            InfoCard { Text(stringResource(R.string.lic_mapillary_body), style = MaterialTheme.typography.bodyMedium) }
            SectionTitle(stringResource(R.string.lic_brands_title))
            InfoCard {
                Text(stringResource(R.string.lic_brands_body), style = MaterialTheme.typography.bodyMedium)
                Text(nsi, style = MaterialTheme.typography.bodySmall)
            }
            SectionTitle(stringResource(R.string.lic_libs_title))
            InfoCard { Text(stringResource(R.string.lic_libs_body), style = MaterialTheme.typography.bodyMedium) }
            SectionTitle(stringResource(R.string.lic_font_title))
            InfoCard { Text(inter, style = MaterialTheme.typography.bodySmall) }
            SectionTitle(stringResource(R.string.lic_app_title))
            InfoCard { Text(stringResource(R.string.lic_app_body), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
