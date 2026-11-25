package org.primeval.manager

import org.json.JSONObject

/** Optional curated assessments. Rank is supplied for one exact IV spread, species and form. */
data class BattleEntry(
    val species: String,
    val form: String,
    val source: String,
    val version: String,
    val pve: String,
    val basis: String,
    val league: String?,
    val cap: Int?,
    val spread: Spread?,
    val rank: Int?,
)

object BattleReference {
    fun parse(doc: JSONObject): List<BattleEntry> {
        val rows = doc.optJSONArray("battle") ?: return emptyList()
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val pvp = row.optJSONObject("pvp")
            val spread =
                pvp?.let { Spread(it.getInt("attack"), it.getInt("defense"), it.getInt("stamina")) }
            if (spread != null) require(ivs(spread.attack, spread.defense, spread.stamina) != null)
            BattleEntry(
                    row.getString("species"),
                    row.getString("form"),
                    doc.getString("source"),
                    doc.getString("version"),
                    row.getString("pve"),
                    row.getString("basis"),
                    pvp?.getString("league"),
                    pvp?.getInt("cap"),
                    spread,
                    pvp?.getInt("rank"),
                )
                .also {
                    require(it.species.isNotBlank() && it.basis.isNotBlank() && it.pve.isNotBlank())
                    if (pvp != null)
                        require(it.cap!! > 0 && it.rank in 1..4096 && !it.league.isNullOrBlank())
                }
        }
    }

    fun describe(p: Specimen, entries: List<BattleEntry>): String {
        val matches =
            entries.filter { p.species.equals(it.species, true) && p.form.equals(it.form, true) }
        val pve =
            matches
                .map { "${it.pve} · ${it.basis} [${it.version}, ${it.source}]" }
                .distinct()
                .singleOrNull() ?: "Unknown"
        val pvp =
            matches
                .filter {
                    it.spread != null &&
                        it.spread.attack == p.attack &&
                        it.spread.defense == p.defense &&
                        it.spread.stamina == p.stamina &&
                        p.cp != null &&
                        p.cp <= it.cap!!
                }
                .groupBy { it.league }
                .mapNotNull { (_, rows) ->
                    rows.distinct().singleOrNull()?.let {
                        "${it.league} IV rank ${it.rank} [${it.version}, ${it.source}]"
                    }
                }
                .joinToString()
                .ifBlank { "Unknown" }
        return "PvE: $pve\nPvP: $pvp"
    }
}
