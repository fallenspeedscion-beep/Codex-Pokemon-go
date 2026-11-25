package org.primeval.manager

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReviewTest {
    private val r =
        Specimen(
            id = 9,
            profileId = 2,
            species = "Ditto",
            nickname = "Ditto",
            attack = 10,
            defense = 12,
            stamina = 5,
        )

    @Test
    fun renameNeedsPersistedEvidenceAndConsent() {
        fun allowed(
            p: Specimen = r,
            mode: String = "Automatic",
            custom: Boolean = false,
            moves: Boolean = true,
        ) = RenamePolicy.eligible(mode, p, "Ditto 60", custom, moves)
        assertTrue(allowed())
        assertFalse(allowed(mode = "Scan only"))
        assertFalse(allowed(mode = "Assisted"))
        assertFalse(allowed(r.copy(id = 0)))
        assertFalse(allowed(r.copy(attack = null)))
        assertFalse(allowed(r.copy(nickname = "Best pal")))
        assertTrue(allowed(r.copy(nickname = "Best pal"), custom = true))
        assertFalse(allowed(moves = false))
    }

    @Test
    fun fullCycleCannotWrap() {
        val s = Session(false)
        val a = Detail("A", 10, 10)
        val b = Detail("B", 20, 20)
        for (d in listOf(a, b)) {
            s.detail(d)
            s.appraise(Spread(1, 2, 3))
            s.saved()
        }
        s.detail(a.copy(visible = Visible(lucky = true)))
        assertEquals(Phase.NEXT, s.phase)
        assertEquals(1, s.failures)
    }

    @Test
    fun explicitUnknownKeepsNulls() {
        val s = Session(true)
        s.detail(Detail("A", 2, 3))
        s.keepUncertainBars()
        s.keepUncertainMoves()
        assertNull(s.spread)
        assertNull(s.attacks)
        assertEquals(Phase.REVIEW, s.phase)
    }

    @Test
    fun tableDamageAndMaxBoundary() {
        fun row(t: String, y: Int, left: Int = 10) = Line(t, left, y, left + 90, y + 20)
        val rows =
            listOf(
                row("Gyms & Raids", 1),
                row("Tackle", 41),
                row("5", 41, 180),
                row("Return", 71),
                row("70", 71, 180),
                row("New Attack", 110),
                row("Max Moves", 130),
                row("Max Strike 250", 150),
            )
        assertEquals(Attacks("Tackle", "Return", null, true), Recognition.moves(rows))
    }

    @Test
    fun exactRankRequiresMatchingSpreadAndCap() {
        val data =
            JSONObject(
                """{"version":"synthetic","source":"test","rules":[],"battle":[{"species":"Ditto","form":"","pve":"fixture","basis":"test only","pvp":{"league":"Example","cap":1500,"rank":123,"attack":10,"defense":12,"stamina":5}}]}"""
            )
        val ref = BattleReference.parse(data)
        assertTrue(BattleReference.describe(r.copy(cp = 1500), ref).contains("IV rank 123"))
        assertFalse(BattleReference.describe(r.copy(cp = 1501), ref).contains("rank 123"))
        assertFalse(BattleReference.describe(r.copy(cp = 1, attack = 15), ref).contains("rank 123"))
    }
}
