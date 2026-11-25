package org.primeval.manager

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(indices=[Index(value=["name"], unique=true)])
data class Profile(@PrimaryKey(autoGenerate=true) val id:Long=0, val name:String)
@Entity(indices=[Index("profileId")], foreignKeys=[ForeignKey(entity=Profile::class,parentColumns=["id"],childColumns=["profileId"])])
data class Specimen(@PrimaryKey(autoGenerate=true) val id:Long=0, val profileId:Long,
    val species:String, val form:String="", val nickname:String="", val cp:Int?=null, val hp:Int?=null,
    val attack:Int?=null,val defense:Int?=null,val stamina:Int?=null,val fast:String="",val charged:String="",val second:String="",
    val shiny:Boolean?=null,val shadow:Boolean?=null,val purified:Boolean?=null,val lucky:Boolean?=null,
    val maxStatus:String="",val scannedAt:Long=System.currentTimeMillis(),val confidence:String="Manual entry",
    val verification:String="Reviewed", @ColumnInfo(defaultValue="''") val notes:String="") {
    val ivPercent:Double? get()=ivs(attack,defense,stamina)
}
@Entity(primaryKeys=["source","uid"])
data class CalendarItem(val source:String,val uid:String,val title:String,val start:Long,val end:Long,val description:String,val sequence:Int,val cancelled:Boolean,val allDay:Boolean)
@Dao
interface Catalog {
    @Query("SELECT * FROM Profile ORDER BY id") fun profiles():List<Profile>
    @Insert fun add(profile:Profile):Long
    @Query("SELECT * FROM Specimen ORDER BY scannedAt DESC,id DESC") fun all():List<Specimen>
    @Query("SELECT * FROM Specimen WHERE profileId=:profile ORDER BY scannedAt DESC,id DESC") fun records(profile:Long):List<Specimen>
    @Insert fun add(record:Specimen):Long
    @Update fun edit(record:Specimen)
    @Query("SELECT * FROM CalendarItem ORDER BY start") fun events():List<CalendarItem>
    @Query("DELETE FROM CalendarItem WHERE source=:source") fun clearSource(source:String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) fun cache(events:List<CalendarItem>)
}
@Database(entities=[Profile::class,Specimen::class,CalendarItem::class],version=2,exportSchema=true)
abstract class Store:RoomDatabase() {
    abstract fun catalog():Catalog
    companion object {
        val migration=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE Specimen ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        }}
        @Volatile private var instance:Store?=null
        fun open(context:Context)=instance ?: synchronized(this){instance ?: Room.databaseBuilder(context.applicationContext,Store::class.java,"primeval.db").addMigrations(migration).build().also { instance=it }}
    }
}
