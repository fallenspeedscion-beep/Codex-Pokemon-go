package org.primeval.manager

/**
 * A regular table must start at its battle tabs. Subsequent viewports must repeat a suffix of the
 * observed rows before they can contribute another slot.
 */
data class MoveViewport(
    val header: Boolean,
    val rows: List<String>,
    val end: Boolean,
    val beforeMax: Boolean,
) {
    fun extend(previous: List<String>): List<String>? {
        if (header) return rows.takeIf { previous.isEmpty() || it.take(previous.size) == previous }
        if (previous.isEmpty() || rows.isEmpty() || beforeMax) return null
        val overlap =
            (1..minOf(previous.size, rows.size)).lastOrNull {
                previous.takeLast(it) == rows.take(it)
            } ?: return null
        return (previous + rows.drop(overlap)).takeIf {
            it.size <= 3 && it.distinct().size == it.size
        }
    }

    companion object {
        private val damage = Regex("""^([A-Za-z][A-Za-z '’-]{1,32}?)\s+(\d{1,3})$""")
        private val name = Regex("""[A-Za-z][A-Za-z '’-]{1,32}""")

        fun read(lines: List<Line>): MoveViewport {
            val sorted = lines.sortedBy { it.top }
            val tab =
                sorted.indexOfLast {
                    it.text.contains("Trainer Battles", true) ||
                        it.text.contains("Gyms & Raids", true)
                }
            val after = sorted.drop(tab + 1)
            val end =
                after.indexOfFirst {
                    it.text.equals("New Attack", true) ||
                        it.text.contains("Max Move", true) ||
                        it.text.equals("Max Battles", true)
                }
            val body = if (end < 0) after else after.take(end)
            val rows =
                body.mapNotNull { line ->
                    damage.matchEntire(line.text.trim())?.groupValues?.get(1)
                        ?: line.text.trim().takeIf {
                            name.matches(it) &&
                                body.count { n ->
                                    n.text.trim().matches(Regex("""\d{1,3}""")) &&
                                        n.left > line.right &&
                                        kotlin.math.abs(
                                            (n.top + n.bottom) - (line.top + line.bottom)
                                        ) < line.bottom - line.top
                                } == 1
                        }
                }
            return MoveViewport(tab >= 0, rows, end >= 0, tab < 0 && body.isEmpty())
        }
    }
}
