package org.primeval.manager

import org.json.JSONObject

enum class Priority {
    MUST_DO,
    RECOMMENDED,
    OPTIONAL,
    SKIP,
    UNKNOWN,
}

data class Rule(
    val category: String,
    val version: String,
    val source: String,
    val species: String,
    val form: String,
    val start: Long,
    val end: Long,
    val move: String,
    val slot: String,
    val evolvesTo: String,
    val rank: Priority,
    val basis: String,
)

data class Advice(val priority: Priority, val reason: String)

object Advisor {
    fun parse(json: String): List<Rule> {
        val doc = JSONObject(json)
        BattleReference.parse(doc)
        val version = doc.getString("version")
        val source = doc.getString("source")
        require(version.isNotBlank() && source.isNotBlank())
        val array = doc.getJSONArray("rules")
        return (0 until array.length()).map { i ->
            val r = array.getJSONObject(i)
            Rule(
                    r.getString("category"),
                    version,
                    source,
                    r.getString("species"),
                    r.getString("form"),
                    java.time.Instant.parse(r.getString("start")).toEpochMilli(),
                    java.time.Instant.parse(r.getString("end")).toEpochMilli(),
                    r.optString("move"),
                    r.optString("slot"),
                    r.optString("evolvesTo"),
                    Priority.valueOf(r.getString("priority").replace(' ', '_')),
                    r.getString("basis"),
                )
                .also {
                    require(
                        it.start < it.end &&
                            it.species.isNotBlank() &&
                            it.basis.isNotBlank() &&
                            it.category in listOf("Frustration", "Community Day")
                    )
                    if (it.category == "Community Day")
                        require(
                            it.slot in listOf("Fast", "Charged") &&
                                it.move.isNotBlank() &&
                                it.evolvesTo.isNotBlank()
                        )
                }
        }
    }

    fun assess(p: Specimen, category: String, rules: List<Rule>, at: Long): Advice {
        val frustrated = listOf(p.charged, p.second).any { it.equals("Frustration", true) }
        if (category == "Frustration") {
            if (p.shadow == false) return Advice(Priority.SKIP, "Recorded as non-Shadow.")
            if (p.shadow != true) return Advice(Priority.UNKNOWN, "Shadow status needs review.")
            if (!frustrated)
                return if (p.charged.isBlank() || p.notes.contains("unconfirmed"))
                    Advice(Priority.UNKNOWN, "Charged slots need review.")
                else Advice(Priority.SKIP, "Frustration is absent from reviewed moves.")
        }
        val applicable =
            rules.filter {
                it.category == category &&
                    p.species.equals(it.species, true) &&
                    (it.form == "*" || p.form.equals(it.form, true)) &&
                    at >= it.start &&
                    at < it.end
            }
        val rule =
            applicable.singleOrNull()
                ?: return Advice(
                    Priority.UNKNOWN,
                    if (applicable.size > 1) "Conflicting reference rules."
                    else "No active reference for this species and form.",
                )
        val ref = "[${rule.version} · ${rule.source}]"
        if (category == "Community Day" && rule.slot == "Charged") {
            if (frustrated)
                return Advice(
                    Priority.SKIP,
                    "Frustration blocks the exclusive Charged Attack on evolution. $ref",
                )
            if (p.shadow != false && p.charged.isBlank())
                return Advice(Priority.UNKNOWN, "Check Frustration first. $ref")
        }
        val action =
            if (category == "Community Day")
                "Evolve into ${rule.evolvesTo} for ${rule.move} (${rule.slot})."
            else "Remove Frustration during the listed window."
        return Advice(rule.rank, "$action ${rule.basis} $ref")
    }
}
