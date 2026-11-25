package org.primeval.manager

import java.io.Writer
import java.time.Instant
import org.json.JSONObject

object Export {
    private fun fields(r: Specimen, profiles: Map<Long, String>) =
        linkedMapOf<String, Any?>(
            "id" to r.id,
            "profileId" to r.profileId,
            "profile" to profiles[r.profileId],
            "species" to r.species,
            "form" to r.form,
            "nickname" to r.nickname,
            "cp" to r.cp,
            "hp" to r.hp,
            "attack" to r.attack,
            "defense" to r.defense,
            "stamina" to r.stamina,
            "ivPercent" to r.ivPercent,
            "fast" to r.fast,
            "charged" to r.charged,
            "second" to r.second,
            "shiny" to r.shiny,
            "shadow" to r.shadow,
            "purified" to r.purified,
            "lucky" to r.lucky,
            "maxStatus" to r.maxStatus,
            "scannedAt" to Instant.ofEpochMilli(r.scannedAt).toString(),
            "confidence" to r.confidence,
            "verification" to r.verification,
            "notes" to r.notes,
        )

    fun write(out: Writer, records: List<Specimen>, profiles: Map<Long, String>, json: Boolean) {
        fun csv(values: Collection<Any?>) =
            values.joinToString(",") { v ->
                "\"" + (v?.toString().orEmpty()).replace("\"", "\"\"") + "\""
            } + "\r\n"
        if (json) out.write("[")
        else out.write(csv(fields(Specimen(profileId = 0, species = ""), profiles).keys))
        records.forEachIndexed { i, r ->
            val f = fields(r, profiles)
            if (json) {
                if (i > 0) out.write(",")
                val obj = JSONObject()
                f.forEach { (k, v) -> obj.put(k, v ?: JSONObject.NULL) }
                out.write(obj.toString())
            } else out.write(csv(f.values))
        }
        if (json) out.write("]")
    }
}
