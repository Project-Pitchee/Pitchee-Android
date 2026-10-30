package io.rovly.pitchee.ui.theme

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.rovly.pitchee.R

internal enum class PitcheeThemePalette(
    val storageId: String,
    val labelRes: Int,
    val lightPrimary: Color,
    val darkPrimary: Color,
    val lightSecondary: Color,
    val darkSecondary: Color,
    val lightTertiary: Color,
    val darkTertiary: Color,
    val lightBackground: Color,
    val darkBackground: Color,
) {
    CORAL(
        storageId = "coral",
        labelRes = R.string.theme_coral,
        lightPrimary = Color(0xFFD96A3E),
        darkPrimary = Color(0xFFFFB59A),
        lightSecondary = Color(0xFF3F6C68),
        darkSecondary = Color(0xFFA5CCC7),
        lightTertiary = Color(0xFF745B18),
        darkTertiary = Color(0xFFE3C56D),
        lightBackground = Color(0xFFFFF8F5),
        darkBackground = Color(0xFF1B120F),
    ),
    ROSE(
        storageId = "rose",
        labelRes = R.string.theme_rose,
        lightPrimary = Color(0xFFA63D64),
        darkPrimary = Color(0xFFFFABC7),
        lightSecondary = Color(0xFF7B5365),
        darkSecondary = Color(0xFFD9B8C4),
        lightTertiary = Color(0xFF7A5A16),
        darkTertiary = Color(0xFFE6C36C),
        lightBackground = Color(0xFFFFF7F9),
        darkBackground = Color(0xFF1E1116),
    ),
    OCEAN(
        storageId = "ocean",
        labelRes = R.string.theme_ocean,
        lightPrimary = Color(0xFF006A6A),
        darkPrimary = Color(0xFF72D8D2),
        lightSecondary = Color(0xFF3E6374),
        darkSecondary = Color(0xFFA3CBDD),
        lightTertiary = Color(0xFF5D5D00),
        darkTertiary = Color(0xFFD8C94A),
        lightBackground = Color(0xFFF5FAFC),
        darkBackground = Color(0xFF0E171A),
    ),
    FOREST(
        storageId = "forest",
        labelRes = R.string.theme_forest,
        lightPrimary = Color(0xFF3D6B35),
        darkPrimary = Color(0xFFA5D39B),
        lightSecondary = Color(0xFF4B6355),
        darkSecondary = Color(0xFFA7CBB8),
        lightTertiary = Color(0xFF735C0F),
        darkTertiary = Color(0xFFE2C66D),
        lightBackground = Color(0xFFF7FAF5),
        darkBackground = Color(0xFF111A12),
    ),
    INDIGO(
        storageId = "indigo",
        labelRes = R.string.theme_indigo,
        lightPrimary = Color(0xFF4A5AAE),
        darkPrimary = Color(0xFFB8C3FF),
        lightSecondary = Color(0xFF675A81),
        darkSecondary = Color(0xFFD3BCE8),
        lightTertiary = Color(0xFF006A6A),
        darkTertiary = Color(0xFF76D5D1),
        lightBackground = Color(0xFFF8F8FF),
        darkBackground = Color(0xFF13131A),
    ),
    AMBER(
        storageId = "amber",
        labelRes = R.string.theme_amber,
        lightPrimary = Color(0xFF805500),
        darkPrimary = Color(0xFFF7BD5D),
        lightSecondary = Color(0xFF735A2E),
        darkSecondary = Color(0xFFDBC48E),
        lightTertiary = Color(0xFF4E6162),
        darkTertiary = Color(0xFFA6CBCB),
        lightBackground = Color(0xFFFDF9F4),
        darkBackground = Color(0xFF1D1610),
    ),
}

internal class ThemePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun selected(): PitcheeThemePalette {
        val id = preferences.getString(KEY_PALETTE, null)
        return PitcheeThemePalette.entries.firstOrNull { it.storageId == id }
            ?: PitcheeThemePalette.CORAL
    }

    fun set(palette: PitcheeThemePalette) {
        preferences.edit().putString(KEY_PALETTE, palette.storageId).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "theme_preferences"
        const val KEY_PALETTE = "palette"
    }
}

@Stable
internal class PitcheeThemeController(
    initial: PitcheeThemePalette,
    private val preferences: ThemePreferences,
) {
    var palette by mutableStateOf(initial)
        private set

    fun select(palette: PitcheeThemePalette) {
        if (this.palette == palette) return
        this.palette = palette
        preferences.set(palette)
    }
}

internal val LocalPitcheeThemeController = staticCompositionLocalOf<PitcheeThemeController> {
    error("PitcheeThemeController is not provided")
}
