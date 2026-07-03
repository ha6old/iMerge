package com.haroldadmin.imerge

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haroldadmin.imerge.gallery.DeviceGallery
import com.haroldadmin.imerge.gallery.GalleryPhoto
import com.haroldadmin.imerge.merge.ImageMerger
import com.haroldadmin.imerge.merge.MergeDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { Gallery, Merge }

enum class SelectionResult { Added, Removed, LimitReached }

sealed interface ExportState {
    data object Idle : ExportState
    data object Working : ExportState
    data class Saved(val fileName: String, val sourceUris: List<Uri>) : ExportState
    data class Failed(val message: String) : ExportState
}

data class MergeUiState(
    val gallery: List<GalleryPhoto> = emptyList(),
    val galleryLoaded: Boolean = false,
    val selected: List<GalleryPhoto> = emptyList(),
    val screen: Screen = Screen.Gallery,
    val direction: MergeDirection = MergeDirection.Vertical,
    val exportState: ExportState = ExportState.Idle,
)

class MergeViewModel(application: Application) : AndroidViewModel(application) {
    private val merger = ImageMerger(application.contentResolver)
    private val deviceGallery = DeviceGallery(application.contentResolver)
    private val mutableState = MutableStateFlow(MergeUiState())
    val state = mutableState.asStateFlow()

    private var galleryObserver: ContentObserver? = null
    private var refreshJob: Job? = null

    init {
        releaseStalePersistedPermissions(application)
    }

    fun onPhotoAccessGranted() {
        if (galleryObserver == null) {
            galleryObserver = deviceGallery.registerObserver { refreshGallery() }
        }
        refreshGallery()
    }

    fun toggleSelection(photo: GalleryPhoto): SelectionResult {
        var result = SelectionResult.Removed
        mutableState.update { current ->
            val existing = current.selected.filterNot { it.key == photo.key }
            when {
                existing.size < current.selected.size -> {
                    result = SelectionResult.Removed
                    current.copy(selected = existing)
                }
                current.selected.size >= MAX_PHOTOS -> {
                    result = SelectionResult.LimitReached
                    current
                }
                else -> {
                    result = SelectionResult.Added
                    current.copy(selected = current.selected + photo)
                }
            }
        }
        return result
    }

    fun openMerge() {
        mutableState.update { current ->
            if (current.selected.isNotEmpty()) current.copy(screen = Screen.Merge) else current
        }
    }

    fun closeMerge() {
        mutableState.update { it.copy(screen = Screen.Gallery) }
    }

    fun removeSelected(key: String) {
        mutableState.update { current ->
            val selected = current.selected.filterNot { it.key == key }
            current.copy(
                selected = selected,
                screen = if (current.screen == Screen.Merge && selected.isEmpty()) Screen.Gallery else current.screen,
            )
        }
    }

    fun moveSelected(from: Int, to: Int) {
        mutableState.update { current ->
            if (from !in current.selected.indices || to !in current.selected.indices || from == to) return@update current
            val reordered = current.selected.toMutableList()
            val photo = reordered.removeAt(from)
            reordered.add(to, photo)
            current.copy(selected = reordered)
        }
    }

    fun setDirection(direction: MergeDirection) {
        mutableState.update { it.copy(direction = direction) }
    }

    fun export() {
        val snapshot = mutableState.value
        if (snapshot.selected.isEmpty() || snapshot.exportState is ExportState.Working) return
        mutableState.update { it.copy(exportState = ExportState.Working) }
        viewModelScope.launch {
            var output: android.graphics.Bitmap? = null
            try {
                output = merger.merge(snapshot.selected.map { it.uri }, snapshot.direction)
                val saved = merger.saveToGallery(output)
                mutableState.update {
                    it.copy(
                        exportState = ExportState.Saved(
                            fileName = saved.displayName,
                            sourceUris = snapshot.selected.map { photo -> photo.uri },
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = error.message
                    ?: getApplication<Application>().getString(R.string.export_failed)
                mutableState.update { it.copy(exportState = ExportState.Failed(message)) }
            } finally {
                output?.recycle()
            }
        }
    }

    fun retainSourcePhotos() {
        mutableState.update {
            it.copy(exportState = ExportState.Idle, selected = emptyList(), screen = Screen.Gallery)
        }
    }

    fun sourcePhotosDeleted(deletedUris: Collection<Uri>) {
        mutableState.update {
            it.copy(exportState = ExportState.Idle, selected = emptyList(), screen = Screen.Gallery)
        }
        refreshGallery()
    }

    fun dismissExportError() {
        mutableState.update { it.copy(exportState = ExportState.Idle) }
    }

    private fun refreshGallery() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val photos = runCatching { deviceGallery.load() }.getOrNull() ?: return@launch
            mutableState.update { current ->
                val available = photos.mapTo(mutableSetOf()) { it.key }
                // Keep non-MediaStore selections (e.g. injected in tests); drop photos gone from the gallery.
                val selected = current.selected.filter {
                    it.uri.authority != MediaStore.AUTHORITY || it.key in available
                }
                current.copy(
                    gallery = photos,
                    galleryLoaded = true,
                    selected = selected,
                    screen = if (current.screen == Screen.Merge && selected.isEmpty()) Screen.Gallery else current.screen,
                )
            }
        }
    }

    // Earlier versions picked photos through SAF and persisted read grants; they are no longer needed.
    private fun releaseStalePersistedPermissions(application: Application) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = application.contentResolver
            resolver.persistedUriPermissions.forEach { permission ->
                runCatching {
                    resolver.releasePersistableUriPermission(
                        permission.uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        galleryObserver?.let(deviceGallery::unregisterObserver)
        galleryObserver = null
    }

    companion object {
        const val MAX_PHOTOS = 30
    }
}
