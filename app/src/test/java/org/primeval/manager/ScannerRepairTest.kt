package org.primeval.manager

import org.junit.Assert.*
import org.junit.Test

/** All images in this class are generated pixel grids. */
class ScannerRepairTest {
    private val stats = listOf("Attack", "Defense", "HP")
    private val labels = stats.mapIndexed { i, s -> Line(s, 66, 380 + i * 52, 110, 398 + i * 52) }

    private fun card(
        ivs: List<Int>,
        colors: List<Int>,
        offsets: List<Int> = listOf(0, 0, 0),
        covered: Boolean = false,
    ) =
        object : Pixels {
            override val width = 576
            override val height = 750

            override fun rgb(x: Int, y: Int): Int {
                for (i in 0..2) {
                    val local = x - 66 - offsets[i]
                    if (y !in 404 + i * 52..415 + i * 52 || local !in 0..197) continue
                    if (covered && i == 1 && local in 70..105) return 0x282828
                    val segment = local / 67
                    val pos = local % 67
                    if (pos >= 64) return 0xffffff
                    return if (segment * 5 + (5.0 * pos / 64) < ivs[i]) colors[i] else 0xe4e4e4
                }
                return 0xffffff
            }
        }

    private val palette = listOf(0xefab4a, 0xd87c88, 0xf2a23b)

    private fun read(v: List<Int>, extras: List<Line> = emptyList()) =
        Appraisal.read(labels + extras, card(v, palette))

    @Test
    fun segmentedCardEveryValueAndColour() {
        for (i in 0..15) {
            val v = listOf(i, 15 - i, i)
            assertEquals(Spread(i, 15 - i, i), read(v).spread)
        }
    }

    @Test
    fun obstructionAndAlignmentRejectExactTriple() {
        val v = listOf(12, 15, 14)
        assertNull(Appraisal.read(labels, card(v, palette, covered = true)).spread)
        assertNull(Appraisal.read(labels, card(v, palette, listOf(0, 18, 0))).spread)
        assertNull(Appraisal.read(emptyList(), card(v, palette)).spread)
    }

    @Test
    fun animationShiftAndDisagreement() {
        val v = listOf(12, 15, 14)
        val stable = read(v)
        assertTrue(stable.agrees(read(v)))
        assertFalse(stable.agrees(read(listOf(10, 14, 14))))
        assertFalse(stable.agrees(Appraisal.read(labels, card(v, palette, listOf(8, 8, 8)))))
        assertNull(read(v, listOf(Line("1 star", 54, 350, 101, 370))).spread)
    }

    @Test
    fun statusArtworkDoesNotSupplyIvValues() {
        for (status in listOf("Shadow", "Purified", "Dynamax")) assertEquals(
            Spread(12, 15, 14),
            read(listOf(12, 15, 14), listOf(Line(status, 20, 100, 120, 130))).spread,
        )
    }

    private val header =
        listOf(
            Line("CP656", 120, 10, 220, 40),
            Line("Krabby", 110, 90, 240, 128),
            Line("60 / 60 HP", 100, 150, 245, 175),
            Line("Appraise", 120, 199, 212, 219),
            Line("Dynamax", 20, 270, 158, 295),
        )
    private val icon =
        object : Pixels {
            override val width = 576
            override val height = 750

            override fun rgb(x: Int, y: Int): Int {
                if (x in 476..514 && listOf(640, 651, 662).any { y in it..it + 2 }) return 0x8ad9aa
                if ((x - 495) * (x - 495) + (y - 652) * (y - 652) <= 45 * 45) return 0x167e84
                return 0xffffff
            }
        }

    @Test
    fun tagCannotAuthorizeMenuCommand() {
        val d = Recognition.detail(header)!!
        val scene = ScanNavigation.inspect(header, icon, d, false)
        assertEquals(NavKind.DETAILS_MENU, scene.kind)
        assertNotNull(scene.target)
        assertEquals(
            NavKind.UNKNOWN,
            ScanNavigation.inspect(header, icon, d.copy(cp = 657), false).kind,
        )
        assertEquals(NavKind.DETAILS, ScanNavigation.inspect(header, icon, d, true).kind)
    }

    @Test
    fun menuRequiresUniqueAlignedPeers() {
        val menu =
            listOf("TAG", "APPRAISE", "TRANSFER").mapIndexed { i, s ->
                Line(s, 360, 200 + i * 70, 500, 230 + i * 70)
            }
        val d = Recognition.detail(header)!!
        assertEquals(NavKind.MENU_APPRAISE, ScanNavigation.inspect(menu, icon, d, false).kind)
        assertEquals(NavKind.UNKNOWN, ScanNavigation.inspect(menu.drop(1), icon, d, false).kind)
    }

    @Test
    fun readOrdinarySlotsAcrossOverlappingViewports() {
        fun page(vararg text: String) =
            MoveViewport.read(text.mapIndexed { i, s -> Line(s, 0, i * 40, 220, i * 40 + 30) })
        val first =
            page("Max Moves", "Max Strike 100", "Gyms & Raids", "Bubble 10", "Frustration 10")
        val known = first.extend(emptyList())!!
        assertFalse(first.end)
        val next = page("Frustration 10", "Return 35", "Max Moves", "Max Strike 100")
        assertEquals(listOf("Bubble", "Frustration", "Return"), next.extend(known))
        assertNull(page("Sludge 25", "New Attack").extend(known))
        val single = page("Gyms & Raids", "Bubble 10", "Return 35", "New Attack")
        assertTrue(single.end)
        assertEquals(2, single.extend(emptyList())!!.size)
    }

    @Test
    fun appraisalCannotCrossIdentityAndRetriesRetainDraft() {
        val d = Recognition.detail(header)!!
        val img = card(listOf(12, 15, 14), palette)
        val lines = header + labels + Line("This Krabby was caught on", 12, 680, 340, 710)
        val card = ScanNavigation.inspect(lines, img, d, false)
        assertEquals(NavKind.APPRAISAL, card.kind)
        assertNotNull(card.target)
        assertEquals(
            NavKind.UNKNOWN,
            ScanNavigation.inspect(lines, img, d.copy(hp = 61), false).kind,
        )
        val session = Session(true)
        session.detail(d)
        repeat(3) { session.fail("alignment") }
        assertEquals(Phase.PAUSED, session.phase)
        session.resume()
        assertEquals(Phase.APPRAISAL, session.phase)
        assertEquals(d, session.details)
    }
}
