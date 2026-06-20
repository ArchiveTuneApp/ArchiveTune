/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shazamkit

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

data class ShazamSignature(
    val uri: String,
    val sampleDurationMs: Long,
)

class ShazamSignatureGenerator(
    private val maxTimeSeconds: Double = 3.1,
    private val maxPeaks: Int = 255,
) {
    private val pending = IntArrayList()
    private var processedSamples = 0

    private val sampleRateHz = 16000
    private val fft = Fft(2048)
    private val window =
        DoubleArray(2048) { i ->
            val n = 2050
            val j = i + 1
            0.5 - 0.5 * cos(2.0 * PI * j / (n - 1))
        }
    private val realBuffer = DoubleArray(2048)
    private val imagBuffer = DoubleArray(2048)

    private val ringSamples = IntArray(2048)
    private var ringSamplePos = 0

    private val fftOutputs = Array(256) { DoubleArray(1025) }
    private var fftPos = 0
    private var fftWritten = 0

    private val spreadOutputs = Array(256) { DoubleArray(1025) }
    private var spreadPos = 0
    private var spreadWritten = 0

    private var signatureNumberSamples = 0
    private val bandToPeaks = linkedMapOf<FrequencyBand, MutableList<FrequencyPeak>>()

    fun feedPcm16Mono(samples: ShortArray) {
        for (s in samples) {
            pending.add(s.toInt())
        }
    }

    fun reset() {
        pending.clear()
        processedSamples = 0
        ringSamples.fill(0)
        ringSamplePos = 0
        fftPos = 0
        fftWritten = 0
        spreadPos = 0
        spreadWritten = 0
        signatureNumberSamples = 0
        bandToPeaks.clear()
    }

    fun nextSignatureOrNull(): ShazamSignature? {
        if (pending.size - processedSamples < 128) return null

        while (pending.size - processedSamples >= 128 &&
            (
                signatureNumberSamples.toDouble() / sampleRateHz < maxTimeSeconds ||
                    bandToPeaks.values.sumOf { it.size } < maxPeaks
            )
        ) {
            processInput(pending, processedSamples, processedSamples + 128)
            processedSamples += 128
        }

        if (bandToPeaks.isEmpty()) return null

        val message =
            DecodedMessage(
                sampleRateHz = sampleRateHz,
                numberSamples = signatureNumberSamples,
                frequencyBandToSoundPeaks =
                    bandToPeaks
                        .toSortedMap(compareBy { it.value })
                        .mapValues { it.value.toList() },
            )

        val signature =
            ShazamSignature(
                uri = message.encodeToUri(),
                sampleDurationMs = (signatureNumberSamples * 1000L) / sampleRateHz,
            )

        reset()
        return signature
    }

    private fun processInput(
        samples: IntArrayList,
        start: Int,
        endExclusive: Int,
    ) {
        val count = endExclusive - start
        signatureNumberSamples += count

        var pos = start
        while (pos < endExclusive) {
            doFft(samples, pos, pos + 128)
            doPeakSpreadingAndRecognition()
            pos += 128
        }
    }

    private fun doFft(
        samples: IntArrayList,
        start: Int,
        endExclusive: Int,
    ) {
        var i = start
        while (i < endExclusive) {
            ringSamples[ringSamplePos] = samples[i]
            ringSamplePos++
            if (ringSamplePos == ringSamples.size) ringSamplePos = 0
            i++
        }

        var p = ringSamplePos
        var idx = 0
        while (idx < 2048) {
            if (p == 2048) p = 0
            realBuffer[idx] = ringSamples[p].toDouble() * window[idx]
            imagBuffer[idx] = 0.0
            p++
            idx++
        }

        fft.fft(realBuffer, imagBuffer)

        val out = fftOutputs[fftPos]
        var k = 0
        while (k <= 1024) {
            val re = realBuffer[k]
            val im = imagBuffer[k]
            val v = (re * re + im * im) / 131072.0
            out[k] = if (v <= 1e-10) 1e-10 else v
            k++
        }

        fftPos++
        if (fftPos == fftOutputs.size) fftPos = 0
        fftWritten++
    }

    private fun doPeakSpreadingAndRecognition() {
        doPeakSpreading()
        if (spreadWritten >= 46) {
            doPeakRecognition()
        }
    }

    private fun doPeakSpreading() {
        val lastFftIndex = (fftPos - 1).floorMod(fftOutputs.size)
        val origin = fftOutputs[lastFftIndex]

        val originSpread = DoubleArray(1025)
        var i = 0
        while (i <= 1021) {
            originSpread[i] = max(origin[i], max(origin[i + 1], origin[i + 2]))
            i++
        }
        originSpread[1022] = origin[1022]
        originSpread[1023] = origin[1023]
        originSpread[1024] = origin[1024]

        val i1 = (spreadPos - 1).floorMod(spreadOutputs.size)
        val i2 = (spreadPos - 3).floorMod(spreadOutputs.size)
        val i3 = (spreadPos - 6).floorMod(spreadOutputs.size)

        val s1 = spreadOutputs[i1]
        val s2 = spreadOutputs[i2]
        val s3 = spreadOutputs[i3]

        var bin = 0
        while (bin < 1025) {
            val m1 = max(originSpread[bin], s1[bin])
            s1[bin] = m1
            val m2 = max(m1, s2[bin])
            s2[bin] = m2
            val m3 = max(m2, s3[bin])
            s3[bin] = m3
            bin++
        }

        val dst = spreadOutputs[spreadPos]
        System.arraycopy(originSpread, 0, dst, 0, 1025)

        spreadPos++
        if (spreadPos == spreadOutputs.size) spreadPos = 0
        spreadWritten++
    }

    private fun doPeakRecognition() {
        val fftMinus46 = fftOutputs[(fftPos - 46).floorMod(fftOutputs.size)]
        val spreadMinus49 = spreadOutputs[(spreadPos - 49).floorMod(spreadOutputs.size)]

        var bin = 10
        while (bin <= 1014) {
            val energy = fftMinus46[bin]
            if (energy >= (1.0 / 64.0) && energy >= spreadMinus49[bin - 1]) {
                var maxNeighbor = 0.0
                val offsets = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)
                var oi = 0
                while (oi < offsets.size) {
                    maxNeighbor = max(maxNeighbor, spreadMinus49[bin + offsets[oi]])
                    oi++
                }

                if (energy > maxNeighbor) {
                    var maxTimeNeighbor = maxNeighbor
                    val timeOffsets =
                        intArrayOf(
                            -53,
                            -45,
                            165,
                            172,
                            179,
                            186,
                            193,
                            200,
                            214,
                            221,
                            228,
                            235,
                            242,
                            249,
                        )
                    var ti = 0
                    while (ti < timeOffsets.size) {
                        val idx = (spreadPos + timeOffsets[ti]).floorMod(spreadOutputs.size)
                        maxTimeNeighbor = max(maxTimeNeighbor, spreadOutputs[idx][bin - 1])
                        ti++
                    }

                    if (energy > maxTimeNeighbor) {
                        val fftNumber = spreadWritten - 46
                        val peakMagnitude = ln(max(1.0 / 64.0, energy)) * 1477.3 + 6144.0
                        val peakMagnitudeBefore =
                            ln(max(1.0 / 64.0, fftMinus46[bin - 1])) * 1477.3 + 6144.0
                        val peakMagnitudeAfter =
                            ln(max(1.0 / 64.0, fftMinus46[bin + 1])) * 1477.3 + 6144.0

                        val variation1 = peakMagnitude * 2.0 - peakMagnitudeBefore - peakMagnitudeAfter
                        if (variation1 > 0.0) {
                            val variation2 = (peakMagnitudeAfter - peakMagnitudeBefore) * 32.0 / variation1
                            val correctedBin = bin * 64.0 + variation2
                            val freqHz = correctedBin * (sampleRateHz / 2.0 / 1024.0 / 64.0)

                            val band =
                                when {
                                    freqHz in 250.0..520.0 -> FrequencyBand.HZ_250_520
                                    freqHz > 520.0 && freqHz <= 1450.0 -> FrequencyBand.HZ_520_1450
                                    freqHz > 1450.0 && freqHz <= 3500.0 -> FrequencyBand.HZ_1450_3500
                                    freqHz > 3500.0 && freqHz <= 5500.0 -> FrequencyBand.HZ_3500_5500
                                    else -> null
                                }

                            if (band != null) {
                                val peaks = bandToPeaks.getOrPut(band) { mutableListOf() }
                                peaks.add(
                                    FrequencyPeak(
                                        fftPassNumber = fftNumber,
                                        peakMagnitude = peakMagnitude.toInt(),
                                        correctedPeakFrequencyBin = correctedBin.toInt(),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            bin++
        }
    }

    private enum class FrequencyBand(
        val value: Int,
    ) {
        HZ_250_520(0),
        HZ_520_1450(1),
        HZ_1450_3500(2),
        HZ_3500_5500(3),
    }

    private data class FrequencyPeak(
        val fftPassNumber: Int,
        val peakMagnitude: Int,
        val correctedPeakFrequencyBin: Int,
    )

    private data class DecodedMessage(
        val sampleRateHz: Int,
        val numberSamples: Int,
        val frequencyBandToSoundPeaks: Map<FrequencyBand, List<FrequencyPeak>>,
    ) {
        fun encodeToUri(): String = "data:audio/vnd.shazam.sig;base64," + Base64.getEncoder().encodeToString(encodeToBinary())

        private fun encodeToBinary(): ByteArray {
            val contents = ByteArrayOutputStream()

            for ((band, peaks) in frequencyBandToSoundPeaks.entries.sortedBy { it.key.value }) {
                val peaksBytes = ByteArrayOutputStream()
                var lastFftPass = 0
                for (peak in peaks) {
                    var delta = peak.fftPassNumber - lastFftPass
                    if (delta >= 255) {
                        peaksBytes.write(0xFF)
                        peaksBytes.writeLittleInt(peak.fftPassNumber)
                        lastFftPass = peak.fftPassNumber
                        delta = 0
                    }
                    peaksBytes.write(delta.coerceAtLeast(0).coerceAtMost(254))
                    peaksBytes.writeLittleShort(peak.peakMagnitude)
                    peaksBytes.writeLittleShort(peak.correctedPeakFrequencyBin)
                    lastFftPass = peak.fftPassNumber
                }

                val peaksValue = peaksBytes.toByteArray()
                contents.writeLittleInt(0x60030040 + band.value)
                contents.writeLittleInt(peaksValue.size)
                contents.write(peaksValue)
                val padding = (-peaksValue.size).floorMod(4)
                repeat(padding) { contents.write(0) }
            }

            val contentsValue = contents.toByteArray()
            val sizeMinusHeader = contentsValue.size + 8

            val header =
                ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(0xCAFE2580.toInt())
                    putInt(0)
                    putInt(sizeMinusHeader)
                    putInt(0x94119C00.toInt())
                    putInt(0)
                    putInt(0)
                    putInt(0)
                    val sampleRateId = 3
                    putInt(sampleRateId shl 27)
                    putInt(0)
                    putInt(0)
                    putInt((numberSamples + sampleRateHz * 0.24).toInt())
                    putInt((15 shl 19) + 0x40000)
                }

            val full = ByteArrayOutputStream()
            full.write(header.array())
            full.writeLittleInt(0x40000000)
            full.writeLittleInt(sizeMinusHeader)
            full.write(contentsValue)

            val withHeader = full.toByteArray()
            val crc = CRC32().apply { update(withHeader, 8, withHeader.size - 8) }.value.toInt()

            val crcBuf = ByteBuffer.wrap(withHeader).order(ByteOrder.LITTLE_ENDIAN)
            crcBuf.putInt(4, crc)
            return withHeader
        }
    }
}

private fun Int.floorMod(mod: Int): Int {
    val r = this % mod
    return if (r < 0) r + mod else r
}

private class IntArrayList(
    initialCapacity: Int = 8192,
) {
    private var array = IntArray(initialCapacity)
    var size: Int = 0
        private set

    operator fun get(index: Int): Int = array[index]

    fun add(value: Int) {
        if (size == array.size) {
            array = array.copyOf(array.size * 2)
        }
        array[size] = value
        size++
    }

    fun clear() {
        size = 0
    }
}

private fun ByteArrayOutputStream.writeLittleInt(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
}

private fun ByteArrayOutputStream.writeLittleShort(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
}

private class Fft(
    private val n: Int,
) {
    private val cosTable = DoubleArray(n / 2) { i -> cos(2.0 * PI * i / n) }
    private val sinTable = DoubleArray(n / 2) { i -> sin(2.0 * PI * i / n) }
    private val bitReversal =
        IntArray(n).also { out ->
            val bits = Integer.numberOfTrailingZeros(n)
            for (i in 0 until n) {
                out[i] = i.reverseBits(bits)
            }
        }

    fun fft(
        real: DoubleArray,
        imag: DoubleArray,
    ) {
        for (i in 0 until n) {
            val j = bitReversal[i]
            if (j > i) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val tableStep = n / len
            var i = 0
            while (i < n) {
                var j = 0
                var k = 0
                while (j < halfLen) {
                    val cos = cosTable[k]
                    val sin = sinTable[k]
                    val i1 = i + j
                    val i2 = i1 + halfLen

                    val r2 = real[i2]
                    val im2 = imag[i2]
                    val tpre = r2 * cos + im2 * sin
                    val tpim = -r2 * sin + im2 * cos

                    real[i2] = real[i1] - tpre
                    imag[i2] = imag[i1] - tpim
                    real[i1] += tpre
                    imag[i1] += tpim

                    j++
                    k += tableStep
                }
                i += len
            }
            len = len shl 1
        }
    }
}

private fun Int.reverseBits(bitCount: Int): Int {
    var x = this
    var y = 0
    repeat(bitCount) {
        y = (y shl 1) or (x and 1)
        x = x ushr 1
    }
    return y
}
