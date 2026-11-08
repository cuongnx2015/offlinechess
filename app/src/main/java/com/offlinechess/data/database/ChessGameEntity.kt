package com.offlinechess.data.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.ColumnInfo
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "games")
data class ChessGameEntity(
    @PrimaryKey val id: String,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val durationMillis: Long,
    val playerColor: String,
    val skillLevel: Int,
    val result: String,
    val fen: String,
    val movesUci: String,
    val pgn: String,
    val isComplete: Boolean,
    @ColumnInfo(defaultValue = "600000") val whiteTimeMillis: Long = 600_000L,
    @ColumnInfo(defaultValue = "600000") val blackTimeMillis: Long = 600_000L,
    @ColumnInfo(defaultValue = "0") val clockUpdatedAtMillis: Long = 0L,
    val timedOutSide: String? = null,
)

@Dao
interface ChessGameDao {
    @Query("SELECT * FROM games ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<ChessGameEntity>>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChessGameEntity?

    @Query("SELECT * FROM games WHERE isComplete = 0 ORDER BY updatedAtMillis DESC LIMIT 1")
    suspend fun getLatestIncomplete(): ChessGameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(game: ChessGameEntity)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM games")
    suspend fun deleteAll()
}

@Database(entities = [ChessGameEntity::class], version = 2, exportSchema = false)
abstract class ChessDatabase : RoomDatabase() {
    abstract fun games(): ChessGameDao

    companion object {
        @Volatile
        private var instance: ChessDatabase? = null
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN whiteTimeMillis INTEGER NOT NULL DEFAULT 600000")
                db.execSQL("ALTER TABLE games ADD COLUMN blackTimeMillis INTEGER NOT NULL DEFAULT 600000")
                db.execSQL("ALTER TABLE games ADD COLUMN clockUpdatedAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN timedOutSide TEXT")
            }
        }

        fun get(context: Context): ChessDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ChessDatabase::class.java,
                "offline_chess.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
