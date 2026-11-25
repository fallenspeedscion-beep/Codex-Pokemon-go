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
@Config(sdk=[34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PersistenceTest {
    @Test fun reopenAndIsolateProfiles(){
        val ctx:Context=RuntimeEnvironment.getApplication()
        fun open()=Room.databaseBuilder(ctx,Store::class.java,"test.db").allowMainThreadQueries().build()
        val db=open();val dao=db.catalog();val a=dao.add(Profile(name="A"));val b=dao.add(Profile(name="B"))
        val specimen=Specimen(profileId=a,species="Ditto")
        dao.add(specimen);dao.add(specimen);dao.add(specimen.copy(profileId=b))
        db.close()
        val reopened=open()
        try {
            val rows=reopened.catalog().records(a)
            assertEquals(2,rows.size);assertNotEquals(rows[0].id,rows[1].id);assertEquals(1,reopened.catalog().records(b).size)
            reopened.catalog().edit(rows[0].copy(nickname="Copy"))
            assertEquals("Copy",reopened.catalog().records(a).first().nickname)
        } finally {reopened.close()}
    }
}
