package com.example.scoreturner

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class WorksRepository private constructor(private val ctx: Context, private val db: AppDatabase) {

    companion object {
        @Volatile private var INSTANCE: WorksRepository? = null
        fun get(ctx: Context): WorksRepository {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "scores.db")
                    .fallbackToDestructiveMigration()
                    .build()
                WorksRepository(ctx.applicationContext, db).also { INSTANCE = it }
            }
        }
    }

    fun observeRecents(): kotlinx.coroutines.flow.Flow<List<WorkWithPages>> = db.workDao().observeRecentsWithPages()
    fun observeWorkWithPages(id: Long): kotlinx.coroutines.flow.Flow<WorkWithPages?> = db.workDao().observeWorkWithPages(id)

    suspend fun touchWork(id: Long) = db.workDao().touchWork(id, System.currentTimeMillis())

    suspend fun createPdfWork(uri: Uri, customTitle: String?): Long {
        persistReadPermission(uri)
        val title = customTitle ?: defaultTitle("PDF")
        val now = System.currentTimeMillis()
        val work = WorkEntity(title = title, type = WorkType.PDF, sourceUri = uri.toString(), createdAt = now, lastOpenedAt = now)
        return db.workDao().insertWork(work)
    }

    suspend fun createImageSetWork(uris: List<Uri>, customTitle: String?): Long {
        if (uris.isEmpty()) error("Empty image list")
        uris.forEach { persistReadPermission(it) }
        val title = customTitle ?: defaultTitle("Images")
        val now = System.currentTimeMillis()
        val workId = db.workDao().insertWork(
            WorkEntity(title = title, type = WorkType.IMAGE_SET, sourceUri = null, createdAt = now, lastOpenedAt = now)
        )
        val pages = uris.mapIndexed { idx, u -> ImagePageEntity(workId = workId, uri = u.toString(), sortOrder = idx) }
        db.workDao().insertPages(pages)
        return workId
    }

    suspend fun renameWork(id: Long, title: String) = db.workDao().renameWork(id, title)
    suspend fun setFavorite(id: Long, fav: Boolean) = db.workDao().setFavorite(id, fav)
    suspend fun deleteWork(entity: WorkEntity) = db.workDao().deleteWork(entity)

    suspend fun importFolderImages(treeUri: Uri, customTitle: String? = null): Long {
        try { ctx.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {}
        val doc = DocumentFile.fromTreeUri(ctx, treeUri) ?: error("Invalid folder")
        val images = doc.listFiles()
            .filter { it.isFile && (
                (it.type?.startsWith("image/") == true) ||
                (it.name?.lowercase()?.endsWith(".jpg") == true) ||
                (it.name?.lowercase()?.endsWith(".jpeg") == true) ||
                (it.name?.lowercase()?.endsWith(".png") == true) ||
                (it.name?.lowercase()?.endsWith(".webp") == true)
            ) }
            .sortedBy { it.name?.lowercase() ?: "" }
            .map { it.uri }
        return createImageSetWork(images, customTitle)
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { /* ignore */ }
    }

    private fun defaultTitle(prefix: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "$prefix ${sdf.format(Date())}"
    }
}
