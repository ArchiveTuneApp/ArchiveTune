/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Player slider color configuration for consistent styling across all slider types
 *
 * This object provides standardized color schemes for Default, Squiggly, and Slim sliders
 * used in the music player interface, ensuring visual consistency and proper contrast.
 */
object PlayerSliderColors {
    /**
     * Standard slider colors for all slider types
     *
     * @param activeColor Color for active track, ticks, and thumb
     * @param inactiveAlpha Alpha transparency for inactive track (default: 0.15f for subtle appearance)
     * @return SliderColors configuration
     */
    @Composable
    fun getSliderColors(
        activeColor: Color,
        inactiveAlpha: Float = 0.25f,
    ): SliderColors =
        SliderDefaults.colors(
            activeTrackColor = activeColor,
            activeTickColor = activeColor,
            thumbColor = activeColor,
            inactiveTrackColor = activeColor.copy(alpha = inactiveAlpha),
        )

    /**
     * Default slider colors using button color scheme
     *
     * @param buttonColor The active button color from player theme
     * @return SliderColors configuration for default slider
     */
    @Composable
    fun standardSliderColors(buttonColor: Color): SliderColors =
        getSliderColors(
            activeColor = buttonColor,
            inactiveAlpha = Config.INACTIVE_TRACK_ALPHA,
        )

    /**
     * Squiggly slider colors using button color scheme
     *
     * @param buttonColor The active button color from player theme
     * @return SliderColors configuration for squiggly slider
     */
    @Composable
    fun wavySliderColors(buttonColor: Color): SliderColors =
        SliderDefaults.colors(
            activeTrackColor = buttonColor,
            activeTickColor = buttonColor,
            thumbColor = Color.Transparent,
            inactiveTrackColor = buttonColor.copy(alpha = Config.INACTIVE_TRACK_ALPHA),
            inactiveTickColor = buttonColor.copy(alpha = Config.INACTIVE_TICK_ALPHA),
        )

    @Composable
    fun thickSliderColors(buttonColor: Color): SliderColors =
        getSliderColors(
            activeColor = buttonColor,
            inactiveAlpha = Config.THICK_INACTIVE_TRACK_ALPHA,
        )

    @Composable
    fun circularSliderColors(buttonColor: Color): SliderColors =
        SliderDefaults.colors(
            activeTrackColor = buttonColor,
            activeTickColor = buttonColor,
            thumbColor = buttonColor,
            inactiveTrackColor = buttonColor.copy(alpha = Config.INACTIVE_TRACK_ALPHA),
        )

    @Composable
    fun simpleSliderColors(buttonColor: Color): SliderColors =
        SliderDefaults.colors(
            activeTrackColor = buttonColor.copy(alpha = Config.SIMPLE_ACTIVE_TRACK_ALPHA),
            activeTickColor = buttonColor.copy(alpha = Config.SIMPLE_ACTIVE_TRACK_ALPHA),
            thumbColor = Color.Transparent,
            inactiveTrackColor = buttonColor.copy(alpha = Config.SIMPLE_INACTIVE_TRACK_ALPHA),
            inactiveTickColor = buttonColor.copy(alpha = Config.SIMPLE_INACTIVE_TRACK_ALPHA),
        )

    /**
     * Configuration constants for slider colors
     */
    object Config {
        /** Alpha transparency for inactive track - subtle appearance */
        const val INACTIVE_TRACK_ALPHA = 0.22f

        const val THICK_INACTIVE_TRACK_ALPHA = 0.28f

        const val SIMPLE_ACTIVE_TRACK_ALPHA = 0.85f

        const val SIMPLE_INACTIVE_TRACK_ALPHA = 0.15f

        /** Alpha transparency for inactive ticks */
        const val INACTIVE_TICK_ALPHA = 0.25f

        /** Default active color when no theme color is available */
        val DEFAULT_ACTIVE_COLOR = Color(0xFF1976D2)

        /** Default inactive color when no theme color is available */
        val DEFAULT_INACTIVE_COLOR = Color.White.copy(alpha = INACTIVE_TRACK_ALPHA)
    }
}
