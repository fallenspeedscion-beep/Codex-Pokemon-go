package org.primeval.manager

import java.io.StringWriter
import java.time.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private val specimen =
        Specimen(profileId = 4, species = "Eevee", attack = 13, defense = 14, stamina = 15)

    @Test
    fun allIvCombinations() {
        for (a in 0..15) for (d in 0..15) for (s in 0..15) assertEquals(
            (a + d + s) / 45.0 * 100,
            ivs(a, d, s)!!,
            0.00000001,
        )
    }

    @Test
    fun invalidAndMissing() {
        assertNull(ivs(16, 0, 0))
        assertNull(ivs(0, -1, 0))
        assertNull(ivs(null, 0, 0))
    }

    @Test
    fun syntheticBars() {
        val labels =
            listOf("Attack", "Defense", "HP").mapIndexed { i, t ->
                Line(t, 50, 20 + i * 40, 100, 30 + i * 40)
            }
        for (v in 0..15) {
            val pixels =
                object : Pixels {
                    override val width = 500
                    override val height = 200

                    override fun rgb(x: Int, y: Int) =
                        when {
                            labels.none { y in it.bottom + 2..it.bottom + 6 } || x !in 50..199 ->
                                0xffffff
                            x in 99..100 || x in 149..150 -> 0xffffff
                            x < 50 + 10 * v -> 0xef9940
                            else -> 0xcacaca
                        }
                }
            assertEquals(Spread(v, v, v), Recognition.bars(labels, pixels))
        }
    }

    @Test
    fun uncertainFramesStayUnknown() {
        assertNull(consensus(Spread(1, 2, 3), Spread(1, 2, 4)))
        assertNull(consensus(null, Spread(1, 2, 3)))
        assertNull(Recognition.detail(emptyList()))
    }

    private fun lines(vararg t: String) =
        t.mapIndexed { i, s -> Line(s, 20, i * 24, 95, i * 24 + 20) }

    @Test
    fun detailFingerprint() {
        assertEquals(
            Detail("Eevee", 900, 49),
            Recognition.detail(lines("CP 900", "Eevee", "40 / 49 HP")),
        )
        assertNull(Recognition.detail(lines("CP 900", "CP 901", "Eevee", "40 / 49 HP")))
    }

    @Test
    fun moveSlots() {
        val r =
            Recognition.moves(
                lines(
                    "Fast Attack",
                    "Tackle",
                    "Charged Attack",
                    "Frustration",
                    "Charged Attack 2",
                    "Return",
                    "Max Moves",
                    "Max Strike",
                )
            )
        assertEquals(Attacks("Tackle", "Frustration", "Return", true), r)
    }

    @Test
    fun maxMovesExcluded() {
        assertNull(
            Recognition.moves(
                lines("Max Moves", "Fast Attack", "Tackle", "Charged Attack", "Return")
            )
        )
    }

    @Test
    fun templateFields() {
        assertEquals("Eeve 93", Naming.preview(specimen, Naming.default))
        assertEquals("13/14/15", Naming.preview(specimen, "{atk}/{def}/{hp}"))
        assertTrue(Naming.protected(specimen.copy(nickname = "Buddy")))
    }

    @Test
    fun unicodeBound() {
        val emoji = "🌿"
        assertEquals(emoji.repeat(6), Naming.preview(specimen, emoji.repeat(6)))
        assertTrue(runCatching { Naming.preview(specimen, emoji.repeat(7)) }.isFailure)
        assertTrue(runCatching { Naming.preview(specimen, "{unknown}") }.isFailure)
    }

    @Test
    fun independentCsvAndJson() {
        val p = specimen.copy(nickname = "★ A,\"B\"\nC")
        fun out(json: Boolean) =
            StringWriter()
                .also { Export.write(it, listOf(p), mapOf(4L to "Local"), json) }
                .toString()
        assertTrue(out(false).contains("\"★ A,\"\"B\"\"\nC\""))
        val obj = JSONArray(out(true)).getJSONObject(0)
        assertEquals(p.nickname, obj.getString("nickname"))
        assertTrue(obj.isNull("shiny"))
        assertEquals("Local", obj.getString("profile"))
        assertTrue(obj.has("scannedAt"))
    }

    private fun feed(vararg props: String) =
        "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:one\r\n" +
            props.joinToString("\r\n") +
            "\r\nEND:VEVENT\r\nEND:VCALENDAR"

    @Test
    fun calendarZoneAndEscapes() {
        val e =
            CalendarFeed.parse(
                    feed(
                        "DTSTART;TZID=Europe/London:20260605T100000",
                        "DTEND:20260605T110000Z",
                        "DESCRIPTION:One\\nTwo\\,",
                        "  three",
                        "SUMMARY:X",
                    ),
                    "a",
                )
                .single()
        assertEquals(Instant.parse("2026-06-05T09:00:00Z").toEpochMilli(), e.start)
        assertEquals("One\nTwo, three", e.description)
    }

    @Test
    fun allDayDst() {
        val e =
            CalendarFeed.parse(
                    feed("DTSTART;VALUE=DATE:20260308"),
                    "a",
                    ZoneId.of("America/New_York"),
                )
                .single()
        assertTrue(e.allDay)
        assertEquals(23 * 3600000L, e.end - e.start)
    }

    @Test
    fun cancelledUpdate() {
        val e = CalendarFeed.parse(feed("STATUS:CANCELLED", "SEQUENCE:3"), "a").single()
        assertTrue(e.cancelled)
        assertEquals(3, e.sequence)
    }

    @Test
    fun invalidCalendar() {
        assertTrue(
            runCatching { CalendarFeed.parse(feed("DTSTART;TZID=Fake/Zone:20260101T100000"), "a") }
                .isFailure
        )
    }

    private fun rule(category: String, slot: String = "Charged") =
        Rule(
            category,
            "synthetic",
            "unit fixture",
            "Eevee",
            "*",
            10,
            20,
            "Test move",
            slot,
            "Test evolution",
            Priority.RECOMMENDED,
            "Synthetic basis",
        )

    @Test
    fun frustrationRule() {
        val p = specimen.copy(shadow = true, charged = "Frustration")
        val category = "Frustration"
        assertEquals(Priority.UNKNOWN, Advisor.assess(p, category, emptyList(), 12).priority)
        assertEquals(
            Priority.RECOMMENDED,
            Advisor.assess(p, category, listOf(rule(category)), 10).priority,
        )
        assertEquals(
            Priority.UNKNOWN,
            Advisor.assess(p, category, listOf(rule(category)), 20).priority,
        )
    }

    @Test
    fun exclusiveEligibility() {
        val c = "Community Day"
        val p = specimen.copy(shadow = true, charged = "Frustration")
        assertEquals(Priority.SKIP, Advisor.assess(p, c, listOf(rule(c)), 15).priority)
        assertEquals(
            Priority.RECOMMENDED,
            Advisor.assess(p, c, listOf(rule(c, "Fast")), 15).priority,
        )
        assertEquals(
            Priority.UNKNOWN,
            Advisor.assess(p, c, listOf(rule(c).copy(form = "Costume")), 15).priority,
        )
    }

    @Test
    fun pauseAndStop() {
        val s = Session(true)
        val d = Detail("Eevee", 1, 2)
        s.detail(d)
        s.appraise(Spread(1, 2, 3))
        assertEquals(Phase.MOVES, s.phase)
        repeat(3) { s.fail("missing") }
        assertEquals(Phase.PAUSED, s.phase)
        s.resume()
        assertNull(s.details)
        assertEquals(Phase.DETAILS, s.phase)
        s.stop()
        s.detail(d)
        s.resume()
        assertEquals(Phase.STOPPED, s.phase)
    }

    @Test
    fun ambiguousAdvance() {
        val s = Session(false)
        val d = Detail("A", 2, 3)
        s.detail(d)
        s.appraise(Spread(1, 1, 1))
        s.saved()
        s.detail(d)
        assertEquals(Phase.NEXT, s.phase)
        s.detail(d.copy(cp = 3))
        assertEquals(Phase.APPRAISAL, s.phase)
    }
}
