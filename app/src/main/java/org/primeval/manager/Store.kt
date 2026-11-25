package org.primeval.manager

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(indices = [Index(value = ["name"], unique = true)])
data class Profile(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(
    indices = [Index("profileId")],
    foreignKeys =
        [ForeignKey(entity = Profile::class, parentColumns = ["id"], childColumns = ["profileId"])],
)
data class Specimen(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val species: String,
    val form: String = "",
    val nickname: String = "",
    val cp: Int? = null,
    val hp: Int? = null,
    val attack: Int? = null,
    val defense: Int? = null,
    val stamina: Int? = null,
    val fast: String = "",
    val charged: String = "",
    val second: String = "",
    val shiny: Boolean? = null,
    val shadow: Boolean? = null,
    val purified: Boolean? = null,
    val lucky: Boolean? = null,
    val maxStatus: String = "",
    val scannedAt: Long = System.currentTimeMillis(),
    val confidence: String = "Manual entry",
    val verification: String = "Reviewed",
    @ColumnInfo(defaultValue = "''") val notes: String = "",
) {
    val ivPercent: Double?
        get() = ivs(attack, defense, stamina)
}

@Entity(primaryKeys = ["source", "uid"])
data class CalendarItem(
    val source: String,
    val uid: String,
    val title: String,
    val start: Long,
    val end: Long,
    val description: String,
    val sequence: Int,
    val cancelled: Boolean,
    val allDay: Boolean,
)

@Entity(
    indices = [Index("profileId")],
    foreignKeys =
        [ForeignKey(entity = Profile::class, parentColumns = ["id"], childColumns = ["profileId"])],
)
data class ScanEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val time: Long = System.currentTimeMillis(),
    val message: String,
)

@Dao
interface Catalog {
    @Query("SELECT * FROM ScanEntry WHERE profileId=:profile ORDER BY id DESC LIMIT 20")
    fun history(profile: Long): List<ScanEntry>

    @Query("SELECT * FROM ScanEntry WHERE profileId=:profile AND message LIKE 'PPM2 %' ORDER BY id DESC LIMIT 2000")
    fun diagnostics(profile:Long):List<ScanEntry>

    @Insert fun log(entry: ScanEntry)

    @Query("SELECT * FROM Profile ORDER BY id") fun profiles(): List<Profile>

    @Insert fun add(profile: Profile): Long

    @Query("SELECT * FROM Specimen ORDER BY scannedAt DESC,id DESC") fun all(): List<Specimen>

    @Query("SELECT * FROM Specimen WHERE profileId=:profile ORDER BY scannedAt DESC,id DESC")
    fun records(profile: Long): List<Specimen>

    @Insert fun add(record: Specimen): Long

    @Query("UPDATE Specimen SET nickname=:name, verification='Rename verified' WHERE id=:id")
    fun verifiedName(id: Long, name: String): Int

    @Update fun edit(record: Specimen)

    @Query("SELECT * FROM CalendarItem ORDER BY start") fun events(): List<CalendarItem>

    @Query("DELETE FROM CalendarItem WHERE source=:source") fun clearSource(source: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun cache(events: List<CalendarItem>)
}

@Database(
    entities = [Profile::class, Specimen::class, CalendarItem::class, ScanEntry::class],
    version = 3,
    exportSchema = true,
)
abstract class Store : RoomDatabase() {
    abstract fun catalog(): Catalog

    fun cache(source: String, text: String) {
        val incoming = CalendarFeed.parse(text, source)
        val dao = catalog()
        runInTransaction {
            val old = dao.events().filter { it.source == source }.associateBy { it.uid }
            if (
                !text.contains(Regex("(?m)^METHOD:CANCEL\\s*$")) &&
                    (incoming.isEmpty() || incoming.any { !it.cancelled })
            )
                dao.clearSource(source)
            dao.cache(incoming.map { e -> old[e.uid]?.takeIf { it.sequence > e.sequence } ?: e })
        }
    }

    companion object {
        val migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE Specimen ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                }
            }
        val scanHistory =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS ScanEntry (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, profileId INTEGER NOT NULL, time INTEGER NOT NULL, message TEXT NOT NULL, FOREIGN KEY(profileId) REFERENCES Profile(id) ON UPDATE NO ACTION ON DELETE NO ACTION)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_ScanEntry_profileId ON ScanEntry(profileId)"
                    )
                }
            }
        @Volatile private var instance: Store? = null

        fun open(context: Context) =
            instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                Store::class.java,
                                "primeval.db",
                            )
                            .addMigrations(migration, scanHistory)
                            .build()
                            .also { instance = it }
                }
    }
}
