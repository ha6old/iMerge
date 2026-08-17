package com.haroldadmin.imerge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.haroldadmin.imerge.R
import com.haroldadmin.imerge.gallery.GalleryPhoto

enum class PhotoAccess { None, Partial, Full }

@Composable
fun GalleryScreen(
    access: PhotoAccess,
    galleryLoaded: Boolean,
    photos: List<GalleryPhoto>,
    selected: List<GalleryPhoto>,
    selectionMode: Boolean,
    onOpenPhoto: (GalleryPhoto) -> Unit,
    onToggleSelection: (GalleryPhoto) -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            access == PhotoAccess.None -> PermissionState(
                onRequestAccess = onRequestAccess,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
            !galleryLoaded -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = Accent)
            }
            photos.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.gallery_empty),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> Column(Modifier.weight(1f)) {
                if (access == PhotoAccess.Partial) {
                    Spacer(Modifier.height(8.dp))
                    PartialAccessBanner(onRequestAccess, Modifier.padding(horizontal = 10.dp))
                    Spacer(Modifier.height(8.dp))
                }
                PhotoGrid(
                    photos = photos,
                    selected = selected,
                    selectionMode = selectionMode,
                    onOpenPhoto = onOpenPhoto,
                    onToggleSelection = onToggleSelection,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Darker than the toolbar in both themes.
                        .background(if (isSystemInDarkTheme()) PhotoWallDark else PhotoWall),
                )
            }
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<GalleryPhoto>,
    selected: List<GalleryPhoto>,
    selectionMode: Boolean,
    onOpenPhoto: (GalleryPhoto) -> Unit,
    onToggleSelection: (GalleryPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionIndex = remember(selected) {
        selected.withIndex().associate { (index, photo) -> photo.key to index }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp),
        contentPadding = PaddingValues(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(photos, key = { it.key }) { photo ->
            GalleryCell(
                photo = photo,
                selectionIndex = selectionIndex[photo.key],
                selectionMode = selectionMode,
                onOpen = { onOpenPhoto(photo) },
                onToggleSelection = { onToggleSelection(photo) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryCell(
    photo: GalleryPhoto,
    selectionIndex: Int?,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val description = if (selectionIndex != null) {
        stringResource(R.string.gallery_selected_position, selectionIndex + 1)
    } else {
        stringResource(R.string.gallery_photo)
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Color.White.copy(alpha = .08f))
            .then(
                if (selectionIndex != null) Modifier.border(2.dp, Accent, shape) else Modifier,
            )
            .testTag(galleryPhotoTestTag(photo.key))
            .combinedClickable(
                onClick = if (selectionMode) onToggleSelection else onOpen,
                onLongClick = onToggleSelection,
                onLongClickLabel = stringResource(R.string.gallery_select_photo),
            ),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selectionMode) {
            if (selectionIndex != null) {
                Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .18f)))
            }
            Surface(
                onClick = onToggleSelection,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .testTag(gallerySelectionTestTag(photo.key)),
                shape = CircleShape,
                color = if (selectionIndex != null) Accent else Ink.copy(alpha = .52f),
                border = if (selectionIndex == null) BorderStroke(1.dp, Color.White.copy(alpha = .82f)) else null,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selectionIndex != null) {
                        Text(
                            "${selectionIndex + 1}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        } else if (selectionIndex != null) {
            Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .18f)))
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                shape = CircleShape,
                color = Accent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${selectionIndex + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

internal fun galleryPhotoTestTag(key: String) = "gallery-photo-$key"

internal fun gallerySelectionTestTag(key: String) = "gallery-selection-$key"

@Composable
private fun PartialAccessBanner(onRequestAccess: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Accent.copy(alpha = .1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text(
                stringResource(R.string.gallery_partial_notice),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequestAccess) {
                Text(stringResource(R.string.gallery_partial_manage), color = Accent)
            }
        }
    }
}

@Composable
private fun PermissionState(
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 48.dp),
        ) {
            Surface(
                onClick = onRequestAccess,
                modifier = Modifier.size(152.dp),
                shape = RoundedCornerShape(40.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "＋",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Light,
                            color = Accent,
                        )
                        Text(stringResource(R.string.gallery_permission_grant), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.gallery_permission_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(7.dp))
            Text(
                stringResource(R.string.gallery_permission_body),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.gallery_permission_settings), color = Accent)
            }
        }
    }
}
