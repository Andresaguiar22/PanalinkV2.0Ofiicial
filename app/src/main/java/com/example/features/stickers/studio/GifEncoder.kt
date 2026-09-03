package com.example.features.stickers.studio

import android.graphics.Bitmap
import com.example.core.logger.AppLogger
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Animated GIF encoder (port of the public-domain AnimatedGifEncoder / NeuQuant
 * by Kevin Weiner & JMG). Used to turn short videos into animated stickers that
 * any chat client can render.
 */
class GifEncoder(private val out: OutputStream) {

    private var width = 0
    private var height = 0
    private var repeat = 0 // 0 = loop forever
    private var delayMs = 100
    private var started = false
    private var firstFrame = true

    fun setSize(w: Int, h: Int) {
        width = w
        height = h
    }

    fun setRepeat(count: Int) {
        repeat = count
    }

    fun setDelay(ms: Int) {
        delayMs = ms
    }

    fun start(): Boolean {
        if (started) return false
        writeString("GIF89a")
        started = true
        firstFrame = true
        return true
    }

    fun addFrame(bitmap: Bitmap): Boolean {
        if (!started) return false
        val pixels = getPixels(bitmap)
        val nq = NeuQuant(pixels, pixels.size, 10)
        val palette = nq.process()
        val indexed = ByteArray(pixels.size / 3)
        for (i in indexed.indices) {
            indexed[i] = nq.map(
                pixels[i * 3].toInt() and 0xFF,
                pixels[i * 3 + 1].toInt() and 0xFF,
                pixels[i * 3 + 2].toInt() and 0xFF
            ).toByte()
        }
        if (firstFrame) {
            writeLSD(palette)
            if (repeat >= 0) writeNetscapeExt()
        }
        writeGraphicCtrlExt()
        writeImageDesc()
        writePalette(palette)
        writePixels(indexed)
        firstFrame = false
        return true
    }

    fun finish(): Boolean {
        if (!started) return false
        out.write(0x3B) // trailer
        out.flush()
        started = false
        return true
    }

    private fun getPixels(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val rgb = ByteArray(w * h * 3)
        for (i in argb.indices) {
            val c = argb[i]
            rgb[i * 3] = (c shr 16 and 0xFF).toByte()
            rgb[i * 3 + 1] = (c shr 8 and 0xFF).toByte()
            rgb[i * 3 + 2] = (c and 0xFF).toByte()
        }
        return rgb
    }

    private fun writeGraphicCtrlExt() {
        out.write(0x21)
        out.write(0xF9)
        out.write(4)
        out.write(0) // no transparency, no disposal
        writeShort(Math.round(delayMs / 10.0f))
        out.write(0) // transparent color index (unused)
        out.write(0)
    }

    private fun writeImageDesc() {
        out.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        out.write(0x80 or 0x70 or 7) // local color table, 256 colors
    }

    private fun writeLSD(palette: ByteArray) {
        writeShort(width)
        writeShort(height)
        out.write(0x80 or 0x70 or 7) // global color table, 256 colors
        out.write(0) // background
        out.write(0) // aspect
        writePalette(palette)
    }

    private fun writeNetscapeExt() {
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        writeString("NETSCAPE2.0")
        out.write(3)
        out.write(1)
        writeShort(repeat)
        out.write(0)
    }

    private fun writePalette(palette: ByteArray) {
        out.write(palette, 0, palette.size)
        val remaining = 768 - palette.size
        if (remaining > 0) out.write(ByteArray(remaining))
    }

    private fun writePixels(indexed: ByteArray) {
        val lzw = LzwEncoder(width, height, indexed, out)
        lzw.encode(8)
    }

    private fun writeShort(value: Int) {
        out.write(value and 0xFF)
        out.write(value shr 8 and 0xFF)
    }

    private fun writeString(s: String) {
        for (c in s) out.write(c.code)
    }

    companion object {
        fun encode(
            frames: List<Bitmap>,
            delayMs: Int,
            outFile: java.io.File,
            loop: Boolean = true
        ): Boolean {
            if (frames.isEmpty()) return false
            return try {
                BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                    val encoder = GifEncoder(bos)
                    encoder.setSize(frames[0].width, frames[0].height)
                    encoder.setRepeat(if (loop) 0 else -1)
                    encoder.setDelay(delayMs)
                    encoder.start()
                    for (frame in frames) encoder.addFrame(frame)
                    encoder.finish()
                }
                true
            } catch (e: Exception) {
                AppLogger.e(message = "Error encoding GIF", throwable = e)
                false
            }
        }
    }
}

/** NeuQuant neural-network color quantizer (public domain, Anthony Dekker / JMG port). */
private class NeuQuant(pixels: ByteArray, length: Int, sample: Int) {
    private val network = IntArray(256 * 4)
    private val index = IntArray(256)
    private val bias = IntArray(256)
    private val freq = IntArray(256)
    private val radpower = IntArray(32)

    init {
        for (i in 0 until 256) {
            network[i * 4] = (i shl 12) / 256
            network[i * 4 + 1] = (i shl 12) / 256
            network[i * 4 + 2] = (i shl 12) / 256
            network[i * 4 + 3] = i
            freq[i] = 1
            bias[i] = 0
        }
        learn(pixels, length, sample)
        inxbuild()
    }

    fun process(): ByteArray {
        val map = ByteArray(768)
        for (i in 0 until 256) {
            map[index[i] * 3] = network[i * 4].toByte()
            map[index[i] * 3 + 1] = network[i * 4 + 1].toByte()
            map[index[i] * 3 + 2] = network[i * 4 + 2].toByte()
        }
        return map
    }

    fun map(b: Int, g: Int, r: Int): Int {
        var bestd = Int.MAX_VALUE
        var best = 0
        for (i in 0 until 256) {
            val dist = kotlin.math.abs(network[i * 4] - b) +
                kotlin.math.abs(network[i * 4 + 1] - g) +
                kotlin.math.abs(network[i * 4 + 2] - r)
            if (dist < bestd) {
                bestd = dist
                best = i
            }
        }
        return index[best]
    }

    private fun inxbuild() {
        var previouscol = 0
        var startpos = 0
        for (i in 0 until 256) {
            var smallpos = i
            var smallval = network[i * 4 + 1]
            for (j in i + 1 until 256) {
                if (network[j * 4 + 1] < smallval) {
                    smallpos = j
                    smallval = network[j * 4 + 1]
                }
            }
            if (i != smallpos) {
                for (k in 0 until 4) {
                    val t = network[smallpos * 4 + k]
                    network[smallpos * 4 + k] = network[i * 4 + k]
                    network[i * 4 + k] = t
                }
            }
            if (smallval != previouscol) {
                index[previouscol] = (startpos + i) shr 1
                for (j in previouscol + 1 until smallval) index[j] = i
                previouscol = smallval
                startpos = i
            }
        }
        index[previouscol] = (startpos + 255) shr 1
        for (j in previouscol + 1 until 256) index[j] = 255
    }

    private fun learn(pixels: ByteArray, length: Int, sample: Int) {
        val alphadec = 30 + (sample - 1) / 3
        val lengthcount = length / (3 * sample)
        var radius = 32 * 8
        var rad = radius shr 6
        if (rad <= 1) rad = 0
        for (i in 0 until rad) radpower[i] = 30 * ((rad - i) * (rad - i)) * 256 / (rad * rad)
        val step = when {
            lengthcount < 1509 -> 3
            lengthcount % 499 != 0 -> 3 * 499
            lengthcount % 491 != 0 -> 3 * 491
            lengthcount % 487 != 0 -> 3 * 487
            else -> 3 * 1509
        }
        var p = 0
        var i = 0
        var alpha = 1024
        var delta = lengthcount / 100
        if (delta == 0) delta = 1
        var count = 0
        while (i < lengthcount) {
            val b = pixels[p].toInt() and 0xFF
            val g = pixels[p + 1].toInt() and 0xFF
            val r = pixels[p + 2].toInt() and 0xFF
            val j = contest(b, g, r)
            altersingle(alpha, j, b, g, r)
            if (rad != 0) alterneigh(rad, j, b, g, r)
            p += step
            if (p >= length) p -= length
            i++
            count++
            if (count >= delta) {
                count = 0
                alpha -= alpha / alphadec
                radius -= radius / 30
                rad = radius shr 6
                if (rad <= 1) rad = 0
                for (k in 0 until rad) radpower[k] = alpha * ((rad - k) * (rad - k)) * 256 / (rad * rad)
            }
        }
    }

    private fun contest(b: Int, g: Int, r: Int): Int {
        var bestd = Int.MAX_VALUE
        var bestbiasd = Int.MAX_VALUE
        var bestpos = -1
        var bestbiaspos = -1
        for (i in 0 until 256) {
            val n = i * 4
            val dist = kotlin.math.abs(network[n] - b) +
                kotlin.math.abs(network[n + 1] - g) +
                kotlin.math.abs(network[n + 2] - r)
            if (dist < bestd) {
                bestd = dist
                bestpos = i
            }
            val biasdist = dist - (bias[i] shr 12)
            if (biasdist < bestbiasd) {
                bestbiasd = biasdist
                bestbiaspos = i
            }
            val betafreq = freq[i] shr 10
            freq[i] -= betafreq
            bias[i] += betafreq shl 12
        }
        freq[bestpos] += 64
        bias[bestpos] -= 64 shl 12
        return bestbiaspos
    }

    private fun altersingle(alpha: Int, i: Int, b: Int, g: Int, r: Int) {
        val n = i * 4
        network[n] -= alpha * (network[n] - b) / 1024
        network[n + 1] -= alpha * (network[n + 1] - g) / 1024
        network[n + 2] -= alpha * (network[n + 2] - r) / 1024
    }

    private fun alterneigh(rad: Int, i: Int, b: Int, g: Int, r: Int) {
        var lo = i - rad
        if (lo < -1) lo = -1
        var hi = i + rad
        if (hi > 256) hi = 256
        var j = i + 1
        var k = i - 1
        var m = 1
        while (j < hi || k > lo) {
            val a = radpower[m++]
            if (j < hi) {
                val n = j++ * 4
                network[n] -= a * (network[n] - b) / 262144
                network[n + 1] -= a * (network[n + 1] - g) / 262144
                network[n + 2] -= a * (network[n + 2] - r) / 262144
            }
            if (k > lo) {
                val n = k-- * 4
                network[n] -= a * (network[n] - b) / 262144
                network[n + 1] -= a * (network[n + 1] - g) / 262144
                network[n + 2] -= a * (network[n + 2] - r) / 262144
            }
        }
    }
}

/** LZW GIF compressor (public domain, Jef Poskanzer / JMG port). */
private class LzwEncoder(
    private val imgW: Int,
    private val imgH: Int,
    private val pixels: ByteArray,
    private val outs: OutputStream
) {
    private val initCodeSize = 8
    private var curPixel = 0
    private var nBits = 0
    private var maxcode = 0
    private var freeEnt = 0
    private var clearCode = 0
    private var eofCode = 0
    private var curAccum = 0
    private var curBits = 0
    private val htab = IntArray(5003)
    private val codetab = IntArray(5003)
    private var aCount = 0
    private val accum = ByteArray(256)

    fun encode(codeSize: Int) {
        outs.write(codeSize)
        curPixel = 0
        compress(codeSize + 1)
        outs.write(0)
    }

    private fun charOut(c: Int) {
        accum[aCount++] = c.toByte()
        if (aCount >= 254) flushPacket()
    }

    private fun flushPacket() {
        if (aCount > 0) {
            outs.write(aCount)
            outs.write(accum, 0, aCount)
            aCount = 0
        }
    }

    private fun output(code: Int) {
        curAccum = curAccum or (code shl curBits)
        curBits += nBits
        while (curBits >= 8) {
            charOut(curAccum and 0xFF)
            curAccum = curAccum shr 8
            curBits -= 8
        }
        if (freeEnt > maxcode) {
            nBits++
            maxcode = (1 shl nBits) - 1
        }
        if (code == eofCode) {
            while (curBits > 0) {
                charOut(curAccum and 0xFF)
                curAccum = curAccum shr 8
                curBits -= 8
            }
            flushPacket()
        }
    }

    private fun compress(initBits: Int) {
        clearCode = 1 shl initCodeSize
        eofCode = clearCode + 1
        freeEnt = clearCode + 2
        nBits = initBits
        maxcode = (1 shl nBits) - 1
        htab.fill(-1)
        var ent = nextPixel()
        var hshift = 0
        var fcode = htab.size
        while (fcode < 65536) {
            hshift++
            fcode *= 2
        }
        hshift = 8 - hshift
        val hsizeReg = htab.size
        output(clearCode)
        outer@ while (true) {
            val c = nextPixel()
            if (c < 0) break@outer
            val fc = (c shl 12) xor ent
            var i = (c shl hshift) xor ent
            if (htab[i] == fc) {
                ent = codetab[i]
                continue
            } else if (htab[i] >= 0) {
                var disp = hsizeReg - i
                if (i == 0) disp = 1
                do {
                    i -= disp
                    if (i < 0) i += hsizeReg
                    if (htab[i] == fc) {
                        ent = codetab[i]
                        continue@outer
                    }
                } while (htab[i] >= 0)
            }
            output(ent)
            ent = c
            if (freeEnt < 4096) {
                codetab[i] = freeEnt++
                htab[i] = fc
            } else {
                htab.fill(-1)
                freeEnt = clearCode + 2
                nBits = initBits
                maxcode = (1 shl nBits) - 1
                output(clearCode)
            }
        }
        output(ent)
        output(eofCode)
    }

    private fun nextPixel(): Int {
        if (curPixel >= imgW * imgH) return -1
        return pixels[curPixel++].toInt() and 0xFF
    }
}
