package com.haroldadmin.imerge.merge

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext

class ImageMerger(private val resolver: ContentResolver) {

    suspend fun merge(uris: List<Uri>, direction: MergeDirection): Bitmap = withContext(Dispatchers.IO) {
        require(uris.size >= 2) { "请至少选择两张照片" }
        val sizes = uris.map { readSize(it) }
        val layout = MergeLayoutPlanner.plan(sizes, direction)
        val output = try {
            Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        } catch (error: OutOfMemoryError) {
            throw IOException("照片尺寸过大，无法分配导出内存", error)
        }

        try {
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            uris.zip(layout.placements).forEach { (uri, placement) ->
                coroutineContext.ensureActive()
                val source = decode(uri, placement.width, placement.height)
                try {
                    canvas.drawBitmap(
                        source,
                        null,
                        Rect(
                            placement.x,
                            placement.y,
                            placement.x + placement.width,
                            placement.y + placement.height,
                        ),
                        paint,
                    )
                } finally {
                    source.recycle()
                }
            }
            output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
    }

    suspend fun saveToGallery(bitmap: Bitmap): SavedImage = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val name = "iMerge_$timestamp.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/iMerge")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException("无法在系统相册中创建文件")
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream)) {
                    throw IOException("照片编码失败")
                }
            } ?: throw IOException("无法打开相册写入位置")
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
            SavedImage(uri, name)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun readSize(uri: Uri): ImageSize {
        var decodedSize: ImageSize? = null
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decodedSize = ImageSize(info.size.width, info.size.height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(1, 1)
        }
        bitmap.recycle()
        return decodedSize ?: throw IOException("无法读取照片尺寸")
    }

    private fun decode(uri: Uri, width: Int, height: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            decoder.setTargetSize(width, height)
        }
}

data class SavedImage(val uri: Uri, val displayName: String)
