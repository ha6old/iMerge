package com.haroldadmin.imerge

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haroldadmin.imerge.merge.ImageMerger
import com.haroldadmin.imerge.merge.MergeDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhotoItem(val uri: Uri, val id: String = uri.toString())

sealed interface ExportState {
    data object Idle : ExportState
    data object Working : ExportState
    data class Saved(val fileName: String, val sourceUris: List<Uri>) : ExportState
    data class Failed(val message: String) : ExportState
}

data class MergeUiState(
    val photos: List<PhotoItem> = emptyList(),
    val direction: MergeDirection = MergeDirection.Vertical,
    val exportState: ExportState = ExportState.Idle,
)

class MergeViewModel(application: Application) : AndroidViewModel(application) {
    private val merger = ImageMerger(application.contentResolver)
    private val mutableState = MutableStateFlow(MergeUiState())
    val state = mutableState.asStateFlow()

    fun addPhotos(uris: List<Uri>) {
        mutableState.update { current ->
            val known = current.photos.mapTo(mutableSetOf()) { it.id }
            val additions = uris.map(::PhotoItem).filter { known.add(it.id) }
            current.copy(photos = (current.photos + additions).take(MAX_PHOTOS))
        }
    }

    fun removePhoto(id: String) {
        mutableState.update { it.copy(photos = it.photos.filterNot { photo -> photo.id == id }) }
    }

    fun movePhoto(from: Int, to: Int) {
        mutableState.update { current ->
            if (from !in current.photos.indices || to !in current.photos.indices || from == to) return@update current
            val reordered = current.photos.toMutableList()
            val photo = reordered.removeAt(from)
            reordered.add(to, photo)
            current.copy(photos = reordered)
        }
    }

    fun setDirection(direction: MergeDirection) {
        mutableState.update { it.copy(direction = direction) }
    }

    fun export() {
        val snapshot = mutableState.value
        if (snapshot.photos.size < 2 || snapshot.exportState is ExportState.Working) return
        mutableState.update { it.copy(exportState = ExportState.Working) }
        viewModelScope.launch {
            var output: android.graphics.Bitmap? = null
            try {
                output = merger.merge(snapshot.photos.map { it.uri }, snapshot.direction)
                val saved = merger.saveToGallery(output)
                mutableState.update {
                    it.copy(
                        exportState = ExportState.Saved(
                            fileName = saved.displayName,
                            sourceUris = snapshot.photos.map { photo -> photo.uri },
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(exportState = ExportState.Failed(error.message ?: "导出失败，请重试"))
                }
            } finally {
                output?.recycle()
            }
        }
    }

    fun retainSourcePhotos() {
        mutableState.update { it.copy(exportState = ExportState.Idle) }
    }

    fun sourcePhotosDeleted(deletedUris: Collection<Uri>) {
        val deleted = deletedUris.mapTo(mutableSetOf()) { it.toString() }
        mutableState.update { current ->
            current.copy(
                photos = current.photos.filterNot { it.id in deleted },
                exportState = ExportState.Idle,
            )
        }
    }

    fun dismissExportError() {
        mutableState.update { it.copy(exportState = ExportState.Idle) }
    }

    companion object {
        const val MAX_PHOTOS = 30
    }
}
