package org.primeval.manager

import kotlin.math.*

data class Line(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

interface Pixels {
    val width: Int
    val height: Int

    fun rgb(x: Int, y: Int): Int
}

data class Visible(
    val species: String? = null,
    val form: String? = null,
    val shiny: Boolean? = null,
    val shadow: Boolean? = null,
    val purified: Boolean? = null,
    val lucky: Boolean? = null,
    val maxStatus: String = "",
)

data class Detail(val name: String, val cp: Int, val hp: Int, val visible: Visible = Visible())

data class Spread(val attack: Int, val defense: Int, val stamina: Int)

data class Attacks(
    val fast: String,
    val charged: String,
    val second: String?,
    val complete: Boolean,
)

object Recognition {
    private val cp = Regex("""^CP\s*(\d+)$""", RegexOption.IGNORE_CASE)
    private val hp = Regex("""^(\d+)\s*/\s*(\d+)\s*HP$""", RegexOption.IGNORE_CASE)

    fun detail(lines: List<Line>): Detail? {
        if (
            lines.any { it.text.equals("Defense", true) } &&
                lines.any { it.text.equals("Attack", true) }
        )
            return null
        val power =
            lines
                .mapNotNull { cp.matchEntire(it.text.trim())?.groupValues?.get(1)?.toIntOrNull() }
                .singleOrNull() ?: return null
        if (power <= 0) return null
        val health = lines.singleOrNull { hp.matches(it.text.trim()) } ?: return null
        val hpMatch = hp.matchEntire(health.text.trim()) ?: return null
        val current = hpMatch.groupValues[1].toIntOrNull() ?: return null
        val maximum = hpMatch.groupValues[2].toIntOrNull() ?: return null
        if (maximum <= 0 || current > maximum) return null
        val names =
            lines.filter {
                it.bottom <= health.top &&
                    it.text.matches(Regex("""[\p{L}][\p{L}\p{M}\p{N} .’'♀♂%★✦-]{0,23}""")) &&
                    !cp.matches(it.text.trim()) &&
                    it.right > health.left &&
                    it.left < health.right
            }
        val name = names.maxByOrNull { it.bottom }?.text ?: return null
        val words = lines.map { it.text.trim() }
        fun after(label: String) =
            words
                .singleOrNull { it.startsWith("$label:", true) }
                ?.substringAfter(':')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        fun flag(label: String) =
            true.takeIf {
                words.any { word ->
                    word.equals(label, true) || word.equals("$label Pokémon", true)
                }
            }
        val visible =
            Visible(
                after("Species"),
                after("Form"),
                flag("Shiny"),
                flag("Shadow"),
                flag("Purified"),
                flag("Lucky"),
                listOf("Dynamax", "Gigantamax").singleOrNull { flag(it) == true }.orEmpty(),
            )
        return Detail(name, power, maximum, visible)
    }

    fun bars(lines: List<Line>, image: Pixels) = Appraisal.read(lines, image).spread

    // Only accept labelled slots. Layouts lacking labels stay pending for human review.
    fun moves(lines: List<Line>): Attacks? {
        val regular = lines.sortedBy { it.top }.takeWhile { !it.text.contains("Max Move", true) }
        fun slot(label: String): String? {
            val index = regular.indexOfFirst { it.text.trim().equals(label, true) }
            return regular.getOrNull(index + 1)?.text?.takeIf {
                index >= 0 &&
                    it.matches(Regex("""[A-Za-z][A-Za-z '’-]{1,32}""")) &&
                    !it.contains("Attack", true)
            }
        }
        val fast = slot("Fast Attack") ?: return moveTable(lines)
        val charged = slot("Charged Attack") ?: slot("Charged Attack 1") ?: return null
        val second = slot("Charged Attack 2")
        return Attacks(
            fast,
            charged,
            second,
            second != null || regular.any { it.text.equals("New Attack", true) },
        )
    }

    // Attack names sit to the left of damage in the battle tab. Keep uncertain
    // slot counts open until the lower boundary of the regular table is visible.
    private fun moveTable(lines: List<Line>): Attacks? {
        val page = MoveViewport.read(lines)
        val names = page.extend(emptyList()) ?: return null
        if (names.size !in 2..3 || names.distinct().size != names.size) return null
        return Attacks(names[0], names[1], names.getOrNull(2), names.size == 3 || page.end)
    }
}

enum class Phase {
    DETAILS,
    APPRAISAL,
    MOVES,
    REVIEW,
    NEXT,
    PAUSED,
    STOPPED,
}

class Session(val scanMoves: Boolean) {
    var phase = Phase.DETAILS
        private set

    var failures = 0
        private set

    var details: Detail? = null
        private set

    var spread: Spread? = null
        private set

    var attacks: Attacks? = null
        private set

    private val visited = mutableSetOf<Triple<String, Int, Int>>()

    private fun key(detail: Detail) = Triple(detail.name, detail.cp, detail.hp)

    var reason = ""
        private set

    private var retryAppraisal = false
    private var appraisalMilestone = 0

    // Give a newly verified screen its own observations. Flicker between two
    // already visited screens cannot refill the retry budget.
    fun confirmAppraisalStage(stage: Int) {
        if (phase == Phase.APPRAISAL && stage in 1..3 && stage > appraisalMilestone) {
            appraisalMilestone = stage
            failures = 0
        }
    }

    fun fail(message: String) {
        reason = message
        if (++failures >= 3) {
            val retain = phase == Phase.APPRAISAL
            pause(message)
            retryAppraisal = retain
        }
    }

    fun pause(message: String) {
        retryAppraisal = false
        reason = message
        phase = Phase.PAUSED
    }

    fun stop() {
        phase = Phase.STOPPED
    }

    fun resume() {
        if (phase == Phase.PAUSED) {
            if (retryAppraisal) {
                phase = Phase.APPRAISAL
                failures = 0
                retryAppraisal = false
                return
            }
            details = null
            spread = null
            attacks = null
            phase = Phase.DETAILS
            failures = 0
        }
    }

    fun detail(value: Detail) {
        if (phase !in listOf(Phase.DETAILS, Phase.NEXT)) return
        if (key(value) in visited) {
            fail("Same or indistinguishable Pokémon. End the session or check the next page.")
            return
        }
        appraisalMilestone = 0
        details = value
        spread = null
        attacks = null
        phase = Phase.APPRAISAL
        failures = 0
    }

    fun appraise(value: Spread) {
        if (phase != Phase.APPRAISAL) return
        spread = value
        phase = if (scanMoves) Phase.MOVES else Phase.REVIEW
        failures = 0
    }

    fun moves(value: Attacks) {
        if (phase == Phase.MOVES) {
            attacks = value
            phase = Phase.REVIEW
            failures = 0
        }
    }

    fun keepUncertainBars() {
        if (phase == Phase.APPRAISAL) {
            spread = null
            phase = if (scanMoves) Phase.MOVES else Phase.REVIEW
            failures = 0
        }
    }

    fun keepUncertainMoves() {
        if (phase == Phase.MOVES) {
            phase = Phase.REVIEW
            failures = 0
        }
    }

    fun detailAfterRename(value: Detail) {
        if (phase == Phase.NEXT) visited.add(key(value))
    }

    fun saved() {
        if (phase == Phase.REVIEW) {
            details?.let { visited.add(key(it)) }
            phase = Phase.NEXT
            failures = 0
        }
    }
}

fun <T> consensus(first: T?, second: T?): T? = first?.takeIf { it == second }
