package org.primeval.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceFixtureTest {
    @Test
    fun independentlyLabelledImages() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val rows =
            assets
                .open("expected.tsv")
                .bufferedReader()
                .use { it.readLines() }
                .filter { it.isNotBlank() }
        require(rows.isNotEmpty())
        for (row in rows) {
            val (file, a, d, h) = row.split('\t')
            val measured = ReferenceReplay.read { assets.open(file) }
            assertEquals(
                measured.summary(),
                Spread(a.toInt(), d.toInt(), h.toInt()),
                measured.spread,
            )
        }
    }
}
