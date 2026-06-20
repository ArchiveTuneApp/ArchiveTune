/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.svg

import android.graphics.Picture
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import com.caverock.androidsvg.SVG
import moe.rukamori.archivetune.ui.theme.palette.TonalPalettes

@Composable
fun DynamicSVGImage(
    svgImageString: String,
    tonalPalettes: TonalPalettes,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    val picture: Picture? =
        remember(isDarkTheme, tonalPalettes, size) {
            if (size.width == 0 || size.height == 0) {
                null
            } else {
                SVG
                    .getFromString(
                        svgImageString.parseDynamicColor(tonalPalettes, isDarkTheme),
                    ).renderToPicture(size.width, size.height)
            }
        }

    Canvas(
        modifier =
            modifier.onGloballyPositioned { coordinates ->
                val newSize = coordinates.size
                if (newSize != size) size = newSize
            },
    ) {
        picture?.let { pic ->
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPicture(pic)
            }
        }
    }
}
