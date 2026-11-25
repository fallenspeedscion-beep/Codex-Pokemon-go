package org.primeval.manager

import org.junit.Assert.*
import org.junit.Test

/** Synthetic rendering with known IVs, a bounded card, JPEG-like dividers and blended endpoints. */
class AppraisalPixelTest {
    private fun image(
        ivs: List<Int>,
        scale: Double = 1.0,
        missing: Int = -1,
        offsetDefense: Int = 0,
        seam: Int = 1,
        card: Boolean = true,
    ) =
        object : Pixels {
            override val width = (600 * scale).toInt()
            override val height = (1200 * scale).toInt()

            override fun rgb(x: Int, y: Int): Int {
                val px = (x / scale).toInt()
                val py = (y / scale).toInt()
                if (card && (px !in 39..305 || py !in 815..1040)) return 0x683c55
                for ((row, iv) in ivs.withIndex()) {
                    if (row == missing || py !in 863 + row * 53..875 + row * 53) continue
                    val pos = px - 70 - (if (row == 1) offsetDefense else 0)
                    if (pos !in 0 until 204) continue
                    val section = pos / 69
                    val local = pos % 69
                    if (local >= 66) return if (iv == 15) 0xffdcdd else 0xfbf7ef
                    val points = (iv - section * 5).coerceIn(0, 5)
                    val filled = (points * 66 + 2) / 5
                    if (local < filled) return if (iv == 15) 0xde848e else 0xeea744
                    if (points in 1..4 && local < filled + seam) return 0xead2b7
                    return 0xe3e3e3
                }
                return 0xffffff
            }
        }

    @Test
    fun pixelsAloneReadEachValueAtMultipleScales() {
        for (scale in listOf(0.7, 1.0, 1.8)) for (iv in 0..15) {
            val values = listOf(iv, 15 - iv, (iv + 7) % 16)
            val result = Appraisal.read(emptyList(), image(values, scale))
            assertEquals(
                "scale=$scale expected=$values ${result.summary()}",
                Spread(values[0], values[1], values[2]),
                result.spread,
            )
        }
    }

    @Test
    fun emptyAndFullTriplesNeedThreeMeasuredRows() {
        for (n in listOf(0, 15)) {
            val r = Appraisal.read(emptyList(), image(List(3) { n }))
            assertEquals(r.summary(), Spread(n, n, n), r.spread)
        }
    }

    @Test
    fun twoVisuallyValidCardsAreAmbiguous() {
        val a = image(listOf(5, 10, 14))
        val stacked =
            object : Pixels by a {
                override fun rgb(x: Int, y: Int): Int =
                    if (y in 605..810) a.rgb(x, y + 215) else a.rgb(x, y)
            }
        val r = Appraisal.read(emptyList(), stacked)
        assertNull(r.spread)
        assertTrue(r.summary(), r.error.orEmpty().contains("aligned card count=2"))
    }

    @Test
    fun missingRowsAndUnboundedOrMisalignedCardsAreUnknown() {
        val values = listOf(1, 15, 14)
        for (missing in 0..2) assertNull(
            Appraisal.read(emptyList(), image(values, missing = missing)).spread
        )
        assertNull(Appraisal.read(emptyList(), image(values, offsetDefense = 20)).spread)
        assertNull(Appraisal.read(emptyList(), image(values, card = false)).spread)
        assertNull(Appraisal.read(emptyList(), image(values, seam = 12)).spread)
    }

    @Test
    fun incompleteOcrCanReadCardButVisualChangeCannotVerify() {
        val hp = listOf(Line("HP", 70, 940, 94, 961))
        val a = Appraisal.read(hp, image(listOf(1, 15, 15)))
        val b = Appraisal.read(emptyList(), image(listOf(1, 15, 15)))
        assertTrue(a.summary(), a.agrees(b))
        assertFalse(a.agrees(Appraisal.read(hp, image(listOf(2, 15, 15)))))
    }
}
