package org.primeval.manager

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.InputStream
import java.util.concurrent.TimeUnit

object ReferenceReplay {
    /** Run off the UI thread. No screenshot or raw OCR text is retained. */
    fun read(open: () -> InputStream): PanelRead {
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, size) }
        require(
            size.outWidth > 0 &&
                size.outHeight > 0 &&
                size.outWidth.toLong() * size.outHeight <= 18000000
        ) {
            "Choose a PNG or JPEG under 18 megapixels"
        }
        val bitmap = open().use { BitmapFactory.decodeStream(it) } ?: error("Cannot decode image")
        val model = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result =
                Tasks.await(model.process(InputImage.fromBitmap(bitmap, 0)), 40, TimeUnit.SECONDS)
            val lines =
                result.textBlocks
                    .flatMap { it.lines }
                    .mapNotNull { l ->
                        l.boundingBox?.let { Line(l.text, it.left, it.top, it.right, it.bottom) }
                    }
            return Appraisal.read(lines, BitmapPixels(bitmap))
        } finally {
            model.close()
            bitmap.recycle()
        }
    }
}
