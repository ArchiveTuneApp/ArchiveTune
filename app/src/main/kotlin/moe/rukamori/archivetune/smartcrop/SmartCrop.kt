package moe.rukamori.archivetune.smartcrop

import kotlin.math.*

data class ImgData(
    val width: Int,
    val height: Int,
    val data: IntArray = IntArray(width * height * 4)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImgData) return false
        return width == other.width && height == other.height && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class Score(
    val detail: Double = 0.0,
    val saturation: Double = 0.0,
    val skin: Double = 0.0,
    val boost: Double = 0.0,
    val total: Double = 0.0
)

data class Crop(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val score: Score? = null
)

data class CropBoost(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val weight: Double
)

data class CropResult(
    val topCrop: Crop,
    val crops: List<Crop>? = null,
    val debugOutput: ImgData? = null,
    val debugOptions: Options? = null,
    val debugTopCrop: Crop? = null
)

data class Options(
    val width: Int = 0,
    val height: Int = 0,
    val aspect: Double = 0.0,
    val cropWidth: Int = 0,
    val cropHeight: Int = 0,
    val detailWeight: Double = 0.2,
    val skinColor: List<Double> = listOf(0.78, 0.57, 0.44),
    val skinBias: Double = 0.01,
    val skinBrightnessMin: Double = 0.2,
    val skinBrightnessMax: Double = 1.0,
    val skinThreshold: Double = 0.8,
    val skinWeight: Double = 1.8,
    val saturationBrightnessMin: Double = 0.05,
    val saturationBrightnessMax: Double = 0.9,
    val saturationThreshold: Double = 0.4,
    val saturationBias: Double = 0.2,
    val saturationWeight: Double = 0.1,
    val scoreDownSample: Int = 8,
    val step: Int = 8,
    val scaleStep: Double = 0.1,
    val minScale: Double = 1.0,
    val maxScale: Double = 1.0,
    val edgeRadius: Double = 0.4,
    val edgeWeight: Double = -20.0,
    val outsideImportance: Double = -0.5,
    val boostWeight: Double = 100.0,
    val ruleOfThirds: Boolean = true,
    val boost: List<CropBoost>? = null,
    val debug: Boolean = false
)

object SmartCrop {

    fun crop(input: ImgData, options: Options = Options()): CropResult {
        var opts = options

        if (opts.aspect > 0.0) {
            opts = opts.copy(width = opts.aspect.toInt(), height = 1, aspect = 0.0)
        }

        var image = input
        var prescale = 1.0

        if (opts.width > 0 && opts.height > 0) {
            val scale = (image.width.toDouble() / opts.width)
                .coerceAtMost(image.height.toDouble() / opts.height)
            val cropWidth = (opts.width * scale).toInt()
            val cropHeight = (opts.height * scale).toInt()
            val minScale = opts.maxScale.coerceAtMost((1.0 / scale).coerceAtLeast(opts.minScale))
            opts = opts.copy(
                cropWidth = cropWidth,
                cropHeight = cropHeight,
                minScale = minScale
            )

            prescale = (256.0 / image.width)
                .coerceAtLeast(256.0 / image.height)
                .coerceAtMost(1.0)
            if (prescale < 1.0) {
                image = prescaleImage(image, prescale)
                opts = opts.copy(
                    cropWidth = (cropWidth * prescale).toInt(),
                    cropHeight = (cropHeight * prescale).toInt()
                )
                opts.boost?.let { boosts ->
                    opts = opts.copy(
                        boost = boosts.map { b ->
                            b.copy(
                                x = (b.x * prescale).toInt(),
                                y = (b.y * prescale).toInt(),
                                width = (b.width * prescale).toInt(),
                                height = (b.height * prescale).toInt()
                            )
                        }
                    )
                }
            }
        }

        val analysis = analyse(opts, image)

        val topCrop = Crop(
            x = (analysis.topCrop.x / prescale).toInt(),
            y = (analysis.topCrop.y / prescale).toInt(),
            width = (analysis.topCrop.width / prescale).toInt(),
            height = (analysis.topCrop.height / prescale).toInt(),
            score = analysis.topCrop.score
        )

        val crops = if (opts.debug) {
            analysis.crops?.map { c ->
                Crop(
                    x = (c.x / prescale).toInt(),
                    y = (c.y / prescale).toInt(),
                    width = (c.width / prescale).toInt(),
                    height = (c.height / prescale).toInt(),
                    score = c.score
                )
            }
        } else null

        val debugTopCrop = if (opts.debug) {
            analysis.debugTopCrop?.let { dc ->
                Crop(
                    x = (dc.x / prescale).toInt(),
                    y = (dc.y / prescale).toInt(),
                    width = (dc.width / prescale).toInt(),
                    height = (dc.height / prescale).toInt()
                )
            }
        } else null

        return CropResult(
            topCrop = topCrop,
            crops = crops,
            debugOutput = if (opts.debug) analysis.debugOutput else null,
            debugOptions = if (opts.debug) analysis.debugOptions else null,
            debugTopCrop = debugTopCrop
        )
    }
}

private data class InternalCrop(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val score: Score? = null
)

private data class AnalyseResult(
    val topCrop: InternalCrop,
    val crops: List<InternalCrop>?,
    val debugOutput: ImgData?,
    val debugOptions: Options?,
    val debugTopCrop: InternalCrop?
)

private fun analyse(options: Options, input: ImgData): AnalyseResult {
    val output = ImgData(input.width, input.height)

    edgeDetect(input, output)
    skinDetect(options, input, output)
    saturationDetect(options, input, output)
    applyBoosts(options, output)

    val scoreOutput = downSample(output, options.scoreDownSample)
    val crops = generateCrops(options, input.width, input.height)

    var topScore = Double.NEGATIVE_INFINITY
    var topCrop: InternalCrop? = null
    val scoredCrops = mutableListOf<InternalCrop>()

    for (crop in crops) {
        val s = score(options, scoreOutput, crop)
        val scored = crop.copy(score = s)
        scoredCrops.add(scored)
        if (s.total > topScore) {
            topScore = s.total
            topCrop = scored
        }
    }

    val debugCrops = if (options.debug) scoredCrops else null

    return AnalyseResult(
        topCrop = topCrop ?: InternalCrop(0.0, 0.0, 0.0, 0.0),
        crops = debugCrops,
        debugOutput = if (options.debug) output else null,
        debugOptions = if (options.debug) options else null,
        debugTopCrop = if (options.debug) topCrop else null
    )
}

private fun edgeDetect(input: ImgData, output: ImgData) {
    val id = input.data
    val od = output.data
    val w = input.width
    val h = input.height

    for (y in 0 until h) {
        for (x in 0 until w) {
            val p = (y * w + x) * 4
            val lightness = if (x == 0 || x >= w - 1 || y == 0 || y >= h - 1) {
                cie(id[p], id[p + 1], id[p + 2])
            } else {
                cie(id[p], id[p + 1], id[p + 2]) * 4.0 -
                    cie(id[p - w * 4], id[p - w * 4 + 1], id[p - w * 4 + 2]) -
                    cie(id[p - 4], id[p - 4 + 1], id[p - 4 + 2]) -
                    cie(id[p + 4], id[p + 4 + 1], id[p + 4 + 2]) -
                    cie(id[p + w * 4], id[p + w * 4 + 1], id[p + w * 4 + 2])
            }
            od[p + 1] = lightness.toInt().coerceIn(0, 255)
        }
    }
}

private fun skinDetect(options: Options, input: ImgData, output: ImgData) {
    val id = input.data
    val od = output.data
    val w = input.width
    val h = input.height

    for (y in 0 until h) {
        for (x in 0 until w) {
            val p = (y * w + x) * 4
            val lightness = cie(id[p], id[p + 1], id[p + 2]) / 255.0
            val skin = skinColor(options, id[p], id[p + 1], id[p + 2])
            val isSkinColor = skin > options.skinThreshold
            val isSkinBrightness = lightness >= options.skinBrightnessMin &&
                lightness <= options.skinBrightnessMax
            if (isSkinColor && isSkinBrightness) {
                od[p] = ((skin - options.skinThreshold) *
                    (255.0 / (1.0 - options.skinThreshold))).toInt().coerceIn(0, 255)
            } else {
                od[p] = 0
            }
        }
    }
}

private fun saturationDetect(options: Options, input: ImgData, output: ImgData) {
    val id = input.data
    val od = output.data
    val w = input.width
    val h = input.height

    for (y in 0 until h) {
        for (x in 0 until w) {
            val p = (y * w + x) * 4
            val lightness = cie(id[p], id[p + 1], id[p + 2]) / 255.0
            val sat = saturation(id[p], id[p + 1], id[p + 2])

            val acceptableSaturation = sat > options.saturationThreshold
            val acceptableLightness = lightness >= options.saturationBrightnessMin &&
                lightness <= options.saturationBrightnessMax
            if (acceptableLightness && acceptableSaturation) {
                od[p + 2] = ((sat - options.saturationThreshold) *
                    (255.0 / (1.0 - options.saturationThreshold))).toInt().coerceIn(0, 255)
            } else {
                od[p + 2] = 0
            }
        }
    }
}

private fun applyBoosts(options: Options, output: ImgData) {
    val boost = options.boost ?: return
    val od = output.data
    for (i in 0 until output.width step 4) {
        od[i + 3] = 0
    }
    for (b in boost) {
        applyBoost(b, output)
    }
}

private fun applyBoost(boost: CropBoost, output: ImgData) {
    val od = output.data
    val w = output.width
    val x0 = boost.x.coerceIn(0, output.width - 1)
    val x1 = (boost.x + boost.width).coerceIn(0, output.width)
    val y0 = boost.y.coerceIn(0, output.height - 1)
    val y1 = (boost.y + boost.height).coerceIn(0, output.height)
    val weight = (boost.weight * 255.0).toInt()
    for (y in y0 until y1) {
        for (x in x0 until x1) {
            val i = (y * w + x) * 4
            od[i + 3] = (od[i + 3] + weight).coerceIn(0, 255)
        }
    }
}

private fun generateCrops(options: Options, width: Int, height: Int): List<InternalCrop> {
    val results = mutableListOf<InternalCrop>()
    val minDimension = width.coerceAtMost(height)
    val cropWidth = if (options.cropWidth > 0) options.cropWidth.toDouble() else minDimension.toDouble()
    val cropHeight = if (options.cropHeight > 0) options.cropHeight.toDouble() else minDimension.toDouble()

    var scale = options.maxScale
    while (scale >= options.minScale) {
        var y = 0
        while (y + cropHeight * scale <= height) {
            var x = 0
            while (x + cropWidth * scale <= width) {
                results.add(
                    InternalCrop(
                        x = x.toDouble(),
                        y = y.toDouble(),
                        width = cropWidth * scale,
                        height = cropHeight * scale
                    )
                )
                x += options.step
            }
            y += options.step
        }
        scale -= options.scaleStep
    }
    return results
}

private fun score(options: Options, output: ImgData, crop: InternalCrop): Score {
    val od = output.data
    val downSample = options.scoreDownSample
    val outputWidth = output.width

    var detailSum = 0.0
    var skinSum = 0.0
    var saturationSum = 0.0
    var boostSum = 0.0

    for (sy in 0 until output.height) {
        val y = sy * downSample
        for (sx in 0 until output.width) {
            val p = (sy * outputWidth + sx) * 4
            val x = sx * downSample
            val i = importance(options, crop, x, y)
            val detail = od[p + 1].toDouble() / 255.0

            skinSum += (od[p].toDouble() / 255.0) * (detail + options.skinBias) * i
            detailSum += detail * i
            saturationSum += (od[p + 2].toDouble() / 255.0) * (detail + options.saturationBias) * i
            boostSum += (od[p + 3].toDouble() / 255.0) * i
        }
    }

    val total = (
        detailSum * options.detailWeight +
            skinSum * options.skinWeight +
            saturationSum * options.saturationWeight +
            boostSum * options.boostWeight
        ) / (crop.width * crop.height)

    return Score(
        detail = detailSum,
        saturation = saturationSum,
        skin = skinSum,
        boost = boostSum,
        total = total
    )
}

private fun importance(options: Options, crop: InternalCrop, x: Int, y: Int): Double {
    if (crop.x > x || x >= crop.x + crop.width ||
        crop.y > y || y >= crop.y + crop.height
    ) {
        return options.outsideImportance
    }

    val nx = (x - crop.x) / crop.width
    val ny = (y - crop.y) / crop.height
    val px = abs(0.5 - nx) * 2.0
    val py = abs(0.5 - ny) * 2.0

    val dx = (px - 1.0 + options.edgeRadius).coerceAtLeast(0.0)
    val dy = (py - 1.0 + options.edgeRadius).coerceAtLeast(0.0)
    val d = (dx * dx + dy * dy) * options.edgeWeight

    var s = 1.41 - sqrt(px * px + py * py)

    if (options.ruleOfThirds) {
        s += (s + d + 0.5).coerceAtLeast(0.0) * 1.2 * (thirds(px) + thirds(py))
    }

    return s + d
}

private fun downSample(input: ImgData, factor: Int): ImgData {
    val idata = input.data
    val iwidth = input.width
    val width = input.width / factor
    val height = input.height / factor
    val output = ImgData(width, height)
    val data = output.data
    val ifactor2 = 1.0 / (factor * factor)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val i = (y * width + x) * 4
            var r = 0.0
            var g = 0.0
            var b = 0.0
            var a = 0.0
            var mr = 0
            var mg = 0

            for (v in 0 until factor) {
                for (u in 0 until factor) {
                    val j = ((y * factor + v) * iwidth + (x * factor + u)) * 4
                    r += idata[j].toDouble()
                    g += idata[j + 1].toDouble()
                    b += idata[j + 2].toDouble()
                    a += idata[j + 3].toDouble()
                    mr = mr.coerceAtLeast(idata[j])
                    mg = mg.coerceAtLeast(idata[j + 1])
                }
            }

            data[i] = (r * ifactor2 * 0.5 + mr * 0.5).toInt().coerceIn(0, 255)
            data[i + 1] = (g * ifactor2 * 0.7 + mg * 0.3).toInt().coerceIn(0, 255)
            data[i + 2] = (b * ifactor2).toInt().coerceIn(0, 255)
            data[i + 3] = (a * ifactor2).toInt().coerceIn(0, 255)
        }
    }
    return output
}

private fun cie(r: Int, g: Int, b: Int): Double {
    return 0.5126 * b + 0.7152 * g + 0.0722 * r
}

private fun skinColor(options: Options, r: Int, g: Int, b: Int): Double {
    val mag = sqrt((r * r + g * g + b * b).toDouble())
    if (mag == 0.0) return 0.0
    val rd = r.toDouble() / mag - options.skinColor[0]
    val gd = g.toDouble() / mag - options.skinColor[1]
    val bd = b.toDouble() / mag - options.skinColor[2]
    val d = sqrt(rd * rd + gd * gd + bd * bd)
    return 1.0 - d
}

private fun saturation(r: Int, g: Int, b: Int): Double {
    val rf = r.toDouble() / 255.0
    val gf = g.toDouble() / 255.0
    val bf = b.toDouble() / 255.0
    val maximum = rf.coerceAtLeast(gf).coerceAtLeast(bf)
    val minimum = rf.coerceAtMost(gf).coerceAtMost(bf)
    if (maximum == minimum) return 0.0
    val l = (maximum + minimum) / 2.0
    val d = maximum - minimum
    return if (l > 0.5) d / (2.0 - maximum - minimum) else d / (maximum + minimum)
}

private fun thirds(x: Double): Double {
    val t = (((x - 1.0 / 3.0 + 1.0) % 2.0) * 0.5 - 0.5) * 16.0
    return (1.0 - t * t).coerceAtLeast(0.0)
}

private fun prescaleImage(image: ImgData, scale: Double): ImgData {
    val newWidth = (image.width * scale).toInt()
    val newHeight = (image.height * scale).toInt()
    val data = IntArray(newWidth * newHeight * 4)

    for (y in 0 until newHeight) {
        for (x in 0 until newWidth) {
            val srcX = (x / scale).toInt().coerceIn(0, image.width - 1)
            val srcY = (y / scale).toInt().coerceIn(0, image.height - 1)
            val srcIdx = (srcY * image.width + srcX) * 4
            val dstIdx = (y * newWidth + x) * 4
            data[dstIdx] = image.data[srcIdx]
            data[dstIdx + 1] = image.data[srcIdx + 1]
            data[dstIdx + 2] = image.data[srcIdx + 2]
            data[dstIdx + 3] = image.data[srcIdx + 3]
        }
    }

    return ImgData(newWidth, newHeight, data)
}
