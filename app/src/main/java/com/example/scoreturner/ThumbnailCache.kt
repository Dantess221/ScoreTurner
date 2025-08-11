package com.example.scoreturner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

object ThumbnailCache {
    fun workThumbFile(ctx: Context, workId: Long): File =
        File(ctx.cacheDir, "thumb_work_${workId}.jpg")

    fun getOrCreateWorkThumb(ctx: Context, work: WorkEntity, pages: List<ImagePageEntity>): File? {
        val f = workThumbFile(ctx, work.id)
        if (f.exists()) return f
        return try {
            when (work.type) {
                WorkType.PDF -> {
                    val src = work.sourceUri?.toUri() ?: return null
                    val bmp = renderPdfPage(ctx, src, 0, 512)
                    if (bmp != null) saveJpeg(f, bmp) else null
                }
                WorkType.IMAGE_SET -> {
                    val first = pages.minByOrNull { it.sortOrder }?.uri ?: return null
                    val bmp = decodeScaled(ctx, Uri.parse(first), 512)
                    if (bmp != null) saveJpeg(f, bmp) else null
                }
            }
        } catch (_: Exception) { null }
    }

    private fun renderPdfPage(ctx: Context, uri: Uri, pageIndex: Int, targetWidth: Int): Bitmap? {
        return try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
                    val page = renderer.openPage(pageIndex)
                    val scale = targetWidth / page.width.toFloat()
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                }
            }
        } catch (e: Exception) { null }
    }

    private fun decodeScaled(ctx: Context, uri: Uri, targetWidth: Int): Bitmap? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                val bmp = BitmapFactory.decodeStream(ins) ?: return null
                val scale = targetWidth / bmp.width.toFloat()
                val w = targetWidth
                val h = (bmp.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bmp, w, h, true)
            }
        } catch (_: Exception) { null }
    }

    private fun saveJpeg(file: File, bmp: Bitmap): File {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 82, out) }
        return file
    }
}
