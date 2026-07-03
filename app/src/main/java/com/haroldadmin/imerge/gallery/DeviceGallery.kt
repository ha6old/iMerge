package com.haroldadmin.imerge.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GalleryPhoto(
    val uri: Uri,
    val width: Int = 0,
    val height: Int = 0,
) {
    val key: String = uri.toString()
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

class DeviceGallery(private val resolver: ContentResolver) {

    suspend fun load(): List<GalleryPhoto> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
        )
        val photos = mutableListOf<GalleryPhoto>()
        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val width = cursor.getInt(1)
                val height = cursor.getInt(2)
                val rotated = cursor.getInt(3) % 180 != 0
                photos += GalleryPhoto(
                    uri = ContentUris.withAppendedId(collection, id),
                    width = if (rotated) height else width,
                    height = if (rotated) width else height,
                )
            }
        }
        photos
    }

    fun registerObserver(onChange: () -> Unit): ContentObserver {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        resolver.registerContentObserver(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            true,
            observer,
        )
        return observer
    }

    fun unregisterObserver(observer: ContentObserver) {
        resolver.unregisterContentObserver(observer)
    }
}
