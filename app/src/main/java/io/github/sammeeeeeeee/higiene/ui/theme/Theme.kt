package io.github.sammeeeeeeee.higiene.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.sammeeeeeeee.higiene.R

/**
 * FSA greens (from ratings.food.gov.uk CSS) arranged the way the Wise system uses its colours:
 * a deep ink green for weight, one bright green as punctuation, pale wash for soft surfaces.
 */
object Fsa {
    val Ink = Color(0xFF002A2A) // --fsa__dark-green-hover: dominant dark, like Wise "Forest Ink"
    val Teal = Color(0xFF014B4C) // --fsa__link-green
    val Green = Color(0xFF007C75) // --fsa-primary_dark-green: links, primary
    val GreenPressed = Color(0xFF005E59)
    val Lime = Color(0xFF6CB33F) // --fsa-primary_mid-green: the "voltage" accent
    val LightGreen = Color(0xFFA9C47F) // --fsa-primary_light-green
    val Mist = Color(0xFFE9F3E3) // pale wash of the mid green
    val Fog = Color(0xFFF0F3F5) // --fsa_online-grey
    val Obsidian = Color(0xFF0C1716)
    val Charcoal = Color(0xFF3A3D40)
    val Slate = Color(0xFF53565A) // --fsa-primary_dark-grey
    val Pebble = Color(0xFF868A8E)
    val Alarm = Color(0xFFB3261E)
    val Paper = Color(0xFFFFFFFF)
}

private val Inter = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.inter_variable, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

/** Heavy, tightly tracked display type (Wise Sans stand-in: Inter Black, -0.04em). */
@Immutable
data class DisplayStyles(val hero: TextStyle, val title: TextStyle, val section: TextStyle)

val LocalDisplay = staticCompositionLocalOf {
    DisplayStyles(TextStyle.Default, TextStyle.Default, TextStyle.Default)
}

private val display = DisplayStyles(
    hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 52.sp, lineHeight = 46.sp, letterSpacing = (-0.04).em),
    title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 32.sp, letterSpacing = (-0.035).em),
    section = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 24.sp, letterSpacing = (-0.02).em),
)

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.011).em),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.009).em),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.008).em),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.006).em),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = (-0.005).em),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = (-0.006).em),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = (-0.005).em),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = (-0.003).em),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = (-0.006).em),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = (-0.005).em),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = (-0.003).em),
)

private val light = lightColorScheme(
    primary = Fsa.Green,
    onPrimary = Fsa.Paper,
    primaryContainer = Fsa.Mist,
    onPrimaryContainer = Fsa.Ink,
    secondary = Fsa.Lime,
    onSecondary = Fsa.Ink,
    secondaryContainer = Fsa.Lime,
    onSecondaryContainer = Fsa.Ink,
    tertiary = Fsa.Teal,
    onTertiary = Fsa.Paper,
    background = Fsa.Paper,
    onBackground = Fsa.Obsidian,
    surface = Fsa.Paper,
    onSurface = Fsa.Obsidian,
    surfaceVariant = Fsa.Fog,
    onSurfaceVariant = Fsa.Slate,
    surfaceContainerLowest = Fsa.Paper,
    surfaceContainerLow = Fsa.Paper,
    surfaceContainer = Fsa.Fog,
    surfaceContainerHigh = Fsa.Fog,
    surfaceContainerHighest = Color(0xFFE4E9EC),
    inverseSurface = Fsa.Ink,
    inverseOnSurface = Fsa.Paper,
    inversePrimary = Fsa.Lime,
    outline = Fsa.Pebble,
    outlineVariant = Color(0xFFD5DBDF),
    error = Fsa.Alarm,
)

private val dark = darkColorScheme(
    primary = Fsa.Lime,
    onPrimary = Fsa.Ink,
    primaryContainer = Color(0xFF0E4040),
    onPrimaryContainer = Color(0xFFDDEFD3),
    secondary = Fsa.Lime,
    onSecondary = Fsa.Ink,
    secondaryContainer = Fsa.Lime,
    onSecondaryContainer = Fsa.Ink,
    tertiary = Fsa.LightGreen,
    onTertiary = Fsa.Ink,
    background = Color(0xFF001A1A),
    onBackground = Color(0xFFE6EEEC),
    surface = Color(0xFF001A1A),
    onSurface = Color(0xFFE6EEEC),
    surfaceVariant = Color(0xFF0B3333),
    onSurfaceVariant = Color(0xFFB7C6C3),
    surfaceContainerLowest = Color(0xFF001414),
    surfaceContainerLow = Color(0xFF002020),
    surfaceContainer = Fsa.Ink,
    surfaceContainerHigh = Color(0xFF083434),
    surfaceContainerHighest = Color(0xFF0E3D3D),
    inverseSurface = Color(0xFFE6EEEC),
    inverseOnSurface = Fsa.Ink,
    inversePrimary = Fsa.Green,
    outline = Color(0xFF7D918E),
    outlineVariant = Color(0xFF214A48),
    error = Color(0xFFFFB4AB),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Colours for the always-dark hero bands (Wise's inverted sections). */
@Immutable
data class HeroColors(val background: Color, val headline: Color, val body: Color, val accent: Color)

val LocalHero = staticCompositionLocalOf { HeroColors(Fsa.Ink, Fsa.Lime, Fsa.Paper, Fsa.Lime) }

/** True in dark theme (follows the app theme, not just the system setting). */
val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun HigieneTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val hero = if (darkTheme) HeroColors(Color(0xFF002424), Fsa.Lime, Color(0xFFE6EEEC), Fsa.Lime) else HeroColors(Fsa.Ink, Fsa.Lime, Fsa.Paper, Fsa.Lime)
    androidx.compose.runtime.CompositionLocalProvider(LocalDisplay provides display, LocalHero provides hero, LocalIsDark provides darkTheme) {
        MaterialTheme(colorScheme = if (darkTheme) dark else light, typography = typography, shapes = shapes, content = content)
    }
}
