package org.primeval.manager

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.AlertDialog
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.view.accessibility.*
import android.widget.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

class GameScanner : AccessibilityService() {
    companion object {
        const val game = "com.nianticlabs.pokemongo"
        private var reference = WeakReference<GameScanner>(null)
        val active: GameScanner?
            get() = reference.get()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val reader by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var panel: LinearLayout? = null
    private lateinit var title: TextView
    private lateinit var read: Button
    private lateinit var pause: Button
    private var session = Session(false)
    private var profile = 0L
    private var mode = ""
    private var profileName = ""
    private var generation = 0
    private var busy = false
    private var scrolls = 0
    private var lastScroll = ""
    private var proposed = ""
    private var approvedSpecies: String? = null
    private var customOverride = false
    private var storedId = 0L
    private var review: AlertDialog? = null
    private lateinit var option: Button

    private var advancing = false
    private var nextRequested = false
    private var follow = false
    private var diagnostics = false
    private var observedRows = emptyList<String>()
    private var lastReturn = ""
    private var closing = false
    private var appraisalSpecies: String? = null
    private var draftStored = false
    private var insertionPending = false
    private val actionCounts = mutableMapOf<NavKind, Int>()
    private var returnScrolls = 0

    private fun trace(msg: String) {
        if (diagnostics)
            log(
                "PPM2 state=${session.phase} foreground=${if(window()!=null)"game" else "other"} retry=${session.failures} scroll=$scrolls/$returnScrolls $msg"
            )
    }

    private var original: Detail? = null

    private enum class Rename {
        NONE,
        VERIFY,
        EDIT,
        CHECK,
    }

    private var rename = Rename.NONE

    override fun onServiceConnected() {
        reference = WeakReference(this)
    }

    override fun onInterrupt() {
        suspendScan("Service interrupted")
    }

    override fun onDestroy() {
        finishSession()
        reference.clear()
        reader.close()
        io.shutdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (
            panel != null &&
                !startedReading &&
                follow &&
                !busy &&
                session.phase == Phase.DETAILS &&
                window() != null
        )
            scheduleRead()
        if (
            review == null &&
                panel != null &&
                event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                rootInActiveWindow?.packageName?.toString() != game &&
                startedReading
        ) {
            if (rootInActiveWindow?.packageName != null) {
                suspendScan("Foreground changed. Recheck the current Pokémon.")
            } else {
                // The old window can vanish before the game publishes its replacement.
                // Every capture and gesture still needs a live, active game window.
                val token = generation
                handler.postDelayed(
                    {
                        if (
                            token == generation &&
                                window() == null &&
                                panel != null &&
                                review == null
                        )
                            suspendScan("Game window unavailable")
                    },
                    300,
                )
            }
        }
    }

    private var startedReading = false

    private fun log(message: String) {
        val entry = ScanEntry(profileId = profile, message = message)
        io.execute {
            runCatching { Store.open(this).catalog().log(entry) }
                .onFailure {
                    handler.post {
                        android.widget.Toast.makeText(
                                this,
                                "Could not write scanner history",
                                Toast.LENGTH_LONG,
                            )
                            .show()
                    }
                }
        }
    }

    private fun suspendScan(why: String) {
        if (panel == null || session.phase == Phase.PAUSED) return
        trace("paused=$why")
        log(why)
        generation++
        review?.dismiss()
        review = null
        approvedSpecies = null
        busy = false
        rename = Rename.NONE
        advancing = false
        session.pause(why)
        show(why)
    }

    fun finishSession() {
        if (panel != null) log("Stopped at ${session.phase}")
        generation++
        review?.dismiss()
        review = null
        approvedSpecies = null
        busy = false
        advancing = false
        session.stop()
        rename = Rename.NONE
        panel?.let { getSystemService(WindowManager::class.java).removeView(it) }
        panel = null
        startedReading = false
    }

    fun start(
        id: Long,
        name: String,
        selectedMode: String,
        moves: Boolean,
        followLabels: Boolean,
        diagnostic: Boolean = false,
    ) {
        finishSession()
        diagnostics = diagnostic
        closing = false
        draftStored = false
        insertionPending = false
        follow = followLabels
        profile = id
        profileName = name
        mode = selectedMode
        session = Session(moves)
        log("Started $mode")
        scrolls = 0
        lastScroll = ""
        val box =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10, 4, 10, 4)
                setBackgroundColor(0xef163530.toInt())
            }
        title =
            TextView(this).apply {
                setTextColor(-1)
                textSize = 14f
            }
        box.addView(title)
        val row = LinearLayout(this)
        box.addView(row)
        fun button(label: String, action: () -> Unit) =
            Button(this).apply {
                text = label
                setOnClickListener { action() }
                row.addView(this)
            }
        read = button("Read") { captureStep() }
        pause =
            button("Pause") {
                if (session.phase == Phase.PAUSED) {
                    generation++
                    session.resume()
                    closing = false
                    actionCounts.clear()
                    show()
                    if (follow) scheduleRead()
                } else suspendScan("Paused by user")
            }
        button("Stop") { finishSession() }
        option =
            Button(this).apply {
                isAllCaps = false
                box.addView(this)
                setOnClickListener {
                    if (session.phase == Phase.PAUSED) {
                        savePartial()
                        return@setOnClickListener
                    }
                    if (session.phase == Phase.APPRAISAL) {
                        session.keepUncertainBars()
                        show("IVs remain unknown")
                    } else if (session.phase == Phase.MOVES) {
                        session.keepUncertainMoves()
                        show("Uncertain move evidence will be saved")
                    } else if (
                        session.phase == Phase.NEXT && rename == Rename.NONE && !nextRequested
                    ) {
                        requestNext()
                    }
                }
            }
        val params =
            WindowManager.LayoutParams(
                (resources.displayMetrics.widthPixels * .94).toInt(),
                -2,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        getSystemService(WindowManager::class.java).addView(box, params)
        panel = box
        show()
        if (follow) scheduleRead()
    }

    private fun show(message: String = "") {
        val prompt =
            when {
                rename == Rename.VERIFY ->
                    "Return to details. Apply “$proposed” to the verified Pokémon?"
                rename == Rename.EDIT -> "Read the labelled name editor."
                rename == Rename.CHECK -> "Read details to verify the saved name."
                else ->
                    when (session.phase) {
                        Phase.DETAILS -> "Open first details in #Appraise, then Read."
                        Phase.APPRAISAL -> "Open appraisal for this same Pokémon, then Read."
                        Phase.MOVES -> "Close appraisal and show regular attacks, then Read."
                        Phase.REVIEW ->
                            "Return to the same details. Review species, appraisal and moves before saving."
                        Phase.NEXT -> "Open the next filtered Pokémon and Read, or Stop at the end."
                        Phase.PAUSED ->
                            "Check the screen, then Resume or save an incomplete header."
                        Phase.STOPPED -> "Stopped"
                    }
            }
        title.text = "$profileName · $mode\n$prompt\n$message".trimEnd()
        read.text =
            when {
                rename == Rename.VERIFY -> "Apply"
                session.phase == Phase.REVIEW -> "Review"
                else -> "Read"
            }
        option.visibility =
            if (
                !busy &&
                    rename == Rename.NONE &&
                    ((session.phase == Phase.PAUSED &&
                        session.details != null &&
                        !draftStored &&
                        !insertionPending) ||
                        session.phase in listOf(Phase.APPRAISAL, Phase.MOVES) &&
                            session.failures > 0 ||
                        session.phase == Phase.NEXT && !nextRequested)
            )
                View.VISIBLE
            else View.GONE
        option.text =
            if (session.phase == Phase.PAUSED) "Save incomplete header and stop"
            else if (session.phase == Phase.NEXT) "Advance in filtered group"
            else if (session.phase == Phase.APPRAISAL) "Keep IVs as uncertain"
            else "Keep move reading as uncertain"
        read.isEnabled = !busy && session.phase != Phase.PAUSED
        pause.text = if (session.phase == Phase.PAUSED) "Resume" else "Pause"
    }

    private fun reject(message: String) {
        advancing = false
        log("${session.phase}: $message")
        busy = false
        trace("failure=$message")
        session.fail(message)
        if (session.phase == Phase.PAUSED) rename = Rename.NONE
        show(message)
    }

    private fun window() = rootInActiveWindow?.takeIf { it.packageName?.toString() == game }

    private fun captureStep() {
        if (busy) return
        if (window() == null) {
            reject("Pokémon GO must be in the foreground")
            return
        }
        startedReading = true
        busy = true
        show("Comparing two observations…")
        val token = ++generation
        handler.postDelayed(
            {
                if (token == generation && busy) {
                    generation++
                    suspendScan("Observation timed out. Check the current page.")
                }
            },
            20000,
        )
        if (rename == Rename.EDIT) {
            editName()
            return
        }
        frame(token) { first, firstLines ->
            handler.postDelayed(
                {
                    if (token == generation)
                        frame(token) secondRead@{ second, lines ->
                            busy = false
                            if (first is Scene && second is Scene) {
                                observeScene(first, second, token)
                                return@secondRead
                            }
                            if (
                                session.phase == Phase.MOVES &&
                                    !closing &&
                                    first == second &&
                                    firstLines.map { it.text } == lines.map { it.text }
                            ) {
                                val page = MoveViewport.read(lines)
                                val merged = page.extend(observedRows)
                                if (
                                    merged != null &&
                                        merged.size <= 3 &&
                                        merged.distinct().size == merged.size &&
                                        merged.isNotEmpty()
                                ) {
                                    observedRows = merged
                                    if (merged.size == 3 || (merged.size == 2 && page.end)) {
                                        accept(
                                            Attacks(merged[0], merged[1], merged.getOrNull(2), true)
                                        )
                                        return@secondRead
                                    }
                                    tryScroll(lines)
                                    return@secondRead
                                }
                                if (observedRows.isNotEmpty()) {
                                    reject("Regular move rows lost overlap after scroll")
                                    return@secondRead
                                }
                            }
                            val value = consensus(first, second)
                            if (value == null) {
                                if (
                                    session.phase == Phase.MOVES &&
                                        first == null &&
                                        second == null &&
                                        firstLines.map { it.text } == lines.map { it.text }
                                )
                                    tryScroll(lines)
                                else if (
                                    session.phase == Phase.REVIEW &&
                                        follow &&
                                        first == null &&
                                        second == null &&
                                        returnScrolls < 5 &&
                                        verifiedReturn(firstLines, lines)
                                ) {
                                    val scroll = nodes().singleOrNull { it.isScrollable }
                                    if (
                                        scroll?.performAction(
                                            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                                        ) == true
                                    ) {
                                        returnScrolls++
                                        scheduleRead()
                                        show("Returning to detail header")
                                    } else if (swipePage(horizontal = false, back = true)) {
                                        returnScrolls++
                                    } else reject("Return to the top of the details page")
                                } else reject("Uncertain or disagreeing observations")
                            } else accept(value)
                        }
                },
                650,
            )
        }
    }

    private fun frame(token: Int, done: (Any?, List<Line>) -> Unit) {
        if (token != generation) return
        val root = window()
        if (root == null || Build.VERSION.SDK_INT < 34) {
            reject("Window capture requires Android 14+ and the game foreground")
            return
        }
        try {
            takeScreenshotOfWindow(
                root.windowId,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onFailure(code: Int) {
                        if (token == generation) {
                            trace("capture=failed code=$code")
                            reject("Capture failed ($code)")
                        }
                    }

                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val bmp =
                            try {
                                if (token == generation)
                                    Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                        ?.copy(Bitmap.Config.ARGB_8888, false)
                                else null
                            } finally {
                                buffer.close()
                            }
                        if (token != generation) return
                        if (bmp == null) {
                            reject("Unreadable frame")
                            return
                        }
                        reader.process(InputImage.fromBitmap(bmp, 0)).addOnCompleteListener { task
                            ->
                            try {
                                if (token != generation) return@addOnCompleteListener
                                if (!task.isSuccessful || window()?.windowId != root.windowId) {
                                    reject("Game window or text unavailable")
                                    return@addOnCompleteListener
                                }
                                val lines =
                                    task.result.textBlocks
                                        .flatMap { it.lines }
                                        .mapNotNull { line ->
                                            line.boundingBox?.let {
                                                Line(
                                                    line.text,
                                                    it.left,
                                                    it.top,
                                                    it.right,
                                                    it.bottom,
                                                )
                                            }
                                        }
                                trace("capture=ok")
                                val value: Any? =
                                    when {
                                        session.phase == Phase.APPRAISAL || closing ->
                                            ScanNavigation.inspect(
                                                lines,
                                                BitmapPixels(bmp),
                                                session.details,
                                                closing,
                                            )
                                        session.phase == Phase.MOVES -> Recognition.moves(lines)
                                        else -> Recognition.detail(lines)
                                    }
                                if (session.phase == Phase.MOVES)
                                    trace(
                                        "ordinaryMoves=${if(value==null)"not located" else "detected"}"
                                    )
                                done(value, lines)
                            } catch (e: RuntimeException) {
                                suspendScan("Observation failed: ${e.javaClass.simpleName}")
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                },
            )
        } catch (e: RuntimeException) {
            suspendScan("Window capture unavailable: ${e.javaClass.simpleName}")
        }
    }

    private fun retryScene(reason: String) {
        reject(reason)
        if (follow && session.phase != Phase.PAUSED) scheduleRead()
    }

    private fun observeScene(first: Scene, second: Scene, token: Int) {
        trace("first=${first.kind} ${first.panel?.summary().orEmpty()}")
        trace("second=${second.kind} ${second.panel?.summary().orEmpty()}")
        if (!first.same(second)) {
            retryScene("Screen transition or target position disagreed")
            return
        }
        if (closing && second.kind == NavKind.DETAILS) {
            closing = false
            trace("appraisal dismissed; header identity verified")
            scheduleRead()
            return
        }
        if (second.kind == NavKind.APPRAISAL && !closing) {
            val a = first.panel
            val b = second.panel
            if (a == null || b == null || !a.agrees(b)) {
                retryScene("Appraisal readings disagree or rail positions changed")
                return
            }
            // Two reads can coincide with a pause in the filling animation. Require a
            // third, later capture of the same card before committing any IV values.
            busy = true
            show("Checking stable appraisal bars")
            handler.postDelayed(
                {
                    if (generation == token)
                        frame(token) { value, _ ->
                            busy = false
                            val last = value as? Scene
                            val c =
                                last
                                    ?.takeIf {
                                        it.kind == NavKind.APPRAISAL &&
                                            it.identity == second.identity
                                    }
                                    ?.panel
                            if (c == null || !b.agrees(c)) {
                                retryScene("Appraisal changed during stability check")
                                return@frame
                            }
                            appraisalSpecies =
                                c.species?.takeIf {
                                    it.equals(b.species, true) && it.equals(a.species, true)
                                }
                            trace("agreement=3/3 ${c.summary()}")
                            session.appraise(c.spread!!)
                            closing = true
                            show("Verified ${c.spread}")
                            if (follow) scheduleRead()
                        }
                },
                750,
            )
            return
        }
        if (!follow) {
            reject(second.reason.ifBlank { "Open or close appraisal manually, then Read" })
            return
        }
        if (second.kind == NavKind.UNKNOWN) {
            retryScene(second.reason)
            return
        }
        if (
            closing &&
                second.kind == NavKind.APPRAISAL &&
                (first.panel?.let { a ->
                    second.panel?.let { a.agrees(it) && it.spread == session.spread }
                } != true)
        ) {
            retryScene("Card changed before dismissal")
            return
        }
        val count = actionCounts[second.kind] ?: 0
        if (count >= 2) {
            retryScene("Transition from ${second.kind} unverified; manual intervention required")
            return
        }
        val semantic =
            when (second.kind) {
                NavKind.DETAILS_MENU -> control("Pokémon menu")
                NavKind.MENU_APPRAISE -> control("Appraise")
                NavKind.APPRAISAL -> if (closing) control("Close appraisal") else null
                else -> null
            }
        actionCounts[second.kind] = count + 1
        if (semantic?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            trace("semantic action=${second.kind}")
            scheduleRead()
            return
        }
        if (second.target == null || !tapVerified(second.target, token)) {
            retryScene("No verified, unobstructed ${second.kind} control")
            return
        }
        trace("visual action=${second.kind} attempt=${count+1}")
    }

    private fun tapVerified(target: Target, token: Int): Boolean {
        val root = window() ?: return false
        val wins = windows
        val gameWin = wins.singleOrNull { it.id == root.windowId && it.isActive } ?: return false
        val bounds = Rect().also { gameWin.getBoundsInScreen(it) }
        if (bounds.width() != target.width || bounds.height() != target.height) return false
        val x = bounds.left + target.x
        val y = bounds.top + target.y
        if (
            !bounds.contains(x, y) ||
                wins.any { w ->
                    w.layer > gameWin.layer &&
                        Rect().also { w.getBoundsInScreen(it) }.contains(x, y)
                }
        )
            return false
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        busy = true
        val sent =
            dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 65))
                    .build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gesture: GestureDescription) {
                        if (token == generation) {
                            busy = false
                            scheduleRead()
                        }
                    }

                    override fun onCancelled(gesture: GestureDescription) {
                        if (token == generation) suspendScan("Verified tap interrupted")
                    }
                },
                handler,
            )
        if (!sent) busy = false
        return sent
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = window() ?: return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && result.size < 400) {
            val n = queue.removeFirst()
            result.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        if (queue.isNotEmpty()) return emptyList()
        return result.filter {
            it.isVisibleToUser && it.isEnabled && it.packageName?.toString() == game
        }
    }

    private fun control(label: String) =
        nodes().singleOrNull {
            it.isClickable &&
                (it.contentDescription?.toString().equals(label, true) ||
                    it.text?.toString().equals(label, true))
        }

    private fun scheduleRead() {
        val token = generation
        handler.postDelayed(
            {
                if (
                    token == generation &&
                        !busy &&
                        window() != null &&
                        session.phase !in listOf(Phase.STOPPED, Phase.PAUSED)
                )
                    captureStep()
            },
            900,
        )
    }

    private fun followControls() {
        if (!follow || window() == null || busy || session.failures > 0 || review != null) return
        when (session.phase) {
            Phase.APPRAISAL,
            Phase.MOVES,
            Phase.REVIEW -> scheduleRead()
            Phase.NEXT -> if (rename == Rename.NONE && !nextRequested) requestNext()
            else -> Unit
        }
    }

    private fun requestNext() {
        advancing = true
        captureStep()
    }

    private fun advanceVerified(value: Any) {
        advancing = false
        if (value != original || session.phase != Phase.NEXT) {
            suspendScan("Current page changed before advance")
            return
        }
        nextRequested = true
        val clicked =
            control("Next Pokémon")?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (!clicked && swipePage(horizontal = true, back = false)) return
        show(if (clicked) "Checking next details…" else "Advance manually or stop.")
        if (clicked) scheduleRead()
    }

    // Gestures only navigate a verified detail window. Edit and submit always require
    // unique semantic controls. A completed gesture triggers a fresh observation.
    private fun swipePage(horizontal: Boolean, back: Boolean): Boolean {
        val root = window() ?: return false
        val gameWindow =
            windows.singleOrNull { it.id == root.windowId && it.isActive } ?: return false
        val bounds = Rect().also { gameWindow.getBoundsInScreen(it) }
        val overlay = Rect()
        if (panel?.getGlobalVisibleRect(overlay) != true || bounds.isEmpty) return false
        val location = IntArray(2)
        panel?.getLocationOnScreen(location)
        val top = maxOf(location[1] + overlay.height() + 24, bounds.top + bounds.height() / 3)
        val bottom = bounds.top + bounds.height() * 4 / 5
        if (bottom - top < bounds.height() / 5) return false
        val x = bounds.centerX().toFloat()
        val y = (top + bottom) / 2f
        val path =
            Path().apply {
                if (horizontal) {
                    moveTo(bounds.left + bounds.width() * 0.78f, y)
                    lineTo(bounds.left + bounds.width() * 0.22f, y)
                } else {
                    moveTo(x, (if (back) top else bottom).toFloat())
                    lineTo(x, (if (back) bottom else (bottom + top) / 2).toFloat())
                }
            }
        val gesture =
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
                .build()
        val token = generation
        busy = true
        val started =
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(description: GestureDescription) {
                        if (token == generation) {
                            busy = false
                            show("Verifying page after swipe")
                            scheduleRead()
                        }
                    }

                    override fun onCancelled(description: GestureDescription) {
                        if (token == generation) suspendScan("Navigation gesture interrupted")
                    }
                },
                handler,
            )
        if (!started) busy = false
        return started
    }

    private fun verifiedReturn(first: List<Line>, lines: List<Line>): Boolean {
        val signature = lines.joinToString("|") { it.text + "@" + it.top / 12 }
        if (signature == lastReturn || first.map { it.text } != lines.map { it.text }) return false
        val page = MoveViewport.read(lines)
        val known =
            page.header ||
                Recognition.moves(lines) != null ||
                page.rows.any { it in observedRows } ||
                listOf("Power Up", "Stardust", "Evolve").count { label ->
                    lines.any { it.text.equals(label, true) }
                } >= 2
        if (known) lastReturn = signature
        return known
    }

    private fun tryScroll(lines: List<Line>) {
        trace("ordinary moves seek")
        val fingerprint = lines.joinToString("|") { it.text }
        val detail = Recognition.detail(lines)
        val sectionLabels =
            listOf("Gyms & Raids", "Trainer Battles", "Power Up", "New Attack", "Stardust", "Candy")
        val recognizedSection =
            sectionLabels.count { label -> lines.any { it.text.equals(label, true) } } >= 2
        if (
            (detail != null && detail != session.details) ||
                (detail == null &&
                    !recognizedSection &&
                    !MoveViewport.read(lines).header &&
                    MoveViewport.read(lines).rows.none { it in observedRows })
        ) {
            reject("Detail page unverified. Show the regular attacks.")
            return
        }
        if (scrolls >= 5 || fingerprint == lastScroll) {
            reject("Scroll limit or unchanged page. Review moves manually.")
            return
        }
        scrolls++
        lastScroll = fingerprint
        val scroll = nodes().singleOrNull { it.isScrollable }
        if (scroll?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true) {
            show("Verifying scrolled page")
            scheduleRead()
        } else if (!swipePage(horizontal = false, back = false)) {
            reject("Scroll unavailable. Move the page manually.")
        }
    }

    private fun accept(value: Any) {
        if (advancing) {
            advanceVerified(value)
            return
        }
        if (rename != Rename.NONE) {
            verifyRename(value as? Detail)
            return
        }
        when (session.phase) {
            Phase.DETAILS,
            Phase.NEXT -> {
                session.detail(value as Detail)
                if (session.phase != Phase.APPRAISAL) {
                    suspendScan("End or repeated identity; no further advance")
                    return
                }
                observedRows = emptyList()
                lastReturn = ""
                draftStored = false
                closing = false
                appraisalSpecies = null
                actionCounts.clear()
                scrolls = 0
                returnScrolls = 0
                lastScroll = ""

                approvedSpecies = null
                customOverride = false
                nextRequested = false
            }
            Phase.APPRAISAL -> session.appraise(value as Spread)
            Phase.MOVES -> {
                val moves = value as Attacks
                if (!moves.complete && follow) {
                    reject(
                        "Ordinary moves visible; last slot boundary missing. Reveal New Attack or the end of the regular table."
                    )
                    return
                }
                session.moves(moves)
            }
            Phase.REVIEW -> {
                if (value != session.details) {
                    suspendScan("Details changed before save")
                    return
                }
                if (
                    follow &&
                        mode == "Scan only" &&
                        session.spread != null &&
                        (!session.scanMoves || session.attacks?.complete == true)
                ) {
                    approvedSpecies = session.details?.visible?.species ?: appraisalSpecies
                    if (approvedSpecies != null) {
                        save(automatic = true)
                        return
                    }
                }
                if (approvedSpecies == null) review(value as Detail) else save()
                return
            }
            else -> return
        }
        show(if (session.failures > 0) session.reason else value.toString())
        followControls()
    }

    private fun review(detail: Detail) {
        val s = session.spread
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 12, 32, 12)
            }
        fun caption(value: String) {
            content.addView(TextView(this).apply { text = value })
        }
        caption("Current name: ${detail.name} · CP ${detail.cp} · HP ${detail.hp}")
        caption(
            if (s == null) "Uncertain IVs · automatic rename unavailable"
            else "Attack ${s.attack}   Defense ${s.defense}   Stamina ${s.stamina}"
        )
        caption(
            session.attacks?.let {
                listOfNotNull(it.fast, it.charged, it.second).joinToString(" / ")
            } ?: "Moves unconfirmed"
        )
        caption("Confirm species. Enter the actual species for a custom nickname.")
        val species =
            EditText(this).apply {
                setSingleLine()
                setText(detail.visible.species ?: detail.name)
                content.addView(this)
            }
        val override =
            CheckBox(this).apply {
                text = "Allow replacing this Pokémon’s custom nickname"
                if (mode == "Automatic") content.addView(this)
            }
        val dialog =
            AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Review scan · $profileName")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", null)
                .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.setOnDismissListener { review = null }
        review = dialog
        dialog.show()
        dialog.getButton(-1).setOnClickListener {
            val name = species.text.toString().trim()
            if (name.isBlank()) {
                species.error = "Enter species"
                return@setOnClickListener
            }
            approvedSpecies = name
            customOverride = override.isChecked
            dialog.dismiss()
            val token = generation
            handler.postDelayed(
                { if (token == generation && session.phase == Phase.REVIEW) captureStep() },
                600,
            )
        }
    }

    private fun savePartial() {
        val d = session.details ?: return
        if (session.phase != Phase.PAUSED || draftStored || insertionPending) return
        approvedSpecies = d.visible.species ?: "Unknown"
        save(partial = true)
    }

    private fun save(partial: Boolean = false, automatic: Boolean = false) {
        if (insertionPending || draftStored) return
        val d = session.details ?: return
        val s = session.spread.takeUnless { partial }
        val a = session.attacks.takeUnless { partial }
        val species = approvedSpecies ?: return
        val record =
            Specimen(
                profileId = profile,
                species = species,
                nickname = d.name,
                form = d.visible.form.orEmpty(),
                shiny = d.visible.shiny,
                shadow = d.visible.shadow,
                purified = d.visible.purified,
                lucky = d.visible.lucky,
                maxStatus = d.visible.maxStatus,
                cp = d.cp,
                hp = d.hp,
                attack = s?.attack,
                defense = s?.defense,
                stamina = s?.stamina,
                fast = a?.fast.orEmpty(),
                charged = a?.charged.orEmpty(),
                second = a?.second.orEmpty(),
                confidence =
                    "Identity 2/2; IVs ${if(s==null)"uncertain" else "2/2"}; moves ${if(a==null)"unconfirmed" else "2/2"}; species ${if(partial)"unconfirmed" else if(automatic)"from appraisal/header" else "user-reviewed"}",
                verification =
                    if (partial || s == null || (session.scanMoves && a?.complete != true))
                        "Incomplete"
                    else if (automatic) "Scan verified" else "Scan reviewed",
                notes = if (a?.complete == true) "" else "Move slots unconfirmed",
            )
        val prefs = getSharedPreferences("settings", 0)
        val template =
            prefs.getString("template." + species.lowercase(), null)
                ?: prefs.getString("template", Naming.default)!!
        proposed = runCatching { Naming.preview(record, template) }.getOrDefault("")
        val token = generation
        busy = true
        insertionPending = true
        show("Saving observation")
        io.execute {
            val result = runCatching { Store.open(this).catalog().add(record) }
            handler.post {
                if (token != generation && session.details === d) {
                    insertionPending = false
                    if (result.isSuccess) draftStored = true
                    show("Check local record before resuming")
                }
                if (token == generation) {
                    insertionPending = false
                    busy = false
                    trace("save=${if(result.isSuccess)"ok" else "failed"} partial=$partial")
                    if (result.isFailure) reject("Local save failed")
                    else {
                        draftStored = true
                        storedId = result.getOrThrow()
                        log("Saved record #$storedId")
                        if (partial) {
                            finishSession()
                            return@post
                        }
                        session.saved()
                        original = d
                        if (
                            RenamePolicy.eligible(
                                mode,
                                record.copy(id = storedId),
                                proposed,
                                customOverride,
                                !session.scanMoves || a?.complete == true,
                            )
                        )
                            rename = Rename.VERIFY
                        if (mode == "Assisted" && proposed.isNotBlank())
                            getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("Nickname", proposed))
                        show(
                            "Saved #${result.getOrNull()}" +
                                if (mode != "Scan only") " · $proposed" else ""
                        )
                        followControls()
                    }
                }
            }
        }
    }

    private fun verifyRename(detail: Detail?) {
        val old = original ?: return
        when (rename) {
            Rename.VERIFY -> {
                if (
                    detail != old ||
                        control("Edit nickname")
                            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true
                ) {
                    suspendScan("Identity or unique rename control unavailable")
                    return
                }
                rename = Rename.EDIT
                show()
                scheduleRead()
            }
            Rename.CHECK -> {
                if (detail != old.copy(name = proposed)) {
                    suspendScan("Rename outcome uncertain. Inspect manually.")
                    return
                }
                rename = Rename.NONE
                session.detailAfterRename(detail)
                original = detail
                val id = storedId
                val name = proposed
                val token = generation
                busy = true
                show("Recording verified name")
                io.execute {
                    val result = runCatching { Store.open(this).catalog().verifiedName(id, name) }
                    handler.post {
                        if (generation == token) {
                            busy = false
                            if (result.getOrNull() == 1) {
                                show("Verified $name")
                                followControls()
                            } else suspendScan("Game rename verified. Local record update failed.")
                        }
                    }
                }
            }
            else -> suspendScan("Unexpected rename state")
        }
    }

    private fun editName() {
        val editor = nodes().singleOrNull { it.isEditable && it.text?.toString() == original?.name }
        val titleFound =
            nodes().any { node ->
                listOf(node.text, node.contentDescription).any {
                    it?.toString()?.lowercase() in
                        setOf("nickname", "edit nickname", "set nickname", "enter nickname")
                }
            }
        if (editor == null || !titleFound || control("OK") == null) {
            suspendScan("Name editor could not be verified")
            return
        }
        val args =
            Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    proposed,
                )
            }
        if (!editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            suspendScan("Editor refused text")
            return
        }
        if (
            nodes().singleOrNull { it.isEditable }?.text?.toString() != proposed ||
                control("OK")?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true
        ) {
            suspendScan("Check the open editor manually")
            return
        }
        busy = false
        rename = Rename.CHECK
        show()
        scheduleRead()
    }
}
