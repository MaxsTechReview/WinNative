package com.winlator.cmod.app.shell

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/** Bakes a blur into the decoded bitmap (two separable box passes ≈ Gaussian). */
class BoxBlurTransformation(
    private val radius: Int,
) : Transformation {
    override val cacheKey = "boxBlur:$radius"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        if (radius < 1) return input
        val w = input.width
        val h = input.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        input.getPixels(a, 0, w, 0, 0, w, h)
        repeat(2) {
            horizontalPass(a, b, w, h)
            verticalPass(b, a, w, h)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun horizontalPass(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
    ) {
        val div = 2 * radius + 1
        for (y in 0 until h) {
            val row = y * w
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -radius..radius) {
                val p = src[row + i.coerceIn(0, w - 1)]
                sa += p ushr 24
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[row + (x + radius + 1).coerceAtMost(w - 1)]
                val sub = src[row + (x - radius).coerceAtLeast(0)]
                sa += (add ushr 24) - (sub ushr 24)
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }

    private fun verticalPass(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
    ) {
        val div = 2 * radius + 1
        for (x in 0 until w) {
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -radius..radius) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                sa += p ushr 24
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[(y + radius + 1).coerceAtMost(h - 1) * w + x]
                val sub = src[(y - radius).coerceAtLeast(0) * w + x]
                sa += (add ushr 24) - (sub ushr 24)
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}
