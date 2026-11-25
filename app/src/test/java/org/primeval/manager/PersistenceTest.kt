package org.primeval.manager

import android.content.Context
import androidx.room.Room
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PersistenceTest {
    @Test
    fun reopenAndIsolateProfiles() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        fun open() =
            Room.databaseBuilder(ctx, Store::class.java, "test.db").allowMainThreadQueries().build()
        val db = open()
        val dao = db.catalog()
        val a = dao.add(Profile(name = "A"))
        val b = dao.add(Profile(name = "B"))
        val specimen = Specimen(profileId = a, species = "Ditto")
        dao.add(specimen)
        dao.add(specimen)
        dao.add(specimen.copy(profileId = b))
        db.close()
        val reopened = open()
        try {
            val rows = reopened.catalog().records(a)
            assertEquals(2, rows.size)
            assertNotEquals(rows[0].id, rows[1].id)
            assertEquals(1, reopened.catalog().records(b).size)
            reopened.catalog().edit(rows[0].copy(nickname = "Copy"))
            assertEquals("Copy", reopened.catalog().records(a).first().nickname)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun migratePriorSchemaWithProfileIntact() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val schema =
            javaClass.classLoader!!
                .getResourceAsStream("org.primeval.manager.Store/2.json")!!
                .bufferedReader()
                .use { it.readText() }
        val tables = org.json.JSONObject(schema).getJSONObject("database").getJSONArray("entities")
        val file = ctx.getDatabasePath("prior.db")
        file.parentFile!!.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            for (i in 0 until tables.length()) {
                val table = tables.getJSONObject(i)
                val name = table.getString("tableName")
                fun create(obj: org.json.JSONObject) =
                    db.execSQL(obj.getString("createSql").replace("\${TABLE_NAME}", name))
                create(table)
                val indices = table.getJSONArray("indices")
                for (k in 0 until indices.length()) create(indices.getJSONObject(k))
            }
            db.execSQL("INSERT INTO Profile(id,name) VALUES (5,'Legacy')")
            db.version = 2
        }
        val db =
            Room.databaseBuilder(ctx, Store::class.java, "prior.db")
                .addMigrations(Store.scanHistory)
                .allowMainThreadQueries()
                .build()
        try {
            assertEquals("Legacy", db.catalog().profiles().single().name)
            db.catalog().log(ScanEntry(profileId = 5, message = "Resumed"))
            assertEquals(1, db.catalog().history(5).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun cancellingOneEventPreservesOtherItems() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val db =
            Room.inMemoryDatabaseBuilder(ctx, Store::class.java).allowMainThreadQueries().build()
        fun event(id: String, revision: Int, status: String) =
            "BEGIN:VEVENT\nUID:$id\nDTSTART:20260101T100000Z\nSEQUENCE:$revision\nSTATUS:$status\nEND:VEVENT"
        fun feed(vararg items: String) =
            "BEGIN:VCALENDAR\n" + items.joinToString("\n") + "\nEND:VCALENDAR"
        try {
            db.cache("a", feed(event("1", 2, "CONFIRMED"), event("2", 1, "CONFIRMED")))
            db.cache("a", feed(event("1", 1, "CANCELLED")))
            assertFalse(db.catalog().events().first { it.uid == "1" }.cancelled)
            db.cache("a", feed(event("1", 3, "CANCELLED")))
            assertTrue(db.catalog().events().first { it.uid == "1" }.cancelled)
            assertEquals(2, db.catalog().events().size)
            assertTrue(runCatching { db.cache("a", "bad") }.isFailure)
            assertEquals(2, db.catalog().events().size)
        } finally {
            db.close()
        }
    }
}
