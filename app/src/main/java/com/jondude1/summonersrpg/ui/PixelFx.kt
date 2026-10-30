package com.jondude1.summonersrpg.ui

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Pixel-art helpers with no Android types in them, so they can be tested on a plain JVM. */
object PixelFx {
    // ---- noise ----
    fun hash(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return h and 0x7fffffff
    }

    fun rnd(x: Int, y: Int, seed: Int): Float = (hash(x, y, seed) and 0xffff) / 65535f

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    fun vnoise(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = smooth(x - x0)
        val fy = smooth(y - y0)
        val a = rnd(x0, y0, seed)
        val b = rnd(x0 + 1, y0, seed)
        val c = rnd(x0, y0 + 1, seed)
        val d = rnd(x0 + 1, y0 + 1, seed)
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fy)
    }

    fun fbm(x: Float, y: Float, seed: Int): Float =
        vnoise(x, y, seed) * 0.56f + vnoise(x * 2.1f + 7.3f, y * 2.1f + 3.1f, seed + 1) * 0.30f +
            vnoise(x * 4.3f + 1.7f, y * 4.3f + 9.4f, seed + 2) * 0.14f

    // ---- colour ----
    fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun red(c: Int): Int = (c shr 16) and 0xFF
    fun green(c: Int): Int = (c shr 8) and 0xFF
    fun blue(c: Int): Int = c and 0xFF

    fun mix(a: Int, b: Int, t: Float): Int = argb(
        (red(a) + (red(b) - red(a)) * t).toInt(),
        (green(a) + (green(b) - green(a)) * t).toInt(),
        (blue(a) + (blue(b) - blue(a)) * t).toInt(),
    )

    fun tone(c: Int, f: Float): Int = argb((red(c) * f).toInt(), (green(c) * f).toInt(), (blue(c) * f).toInt())

    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /** Ordered-dither threshold for a pixel, between 0 and 1. */
    fun bayer(x: Int, y: Int): Float = (BAYER[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f

    // ---- sprites ----

    /**
     * Turns a small hand-drawn sprite (0 = see-through) into a finer one at twice the size: smoother diagonals,
     * a dark selective outline, a light rim on the lit side and a little painted shading.
     */
    fun polish(src: IntArray, w: Int, h: Int, outline: Boolean = true, shade: Boolean = true): IntArray {
        val bw = w * 2
        val bh = h * 2
        fun s(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) 0 else src[y * w + x]
        val big = IntArray(bw * bh)
        for (y in 0 until h) for (x in 0 until w) {
            val p = s(x, y)
            val a = s(x, y - 1)
            val b = s(x + 1, y)
            val c = s(x - 1, y)
            val d = s(x, y + 1)
            val o1 = if (c == a && c != d && a != b) a else p
            val o2 = if (a == b && a != c && b != d) b else p
            val o3 = if (d == c && d != b && c != a) c else p
            val o4 = if (b == d && b != a && d != c) d else p
            big[(2 * y) * bw + 2 * x] = o1
            big[(2 * y) * bw + 2 * x + 1] = o2
            big[(2 * y + 1) * bw + 2 * x] = o3
            big[(2 * y + 1) * bw + 2 * x + 1] = o4
        }
        fun see(x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < bw && y < bh && (big[y * bw + x] ushr 24) != 0
        val edge = BooleanArray(bw * bh)
        if (outline) {
            for (y in 0 until bh) for (x in 0 until bw) {
                if (!see(x, y)) continue
                if (!see(x - 1, y) || !see(x + 1, y) || !see(x, y - 1) || !see(x, y + 1)) edge[y * bw + x] = true
            }
        }
        fun isEdge(x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < bw && y < bh && edge[y * bw + x]
        val out = IntArray(bw * bh)
        for (y in 0 until bh) for (x in 0 until bw) {
            val c = big[y * bw + x]
            if ((c ushr 24) == 0) continue
            if (edge[y * bw + x]) {
                out[y * bw + x] = argb((red(c) * 0.46f).toInt(), (green(c) * 0.46f).toInt(), (blue(c) * 0.60f).toInt())
                continue
            }
            var f = 1f
            if (shade) {
                f = 1.07f - 0.17f * (y.toFloat() / bh) + (bayer(x, y) - 0.5f) * 0.05f
                if (outline) {
                    if (isEdge(x, y - 1) || isEdge(x - 1, y)) f += 0.13f
                    if (isEdge(x + 1, y) || isEdge(x, y + 1)) f -= 0.10f
                }
            }
            out[y * bw + x] = tone(c, f)
        }
        return out
    }

    /** A black copy of a sprite with the same see-through shape, used to cast shadows. */
    fun silhouette(src: IntArray): IntArray = IntArray(src.size) { if ((src[it] ushr 24) != 0) (0xFF shl 24) else 0 }
}

