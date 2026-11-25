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
