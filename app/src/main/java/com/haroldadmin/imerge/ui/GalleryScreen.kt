package com.haroldadmin.imerge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
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
    onToggle: (GalleryPhoto) -> Unit,
    onMerge: () -> Unit,
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
            else -> Column(Modifier.weight(1f).padding(top = 15.dp)) {
                if (access == PhotoAccess.Partial) {
                    PartialAccessBanner(onRequestAccess)
                    Spacer(Modifier.height(8.dp))
                }
                PhotoGrid(
                    photos = photos,
                    selected = selected,
                    onToggle = onToggle,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AnimatedVisibility(
            visible = selected.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Button(
                onClick = onMerge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(stringResource(R.string.merge_button_count, selected.size))
            }
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<GalleryPhoto>,
    selected: List<GalleryPhoto>,
    onToggle: (GalleryPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionIndex = remember(selected) {
        selected.withIndex().associate { (index, photo) -> photo.key to index }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(photos, key = { it.key }) { photo ->
            GalleryCell(
                photo = photo,
                selectionIndex = selectionIndex[photo.key],
                onToggle = { onToggle(photo) },
            )
        }
    }
}

@Composable
private fun GalleryCell(
    photo: GalleryPhoto,
    selectionIndex: Int?,
    onToggle: () -> Unit,
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
            .background(Hairline.copy(alpha = .35f))
            .then(
                if (selectionIndex != null) Modifier.border(2.dp, Accent, shape) else Modifier,
            )
            .clickable(onClick = onToggle),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selectionIndex != null) {
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

@Composable
private fun PartialAccessBanner(onRequestAccess: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Accent.copy(alpha = .1f),
        modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.padding(bottom = 48.dp),
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
