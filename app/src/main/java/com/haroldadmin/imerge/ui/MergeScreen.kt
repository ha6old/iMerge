package com.haroldadmin.imerge.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.haroldadmin.imerge.ExportState
import com.haroldadmin.imerge.R
import com.haroldadmin.imerge.gallery.GalleryPhoto
import com.haroldadmin.imerge.merge.MergeDirection

@Composable
fun MergeScreen(
    photos: List<GalleryPhoto>,
    direction: MergeDirection,
    exportState: ExportState,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDirection: (MergeDirection) -> Unit,
    onExport: () -> Unit,
    onAddMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StitchedPreview(
            photos = photos,
            direction = direction,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(14.dp))
        ReorderStrip(photos, onAddMore, onRemove, onMove)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionSwitch(direction, onDirection)
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onExport,
                enabled = exportState !is ExportState.Working,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (exportState is ExportState.Working) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(stringResource(R.string.merging))
                } else {
                    Text(stringResource(R.string.save_to_gallery))
                }
            }
        }
    }
}

@Composable
private fun StitchedPreview(
    photos: List<GalleryPhoto>,
    direction: MergeDirection,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            when (direction) {
                MergeDirection.Vertical -> LazyColumn(
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(photos, key = { it.key }) { PreviewImage(it, Modifier.fillMaxWidth()) }
                }
                MergeDirection.Horizontal -> LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(photos, key = { it.key }) { PreviewImage(it, Modifier.fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
private fun PreviewImage(photo: GalleryPhoto, modifier: Modifier = Modifier) {
    var measuredRatio by remember(photo.key) { mutableFloatStateOf(1f) }
    val ratio = if (photo.width > 0 && photo.height > 0) photo.aspectRatio else measuredRatio
    AsyncImage(
        model = photo.uri,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.height > 0f) measuredRatio = size.width / size.height
        },
        modifier = modifier
            .aspectRatio(ratio)
            .background(Hairline.copy(alpha = .35f)),
    )
}

@Composable
private fun ReorderStrip(
    photos: List<GalleryPhoto>,
    onAddMore: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(70.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        itemsIndexed(photos, key = { _, photo -> photo.key }) { index, photo ->
            ReorderThumbnail(
                photo = photo,
                index = index,
                lastIndex = photos.lastIndex,
                onRemove = onRemove,
                onMove = onMove,
            )
        }
        item {
            val addLabel = stringResource(R.string.add_more_photos)
            Surface(
                onClick = onAddMore,
                modifier = Modifier.size(70.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "＋",
                        color = Accent,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { contentDescription = addLabel },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderThumbnail(
    photo: GalleryPhoto,
    index: Int,
    lastIndex: Int,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    // The gesture outlives recompositions triggered by onMove, so it must read
    // the current position instead of the values captured when it started.
    val currentIndex by rememberUpdatedState(index)
    val currentLast by rememberUpdatedState(lastIndex)
    var drag by remember(photo.key) { mutableFloatStateOf(0f) }
    var dragging by remember(photo.key) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (dragging) 1.08f else 1f, label = "dragScale")
    val reorderHint = stringResource(R.string.reorder_hint, index + 1)
    Box(
        modifier
            .size(70.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .semantics { contentDescription = reorderHint }
            .pointerInput(photo.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        drag = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        drag = 0f
                    },
                ) { change, amount ->
                    change.consume()
                    drag += amount.x
                    if (drag > threshold && currentIndex < currentLast) {
                        onMove(currentIndex, currentIndex + 1)
                        drag = 0f
                    } else if (drag < -threshold && currentIndex > 0) {
                        onMove(currentIndex, currentIndex - 1)
                        drag = 0f
                    }
                }
            },
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val removeLabel = stringResource(R.string.remove_photo, index + 1)
        Surface(
            onClick = { onRemove(photo.key) },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp),
            shape = CircleShape,
            color = Ink.copy(alpha = .76f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.semantics { contentDescription = removeLabel }) {
                Text("×", color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
            shape = CircleShape,
            color = Ink.copy(alpha = .72f),
        ) {
            Text(
                "${index + 1}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DirectionSwitch(selected: MergeDirection, onSelected: (MergeDirection) -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(4.dp)) {
            DirectionOption(stringResource(R.string.direction_vertical), MergeDirection.Vertical, selected, onSelected)
            DirectionOption(stringResource(R.string.direction_horizontal), MergeDirection.Horizontal, selected, onSelected)
        }
    }
}

@Composable
private fun DirectionOption(
    label: String,
    value: MergeDirection,
    selected: MergeDirection,
    onSelected: (MergeDirection) -> Unit,
) {
    val active = value == selected
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Accent.copy(alpha = .15f) else Color.Transparent)
            .clickable { onSelected(value) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (active) Accent else MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
