package org.primeval.manager

import kotlin.math.*

data class Target(val x: Int, val y: Int, val width: Int, val height: Int) {
    /** Only a uniform resize and window origin are unambiguous. Refuse cropped buffers. */
    fun inWindow(left: Int, top: Int, w: Int, h: Int): Pair<Int, Int>? {
        if (
            width <= 0 ||
                height <= 0 ||
                w <= 0 ||
                h <= 0 ||
                x !in 0 until width ||
                y !in 0 until height
        )
            return null
        val scale = w.toDouble() / width
        if (abs(height * scale - h) > 1.5) return null
        return (left + (x * scale).toInt()) to (top + (y * scale).toInt())
    }
}

enum class NavKind {
    DETAILS_MENU,
    MENU_APPRAISE,
    INTRO,
    APPRAISAL,
    DETAILS,
    UNKNOWN,
}

data class Scene(
    val kind: NavKind,
    val target: Target? = null,
    val panel: PanelRead? = null,
    val identity: Detail? = null,
    val reason: String = "",
) {
    fun same(other: Scene): Boolean {
        if (kind != other.kind || identity != other.identity) return false
        if (target == null || other.target == null) return target == other.target
        return target.width == other.target.width &&
            target.height == other.target.height &&
            abs(target.x - other.target.x) <= max(2, target.width / 80) &&
            abs(target.y - other.target.y) <= max(2, target.width / 80)
    }
}

object ScanNavigation {
    fun expectedAfter(action: NavKind?, observed: NavKind): Boolean =
        when (action) {
            NavKind.MENU_APPRAISE,
            NavKind.INTRO -> observed != NavKind.DETAILS_MENU
            else -> true
        }

    private fun green(p: Int): Boolean {
        val r = p shr 16 and 255
        val g = p shr 8 and 255
        val b = p and 255
        return g > 145 && g - r > 12 && g >= b - 6 && r > 65
    }

    private fun teal(p: Int): Boolean {
        val r = p shr 16 and 255
        val g = p shr 8 and 255
        val b = p and 255
        return r < 105 && g - r > 40 && b - r > 30
    }

    // Three matched green strokes inside a teal circular button. Search the
    // lower-right region in screenshot space, then verify margins around them.
    fun menuIcon(im: Pixels): Target? {
        val step = max(1, im.width / 520)
        data class Stroke(val x: Int, val y: Int, val length: Int)
        val bands = mutableListOf<MutableList<Stroke>>()
        for (y in im.height * 3 / 5 until im.height step step) {
            var x = im.width * 7 / 10
            while (x < im.width) {
                if (!green(im.rgb(x, y))) {
                    x += step
                    continue
                }
                val left = x
                while (x < im.width && green(im.rgb(x, y))) x += step
                val w = x - left
                if (w !in im.width / 25..im.width / 9) continue
                if (
                    left - w / 6 < 0 ||
                        x + w / 6 >= im.width ||
                        !teal(im.rgb(left - w / 6, y)) ||
                        !teal(im.rgb(x + w / 6, y))
                )
                    continue
                val stroke = Stroke((left + x) / 2, y, w)
                val group =
                    bands.lastOrNull {
                        y - it.last().y <= step && abs(stroke.x - it.last().x) < w / 7
                    }
                if (group == null) bands.add(mutableListOf(stroke)) else group.add(stroke)
            }
        }
        val strokes = bands.map { it[it.size / 2] }
        // Ignore unrelated bands beside the button (for example, a move or power-up row).
        val triples =
            strokes.flatMap { middle ->
                val column =
                    strokes.filter {
                        abs(it.x - middle.x) <= middle.length / 9 &&
                            abs(it.length - middle.length) <= middle.length / 5
                    }
                val i = column.indexOf(middle)
                if (i in 1 until column.lastIndex) listOf(column.subList(i - 1, i + 2))
                else emptyList()
            }
        return triples
            .mapNotNull { (a, b, c) ->
                val gap = b.y - a.y
                if (
                    gap !in b.length / 5..b.length / 2 ||
                        abs(c.y - b.y - gap) > max(step * 2, gap / 5) ||
                        listOf(a, c).any {
                            abs(it.x - b.x) > b.length / 9 ||
                                abs(it.length - b.length) > b.length / 5
                        }
                )
                    return@mapNotNull null
                if (!teal(im.rgb(b.x, (a.y + b.y) / 2)) || !teal(im.rgb(b.x, (b.y + c.y) / 2)))
                    return@mapNotNull null
                // Across the disc, find the first rim/background crossing on multiple
                // rays. A clipped bottom margin or teal game artwork on one side
                // must not invalidate evidence from the remaining circumference.
                val radii =
                    (0..7).mapNotNull ray@{ k ->
                        val angle = k * Math.PI / 4
                        fun sample(distance: Int): Int? {
                            val xx = b.x + (cos(angle) * distance).roundToInt()
                            val yy = b.y + (sin(angle) * distance).roundToInt()
                            return if (xx in 0 until im.width && yy in 0 until im.height)
                                im.rgb(xx, yy)
                            else null
                        }
                        val inner = b.length * 3 / 4
                        if (sample(inner)?.let(::teal) != true) return@ray null
                        (inner + 1..b.length * 8 / 5).firstOrNull { r ->
                            sample(r)?.let { !teal(it) } == true
                        }
                    }
                if (radii.size < 5) return@mapNotNull null
                val median = radii.sorted()[radii.size / 2]
                if (radii.count { abs(it - median) <= max(3, b.length / 6) } < 5)
                    return@mapNotNull null
                Target(b.x, b.y, im.width, im.height)
            }
            .singleOrNull()
    }

    fun inspect(lines: List<Line>, im: Pixels, expected: Detail?, closing: Boolean): Scene {
        val panel = Appraisal.read(lines, im)
        fun point(l: Line) =
            Target((l.left + l.right) / 2, (l.top + l.bottom) / 2, im.width, im.height)
        val dialogue = lines.filter { it.top > im.height * 2 / 3 && it.left < im.width / 4 }
        val text = dialogue.sortedBy { it.top }.joinToString(" ") { it.text }
        val speech = dialogue.firstOrNull { it.text.trim().startsWith("This ", true) }
        if (panel.spread != null) {
            val headerLines =
                lines.filter {
                    it.bottom < panel.rails.first().y && !it.text.trim().equals("Attack", true)
                }
            val identity = Recognition.detail(headerLines)
            if (
                expected == null ||
                    identity == null ||
                    identity.name != expected.name ||
                    identity.cp != expected.cp ||
                    identity.hp != expected.hp ||
                    (identity.visible.form != null &&
                        expected.visible.form != null &&
                        identity.visible.form != expected.visible.form)
            )
                return Scene(
                    NavKind.UNKNOWN,
                    panel = panel,
                    reason = "Appraisal header identity absent or changed",
                )
            return Scene(
                NavKind.APPRAISAL,
                if (Appraisal.species(dialogue) != null) speech?.let(::point) else null,
                panel,
                identity,
            )
        }
        if (
            Appraisal.species(dialogue) != null ||
                lines.count { it.text in listOf("Defense", "Attack", "HP") } >= 2
        )
            return Scene(
                NavKind.UNKNOWN,
                panel = panel,
                reason = panel.error ?: "bar reading unavailable",
            )
        if (
            !closing &&
                expected != null &&
                text.contains(expected.name, true) &&
                listOf("appraise", "take a look", "check out", "analyse", "analyze").any {
                    text.contains(it, true)
                }
        ) {
            return Scene(NavKind.INTRO, dialogue.firstOrNull()?.let(::point))
        }
        if (!closing) {
            val peers =
                lines.filter {
                    it.text.trim().uppercase() in
                        setOf(
                            "TAG",
                            "TRANSFER",
                            "FAVORITE",
                            "ITEMS",
                            "POKÉDEX",
                            "POKEDEX",
                            "CATCH CARD",
                            "POKÉMON HOME",
                        )
                }
            val action =
                lines
                    .filter { it.text.trim().equals("Appraise", true) }
                    .singleOrNull { row ->
                        val aligned = peers.filter { abs(it.right - row.right) < im.width / 10 }
                        val above = aligned.filter { it.bottom < row.top }
                        val below = aligned.filter { it.top > row.bottom }
                        above.isNotEmpty() &&
                            below.isNotEmpty() &&
                            aligned.map { it.text.uppercase() }.distinct().size >= 2 &&
                            aligned.none { it.bottom > row.top && it.top < row.bottom }
                    }
            if (action != null) return Scene(NavKind.MENU_APPRAISE, point(action))
        }
        val detail = Recognition.detail(lines)
        if (detail != null && detail == expected)
            return if (closing) Scene(NavKind.DETAILS, identity = detail)
            else Scene(NavKind.DETAILS_MENU, menuIcon(im), identity = detail)
        return Scene(
            NavKind.UNKNOWN,
            panel = panel,
            reason =
                if (detail != null) "header identity changed"
                else "no verified detail, menu or appraisal",
        )
    }
}
