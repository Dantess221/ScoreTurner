package com.example.scoreturner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache

object PdfPageCache {
    private val mem = object : LruCache<String, Bitmap>((4 * 1024 * 1024)) { // ~4MB rough
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun getPage(ctx: Context, uri: Uri, pageIndex: Int, scale: Int = 3): Bitmap? {
        val key = "${uri}#${pageIndex}#${scale}"
        mem.get(key)?.let { return it }
        val bmp = render(ctx, uri, pageIndex, scale) ?: return null
        mem.put(key, bmp)
        return bmp
    }

    fun getPageCount(ctx: Context, uri: Uri): Int? {
        return try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
        } catch (_: Exception) { null }
    }

    private fun render(ctx: Context, uri: Uri, pageIndex: Int, scale: Int): Bitmap? {
        return try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
                    val page = renderer.openPage(pageIndex)
                    val w = page.width * scale
                    val h = page.height * scale
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                }
            }
        } catch (_: Exception) { null }
    }
}
