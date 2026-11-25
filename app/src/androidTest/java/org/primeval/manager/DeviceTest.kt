package org.primeval.manager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceTest {
    private fun views(v: View): Sequence<View> = sequence {
        yield(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) yieldAll(views(v.getChildAt(i)))
    }

    @Test
    fun openDashboardAndCollection() {
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            val deadline = android.os.SystemClock.elapsedRealtime() + 60000
            var opened = false
            while (!opened && android.os.SystemClock.elapsedRealtime() < deadline) {
                screen.onActivity { activity ->
                    val button =
                        views(activity.window.decorView).filterIsInstance<Button>().firstOrNull {
                            it.text == "Browse collection"
                        }
                    if (button != null) {
                        button.performClick()
                        opened = true
                    }
                }
                if (!opened) Thread.sleep(100)
            }
            assertTrue("Dashboard should open the collection", opened)
            screen.onActivity { activity ->
                assertTrue(
                    views(activity.window.decorView).filterIsInstance<TextView>().any {
                        it.text == "Collection"
                    }
                )
            }
        }
    }

    @Test
    fun saveThenCorrectManualRecordThroughEditor() {
        val nickname = "Camp ★ " + System.nanoTime().toString().takeLast(4)
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            fun pressWhenReady(label: String, prefix: Boolean = false) {
                val deadline = android.os.SystemClock.elapsedRealtime() + 60000
                var pressed = false
                while (!pressed && android.os.SystemClock.elapsedRealtime() < deadline) {
                    screen.onActivity {
                        val matches =
                            android.view.inspector.WindowInspector.getGlobalWindowViews()
                                .flatMap { root -> views(root).filterIsInstance<Button>().toList() }
                                .filter {
                                    if (prefix) it.text.startsWith(label)
                                    else it.text.toString().equals(label, true)
                                }
                        if (matches.size == 1) {
                            matches.single().performClick()
                            pressed = true
                        }
                    }
                    if (!pressed) Thread.sleep(100)
                }
                assertTrue("Missing button $label", pressed)
            }
            fun input(hint: String, value: String) =
                screen.onActivity {
                    val editor =
                        android.view.inspector.WindowInspector.getGlobalWindowViews()
                            .flatMap { root ->
                                views(root).filterIsInstance<android.widget.EditText>().toList()
                            }
                            .single { it.hint == hint }
                    editor.setText(value)
                }
            pressWhenReady("Add a Pokémon")
            input("Species", "Pikachu")
            input("Current nickname", nickname)
            input("Attack IV", "14")
            input("Defense IV", "15")
            input("Stamina IV", "15")
            pressWhenReady("Save")
            pressWhenReady(nickname, true)
            input("Attack IV", "15")
            pressWhenReady("Save")
            val ctx =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .targetContext
            val deadline = android.os.SystemClock.elapsedRealtime() + 30000
            val dao = Store.open(ctx).catalog()
            while (
                dao.all().singleOrNull { it.nickname == nickname }?.attack != 15 &&
                    android.os.SystemClock.elapsedRealtime() < deadline
            ) Thread.sleep(100)
            val record = dao.all().single { it.nickname == nickname }
            assertEquals(100.0, record.ivPercent!!, 0.0)
            assertEquals("Manually reviewed", record.verification)
        }
    }

    @Test
    fun bundledOcrReadsSyntheticDetails() {
        val bitmap = Bitmap.createBitmap(720, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 54f
            }
        canvas.drawText("CP 1234", 230f, 130f, paint)
        canvas.drawText("Eevee", 270f, 350f, paint)
        canvas.drawText("75 / 80 HP", 235f, 455f, paint)
        val model = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text =
                Tasks.await(model.process(InputImage.fromBitmap(bitmap, 0)), 120, TimeUnit.SECONDS)
            val lines =
                text.textBlocks
                    .flatMap { it.lines }
                    .mapNotNull { l ->
                        l.boundingBox?.let { Line(l.text, it.left, it.top, it.right, it.bottom) }
                    }
            assertEquals(Detail("Eevee", 1234, 80), Recognition.detail(lines))
        } finally {
            model.close()
            bitmap.recycle()
        }
    }
}
