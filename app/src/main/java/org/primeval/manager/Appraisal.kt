package org.primeval.manager

import java.util.Locale
import kotlin.math.*

data class Rail(
    val left: Int,
    val y: Int,
    val width: Int,
    val ratio: Double,
    val iv: Int,
    val confidence: Double,
)

data class PanelRead(
    val rails: List<Rail>,
    val error: String?,
    val species: String? = null,
    val rowEvidence: List<Rail?> = rails,
) {
    val spread
        get() =
            if (error == null && rails.size == 3) Spread(rails[0].iv, rails[1].iv, rails[2].iv)
            else null

    fun summary(): String =
        "panel=" +
            (spread != null) +
            " " +
            listOf("Attack", "Defense", "HP")
                .mapIndexed { i, label ->
                    val rail = rowEvidence.getOrNull(i)
                    if (rail == null) "$label=missing"
                    else
                        String.format(
                            Locale.ROOT,
                            "%s[x=%d,y=%d,w=%d,ratio=%.3f,iv=%d,confidence=%.2f]",
                            label,
                            rail.left,
                            rail.y,
                            rail.width,
                            rail.ratio,
                            rail.iv,
                            rail.confidence,
                        )
                }
                .joinToString(" ") +
            " " +
            (error ?: "ok")

    fun agrees(other: PanelRead): Boolean =
        spread != null &&
            spread == other.spread &&
            rails.zip(other.rails).all { (a, b) ->
                val tolerance = max(2, a.width / 70)
                abs(a.left - b.left) <= tolerance &&
                    abs(a.y - b.y) <= tolerance &&
                    abs(a.width - b.width) <= tolerance &&
                    abs(a.ratio - b.ratio) < 0.023 &&
                    min(a.confidence, b.confidence) >= 0.75
            }
}

/**
 * Find the three uniform five-IV segments on the white appraisal card. Measurements use interior
 * scan lines to avoid foreshortened endpoints on the rounded caps.
 */
object Appraisal {
    private val caught =
        Regex("""\bThis\s+([\p{L}♀♂.’' -]{1,30}?)\s+was\s+caught\b""", RegexOption.IGNORE_CASE)

    fun species(lines: List<Line>) =
        caught
            .find(lines.sortedBy { it.top }.joinToString(" ") { it.text })
            ?.groupValues
            ?.get(1)
            ?.trim()

    private fun material(rgb: Int): Int {
        val r = rgb shr 16 and 255
        val g = rgb shr 8 and 255
        val b = rgb and 255
        return when {
            minOf(r, g, b) >= 244 -> 0 // card and separator
            // JPEG chroma bleed from a rose bar can tint its narrow white dividers.
            r >= 248 && g >= 208 && b >= 196 && abs(g - b) <= 18 -> 0
            r in 145..240 && maxOf(r, g, b) - minOf(r, g, b) <= 16 -> 1 // empty rail
            r >= 160 && r - g >= 28 && r - b >= 40 && g >= 55 -> 2 // orange or rose
            else -> -1
        }
    }

    private fun trace(im: Pixels, y: Int): List<Rail> {
        val runs = mutableListOf<IntRange>()
        val limit = minOf(im.width, im.width * 7 / 10)
        val colors = IntArray(limit) { material(im.rgb(it, y)) }
        var x = 0
        while (x < limit) {
            if (colors[x] <= 0) {
                x++
                continue
            }
            val start = x
            while (x < limit && colors[x] > 0) {
                x++
                if (colors[x - 1] != 2 || x >= limit || colors[x] != -1) continue
                var next = x
                while (
                    next < limit && colors[next] == -1 && next - x < max(2, im.width / 140)
                ) next++
                // Keep a blended fill endpoint inside its segment. Measurement below
                // bounds both possible fill lengths including all uncertain pixels.
                if (next < limit && colors[next] == 1) x = next
            }
            if (x - start >= im.width / 30) runs.add(start until x)
        }
        return runs.windowed(3).mapNotNull { segments ->
            val first = segments.first().first
            val last = segments.last().last
            val width = last - first + 1
            if (width !in im.width / 6..im.width / 2 || first < 2 || last + 2 >= limit)
                return@mapNotNull null
            if (colors[first - 2] != 0 || colors[last + 2] != 0) return@mapNotNull null
            if (
                segments.zipWithNext().any { (a, b) ->
                    val gap = b.first - a.last - 1
                    gap !in 1..max(2, width / 28) ||
                        (a.last + 1 until b.first).none { colors[it] == 0 }
                }
            )
                return@mapNotNull null
            measure(colors, segments, 0, y)
        }
    }

    private fun calibratedRow(im: Pixels, left: Int, y: Int, width: Int): Rail? {
        val guard = max(2, width / 90)
        if (left < guard || left + width + guard >= im.width) return null
        val colors = IntArray(width) { material(im.rgb(left + it, y)) }
        if (
            material(im.rgb(left - guard, y)) != 0 || material(im.rgb(left + width + guard, y)) != 0
        )
            return null
        val first = colors.indexOfFirst { it > 0 }
        val last = colors.indexOfLast { it > 0 }
        if (first !in 0..guard || last !in width - 1 - guard until width) return null
        fun divider(center: Int): IntRange? {
            val white = (center - guard..center + guard).filter { colors[it] == 0 }
            if (white.isEmpty() || white.size != white.last() - white.first() + 1) return null
            var l = white.first()
            var r = white.last()
            while (l > first && colors[l - 1] <= 0) l--
            while (r < last && colors[r + 1] <= 0) r++
            return (l..r).takeIf { r - l + 1 <= max(3, width / 28) }
        }
        val one = divider(width / 3) ?: return null
        val two = divider(width * 2 / 3) ?: return null
        val segments =
            listOf(first until one.first, one.last + 1 until two.first, two.last + 1..last)
        return measure(colors, segments, left, y)
    }

    private fun measure(colors: IntArray, segments: List<IntRange>, origin: Int, y: Int): Rail? {
        val first = segments.first().first
        val width = segments.last().last - first + 1
        val lengths = segments.map { it.last - it.first + 1 }
        if (lengths.min() * 1.16 < lengths.max()) return null
        var filled = 0
        var empty = 0
        var uncertain = 0
        // Material must progress from fill through a narrow transition to empty.
        for (segment in segments) for (x in segment) {
            when (colors[x]) {
                2 -> if (empty == 0 && uncertain == 0) filled++ else return null
                1 -> empty++
                else -> if (empty == 0) uncertain++ else return null
            }
        }
        if (uncertain > max(2, width / 65)) return null
        val count = lengths.sum()
        val low = 15.0 * filled / count
        val high = 15.0 * (filled + uncertain) / count
        val iv = low.roundToInt()
        val confidence = 1 - max(abs(low - iv), abs(high - iv))
        if (iv != high.roundToInt() || confidence < 0.75) return null
        for ((index, range) in segments.withIndex()) for (k in 0..4) {
            val center = range.first + ((k + 0.5) * lengths[index] / 5).toInt()
            val expected = if (index * 5 + k < iv) 2 else 1
            if (colors[center] != expected) return null
        }
        return Rail(origin + first, y, width, (low + high) / 30, iv, confidence)
    }

    // When OCR misses the appraisal text, require the same two visible card edges
    // on every stat row. Isolated bars on an unbounded white screen are ambiguous.
    private fun boundedCard(im: Pixels, panel: List<Rail>): Boolean {
        fun edge(row: Rail, direction: Int): Int? {
            val cap = if (direction < 0) row.left else row.left + row.width - 1
            val start = max(3, row.width / 32)
            fun white(x: Int): Boolean {
                val c = im.rgb(x, row.y)
                return minOf(c shr 16 and 255, c shr 8 and 255, c and 255) >= 240
            }
            for (offset in start..row.width / 4) {
                val x = cap + direction * offset
                val outside = x + direction * 3
                if (x !in 0 until im.width || outside !in 0 until im.width) return null
                if (!white(x)) return x.takeIf { offset > start && !white(outside) }
            }
            return null
        }
        return listOf(-1, 1).all { direction ->
            val edges = panel.map { edge(it, direction) ?: return false }
            edges.max() - edges.min() <= max(2, panel.first().width / 40)
        }
    }

    private val labels = listOf("Attack", "Defense", "HP")

    fun read(lines: List<Line>, im: Pixels): PanelRead {
        val species = species(lines)
        val anchors =
            labels.map { label ->
                lines.filter { it.text.trim().equals(label, true) && it.left < im.width / 2 }
            }
        val all = anchors.flatten()
        val needsCardEdges = anchors.count { it.isNotEmpty() } < 2 && species == null
        val start =
            if (all.isEmpty() || needsCardEdges) im.height / 2
            else max(0, all.minOf { it.top } - im.width / 6)
        val end =
            if (all.isEmpty() || needsCardEdges) im.height
            else min(im.height, all.maxOf { it.bottom } + im.width / 4)
        val step = max(1, im.width / 500)
        val groups = mutableListOf<MutableList<Rail>>()
        for (y in start until end step step) for (row in trace(im, y)) {
            val group =
                groups.lastOrNull { g ->
                    y - g.last().y in 1..2 * step &&
                        abs(row.width - g.last().width) <= max(4, row.width / 24) &&
                        abs(row.left - g.last().left) <= max(3, row.width / 25)
                }
            if (group == null) groups.add(mutableListOf(row)) else group.add(row)
        }
        val rows =
            groups.mapNotNull { group ->
                if (group.size < 2) return@mapNotNull null
                val center = group[group.size / 2]
                val middle =
                    group.filter {
                        abs(it.y - center.y) <= max(step, (group.last().y - group.first().y) / 4)
                    }
                center.takeIf { middle.all { it.iv == center.iv } }
            }
        // A blended pixel at the rounded fill endpoint can split one physical
        // segment into two colour runs. Locate that row using its two peers,
        // then require its own caps, dividers, fill extent and 15 cell centres.
        fun anchored(row: Rail, label: Int) =
            anchors[label].any { l ->
                abs(l.left - row.left) < row.width / 8 && row.y - l.bottom in 0..row.width / 5
            }
        val byLabel =
            labels.indices
                .map { i -> rows.filter { anchored(it, i) }.singleOrNull() }
                .toMutableList()
        var repairReason = ""
        if (byLabel[0] == null && byLabel[1] != null && byLabel[2] != null) {
            val d = byLabel[1]!!
            val h = byLabel[2]!!
            val pitch = h.y - d.y
            if (
                pitch in d.width / 6..d.width / 2 &&
                    abs(d.left - h.left) <= max(2, d.width / 50) &&
                    abs(d.width - h.width) <= max(2, d.width / 50)
            ) {
                val expectedY = d.y - pitch
                val candidates =
                    (maxOf(0, expectedY - pitch / 7)..minOf(im.height - 1, expectedY + pitch / 7))
                        .mapNotNull { y -> calibratedRow(im, d.left, y, d.width) }
                if (
                    candidates.size >= maxOf(3, step) &&
                        candidates.all { it.iv == candidates.first().iv } &&
                        candidates.zipWithNext().all { (a, b) -> b.y == a.y + 1 }
                ) {
                    val measured = candidates[candidates.size / 2]
                    if (anchors[0].isEmpty() || anchored(measured, 0)) byLabel[0] = measured
                }
                repairReason =
                    "; Attack calibrated scanlines=${candidates.size}, distinct IVs=${candidates.map{it.iv}.distinct().size}"
            }
        }
        val measuredRows = (rows + byLabel.filterNotNull()).distinct()
        val panels = mutableListOf<List<Rail>>()
        var unbounded = 0
        for (a in measuredRows) for (b in
            measuredRows.filter { it.y - a.y in a.width / 6..a.width / 2 }) for (c in
            measuredRows.filter { it.y > b.y }) {
            val gap = b.y - a.y
            val ordered = listOf(a, b, c)
            if (abs(c.y - b.y - gap) > max(step * 2, gap / 9)) continue
            if (
                ordered.any {
                    abs(it.left - a.left) > max(3, a.width / 30) ||
                        abs(it.width - a.width) > max(4, a.width / 24)
                }
            )
                continue
            if (
                anchors.indices.any { i ->
                    anchors[i].isNotEmpty() &&
                        anchors[i].none { l ->
                            abs(l.left - ordered[i].left) < a.width / 8 &&
                                ordered[i].y - l.bottom in -gap / 8..gap * 2 / 3
                        }
                }
            )
                continue
            if (needsCardEdges && !boundedCard(im, ordered)) {
                unbounded++
                continue
            }
            panels.add(ordered)
        }
        val panel =
            panels.singleOrNull()
                ?: return PanelRead(
                    emptyList(),
                    "aligned card count=${panels.size}, candidate rails=${rows.size}, rejected card edges=$unbounded$repairReason",
                    rowEvidence = byLabel,
                )
        // A textual rating is used only alongside the card. Badge art alone is ambiguous.
        val stars =
            lines
                .filter {
                    it.bottom <= panel[0].y &&
                        panel[0].y - it.bottom < panel[0].width &&
                        it.left < panel[0].left + panel[0].width
                }
                .mapNotNull {
                    Regex("""^([0-4])[- ]stars?$""", RegexOption.IGNORE_CASE)
                        .matchEntire(it.text.trim())
                        ?.groupValues
                        ?.get(1)
                        ?.toInt()
                }
                .singleOrNull()
        val total = panel.sumOf { it.iv }
        val category =
            when (total) {
                in 0..22 -> 0
                in 23..29 -> 1
                in 30..36 -> 2
                in 37..44 -> 3
                else -> 4
            }
        return PanelRead(
            panel,
            if (stars != null && stars != category) "displayed rating conflicts with bars"
            else null,
            species,
        )
    }
}
