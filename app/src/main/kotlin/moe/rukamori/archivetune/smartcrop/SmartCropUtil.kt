package moe.rukamori.archivetune.smartcrop

import android.graphics.Bitmap
import android.graphics.Rect

enum class CropThumbnailStyle(val key: String) {
    OFF("off"),
    CENTER_CROP("center_crop"),
    SMART_CROP("smart_crop"),
    SMART_CROP_NO_ZOOM("smart_crop_no_zoom"),
}

fun Bitmap.toImgData(): ImgData {
    val w = width
    val h = height
    val pixels = IntArray(w * h)
    getPixels(pixels, 0, w, 0, 0, w, h)
    val data = IntArray(w * h * 4)
    for (i in pixels.indices) {
        val p = pixels[i]
        data[i * 4] = (p shr 16) and 0xFF
        data[i * 4 + 1] = (p shr 8) and 0xFF
        data[i * 4 + 2] = p and 0xFF
        data[i * 4 + 3] = (p shr 24) and 0xFF
    }
    return ImgData(w, h, data)
}

fun Bitmap.smartCropRect(allowZoom: Boolean): Rect {
    val input = toImgData()
    val side = minOf(width, height)
    val options = Options(
        width = side,
        height = side,
        minScale = if (allowZoom) 0.5 else 1.0,
        maxScale = 1.0,
    )
    val result = SmartCrop.crop(input, options)
    return Rect(
        result.topCrop.x,
        result.topCrop.y,
        result.topCrop.x + result.topCrop.width,
        result.topCrop.y + result.topCrop.height,
    )
}
