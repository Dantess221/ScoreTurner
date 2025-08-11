package com.example.scoreturner

import androidx.room.*

enum class WorkType { PDF, IMAGE_SET }

@Entity(tableName = "works")
data class WorkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: WorkType,
    val sourceUri: String?, // for PDF
    val createdAt: Long,
    val lastOpenedAt: Long,
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = WorkEntity::class,
        parentColumns = ["id"],
        childColumns = ["workId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workId"), Index("sortOrder")]
)
data class ImagePageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workId: Long,
    val uri: String,
    val sortOrder: Int
)

data class WorkWithPages(
    @Embedded val work: WorkEntity,
    @Relation(parentColumn = "id", entityColumn = "workId", entity = ImagePageEntity::class)
    val pages: List<ImagePageEntity>
)

class Converters {
    @TypeConverter fun fromType(t: WorkType): String = t.name
    @TypeConverter fun toType(s: String): WorkType = WorkType.valueOf(s)
}

@Database(entities = [WorkEntity::class, ImagePageEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao
}

@Dao
interface WorkDao {
    @Insert suspend fun insertWork(work: WorkEntity): Long
    @Insert suspend fun insertPages(pages: List<ImagePageEntity>)
    @Update suspend fun updateWork(work: WorkEntity)

    @Query("UPDATE works SET lastOpenedAt=:ts WHERE id=:id")
    suspend fun touchWork(id: Long, ts: Long)

    @Query("UPDATE works SET title=:title WHERE id=:id")
    suspend fun renameWork(id: Long, title: String)

    @Query("UPDATE works SET isFavorite=:fav WHERE id=:id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Delete suspend fun deleteWork(work: WorkEntity)

    @Transaction
    @Query("SELECT * FROM works WHERE id=:id")
    fun observeWorkWithPages(id: Long): kotlinx.coroutines.flow.Flow<WorkWithPages?>

    @Transaction
    @Query("SELECT * FROM works ORDER BY isFavorite DESC, lastOpenedAt DESC")
    fun observeRecentsWithPages(): kotlinx.coroutines.flow.Flow<List<WorkWithPages>>
}
