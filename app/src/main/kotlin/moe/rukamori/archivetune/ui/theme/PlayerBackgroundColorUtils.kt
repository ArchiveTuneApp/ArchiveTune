/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

object PlayerBackgroundColorUtils {
    private const val DEFAULT_MIN_BRIGHTNESS = 0.15f
    private const val DEFAULT_MAX_BRIGHTNESS = 0.58f
    private const val DEFAULT_MIN_SATURATION = 0.32f

    fun ensureComfortableColor(
        color: Color,
        minBrightness: Float = DEFAULT_MIN_BRIGHTNESS,
        maxBrightness: Float = DEFAULT_MAX_BRIGHTNESS,
        minSaturation: Float = DEFAULT_MIN_SATURATION,
    ): Color {
        val hsv = color.toHsv()
        hsv[1] = hsv[1].coerceAtLeast(minSaturation)
        hsv[2] = hsv[2].coerceIn(minBrightness, maxBrightness)
        return hsv.toColor()
    }

    fun darkenColor(
        color: Color,
        factor: Float,
    ): Color {
        val hsv = color.toHsv()
        hsv[2] = (hsv[2] * factor).coerceAtLeast(0f)
        return hsv.toColor()
    }

    fun buildColoringStops(baseColor: Color): Array<Pair<Float, Color>> {
        val comfortable = ensureComfortableColor(baseColor, minBrightness = 0.18f, maxBrightness = 0.5f)
        val mid = darkenColor(comfortable, 0.82f)
        val deep = darkenColor(comfortable, 0.6f)
        return arrayOf(
            0f to comfortable.copy(alpha = 0.97f),
            0.4f to mid.copy(alpha = 0.94f),
            0.75f to deep.copy(alpha = 0.92f),
            1f to Color.Black.copy(alpha = 0.88f),
        )
    }

    fun buildBlurOverlayStops(colors: List<Color>): Array<Pair<Float, Color>> {
        if (colors.isEmpty()) {
            return defaultBlurOverlayStops()
        }
        val comfortable = colors.map(::ensureComfortableColor)
        val first = comfortable[0]
        val second = comfortable.getOrNull(1) ?: first
        val third = comfortable.getOrNull(2) ?: second
        return arrayOf(
            0f to first.copy(alpha = 0.45f),
            0.4f to lerp(first, second, 0.5f).copy(alpha = 0.38f),
            0.75f to lerp(second, third, 0.55f).copy(alpha = 0.35f),
            1f to third.copy(alpha = 0.50f),
        )
    }

    fun buildBlurGradientStops(colors: List<Color>): Array<Pair<Float, Color>> {
        if (colors.isEmpty()) {
            return arrayOf(
                0f to Color.Transparent,
                1f to Color.Transparent,
            )
        }
        val comfortable = colors.map(::ensureComfortableColor)
        val first = comfortable[0]
        val second = comfortable.getOrNull(1) ?: first
        val third = comfortable.getOrNull(2) ?: second
        return arrayOf(
            0f to first.copy(alpha = 0.55f),
            0.2f to lerp(first, second, 0.3f).copy(alpha = 0.48f),
            0.5f to second.copy(alpha = 0.42f),
            0.8f to lerp(second, third, 0.6f).copy(alpha = 0.38f),
            1f to third.copy(alpha = 0.35f),
        )
    }

    private fun defaultBlurOverlayStops(): Array<Pair<Float, Color>> =
        arrayOf(
            0f to Color.Black.copy(alpha = 0.35f),
            1f to Color.Black.copy(alpha = 0.45f),
        )

    private fun Color.toHsv(): FloatArray {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(this.toArgb(), hsv)
        return hsv
    }

    private fun FloatArray.toColor(): Color = Color(AndroidColor.HSVToColor(this))
}
