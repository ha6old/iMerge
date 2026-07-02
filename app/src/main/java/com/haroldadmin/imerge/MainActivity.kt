package com.haroldadmin.imerge

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haroldadmin.imerge.merge.MergeDirection
import com.haroldadmin.imerge.ui.Accent
import com.haroldadmin.imerge.ui.Hairline
import com.haroldadmin.imerge.ui.IMergeTheme
import com.haroldadmin.imerge.ui.Ink
import com.haroldadmin.imerge.update.AutoUpdateEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IMergeTheme(darkTheme = isSystemInDarkTheme()) {
                IMergeApp()
            }
        }
    }
}

@Composable
private fun IMergeApp(viewModel: MergeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    AutoUpdateEffect { message ->
        scope.launch { snackbarHost.showSnackbar(message) }
    }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            uris.forEach { uri ->
                runCatching {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            viewModel.addPhotos(uris)
        },
    )
    val openPicker = { picker.launch(arrayOf("image/*")) }

    val batchDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val export = viewModel.state.value.exportState as? ExportState.Saved
        if (export != null && result.resultCode == Activity.RESULT_OK) {
            val deleted = deleteTargets(context, export.sourceUris).map { it.sourceUri }
            viewModel.sourcePhotosDeleted(deleted)
            scope.launch { snackbarHost.showSnackbar(deletionMessage(deleted.size, export.sourceUris.size)) }
        } else {
            viewModel.retainSourcePhotos()
            scope.launch { snackbarHost.showSnackbar("已取消删除，原图仍保留在相册中") }
        }
    }

    var legacyRemaining by remember { mutableStateOf<List<DeleteTarget>>(emptyList()) }
    var legacyDeleted by remember { mutableStateOf<List<Uri>>(emptyList()) }
    lateinit var continueLegacyDelete: (List<DeleteTarget>, List<Uri>) -> Unit
    val legacyDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            continueLegacyDelete(legacyRemaining, legacyDeleted)
        } else {
            val total = (viewModel.state.value.exportState as? ExportState.Saved)?.sourceUris?.size ?: 0
            viewModel.sourcePhotosDeleted(legacyDeleted)
            scope.launch {
                snackbarHost.showSnackbar(
                    if (legacyDeleted.isEmpty()) "已取消删除，原图仍保留在相册中"
                    else deletionMessage(legacyDeleted.size, total),
                )
            }
        }
    }
    continueLegacyDelete = { targets, alreadyDeleted ->
        scope.launch {
            val attempt = deleteUntilConfirmation(context, targets)
            val allDeleted = alreadyDeleted + attempt.deleted
            if (attempt.permission != null) {
                legacyRemaining = attempt.remaining
                legacyDeleted = allDeleted
                legacyDeleteLauncher.launch(IntentSenderRequest.Builder(attempt.permission).build())
            } else {
                val total = (viewModel.state.value.exportState as? ExportState.Saved)?.sourceUris?.size ?: 0
                viewModel.sourcePhotosDeleted(allDeleted)
                snackbarHost.showSnackbar(
                    if (attempt.error != null && allDeleted.isEmpty()) attempt.error
                    else deletionMessage(allDeleted.size, total),
                )
            }
        }
    }

    val requestSourceDeletion: (List<Uri>) -> Unit = { sources ->
        val targets = deleteTargets(context, sources)
        if (targets.isEmpty()) {
            viewModel.retainSourcePhotos()
            scope.launch { snackbarHost.showSnackbar("这些原图来自云端或不支持删除，已为你保留") }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val request = MediaStore.createDeleteRequest(resolver, targets.map { it.mediaUri })
                batchDeleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }.onFailure {
                viewModel.retainSourcePhotos()
                scope.launch { snackbarHost.showSnackbar("无法请求删除原图，原图已保留") }
            }
        } else {
            continueLegacyDelete(targets, emptyList())
        }
    }

    LaunchedEffect(state.exportState) {
        when (val export = state.exportState) {
            is ExportState.Failed -> {
                snackbarHost.showSnackbar(export.message)
                viewModel.dismissExportError()
            }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Header(photoCount = state.photos.size)
            if (state.photos.isEmpty()) {
                EmptyState(onPick = openPicker, modifier = Modifier.weight(1f))
            } else {
                Editor(
                    state = state,
                    onPick = openPicker,
                    onRemove = viewModel::removePhoto,
                    onMove = viewModel::movePhoto,
                    onDirection = viewModel::setDirection,
                    onExport = viewModel::export,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    (state.exportState as? ExportState.Saved)?.let { export ->
        MergeSuccessDialog(
            export = export,
            onKeep = {
                viewModel.retainSourcePhotos()
                scope.launch { snackbarHost.showSnackbar("拼接图已保存，原图已保留") }
            },
            onDelete = { requestSourceDeletion(export.sourceUris) },
        )
    }
}

@Composable
private fun MergeSuccessDialog(
    export: ExportState.Saved,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Surface(shape = CircleShape, color = Accent.copy(alpha = .16f), modifier = Modifier.size(54.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✓", color = Accent, style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
        title = { Text("拼接成功", textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "已保存到手机本地相册",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pictures/iMerge/${export.fileName}",
                    color = Accent,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "是否删除拼接前的 ${export.sourceUris.size} 张原图？",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("删除原图", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) { Text("保留原图") }
        },
    )
}

private data class DeleteTarget(val sourceUri: Uri, val mediaUri: Uri)

private data class LegacyDeleteAttempt(
    val deleted: List<Uri>,
    val remaining: List<DeleteTarget> = emptyList(),
    val permission: IntentSender? = null,
    val error: String? = null,
)

private fun deleteTargets(context: Context, sources: List<Uri>): List<DeleteTarget> =
    sources.mapNotNull { source ->
        val mediaUri = when {
            source.authority == MediaStore.AUTHORITY && "picker" !in source.pathSegments -> source
            else -> runCatching { MediaStore.getMediaUri(context, source) }.getOrNull()
        }
        mediaUri?.let { DeleteTarget(source, it) }
    }

private suspend fun deleteUntilConfirmation(
    context: Context,
    targets: List<DeleteTarget>,
): LegacyDeleteAttempt = withContext(Dispatchers.IO) {
    val deleted = mutableListOf<Uri>()
    targets.forEachIndexed { index, target ->
        try {
            context.contentResolver.delete(target.mediaUri, null, null)
            deleted += target.sourceUri
        } catch (recoverable: RecoverableSecurityException) {
            return@withContext LegacyDeleteAttempt(
                deleted = deleted,
                remaining = targets.drop(index),
                permission = recoverable.userAction.actionIntent.intentSender,
            )
        } catch (_: SecurityException) {
            return@withContext LegacyDeleteAttempt(
                deleted = deleted,
                error = "部分原图不允许删除，已为你保留",
            )
        }
    }
    LegacyDeleteAttempt(deleted = deleted)
}

private fun deletionMessage(deleted: Int, total: Int): String = when {
    deleted <= 0 -> "原图未删除，仍保留在相册中"
    deleted == total -> "已删除 $deleted 张原图，拼接图已保留"
    else -> "已删除 $deleted 张原图，其余来源不支持删除并已保留"
}

@Composable
private fun Header(photoCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("iMerge", style = MaterialTheme.typography.headlineSmall)
        Box(Modifier.padding(start = 2.dp, top = 13.dp).size(6.dp).background(Accent, CircleShape))
        Spacer(Modifier.weight(1f))
        if (photoCount > 0) {
            Text("$photoCount 张照片", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .56f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 48.dp)) {
            Surface(
                onClick = onPick,
                modifier = Modifier.size(152.dp),
                shape = RoundedCornerShape(40.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("＋", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light, color = Accent)
                        Text("选择照片", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("把片段连成一张完整的图", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(7.dp))
            Text("支持纵向或横向拼接，无水印", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Editor(
    state: MergeUiState,
    onPick: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDirection: (MergeDirection) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StitchedPreview(
            photos = state.photos,
            direction = state.direction,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(14.dp))
        ReorderStrip(state.photos, onPick, onRemove, onMove)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionSwitch(state.direction, onDirection)
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onExport,
                enabled = state.photos.size >= 2 && state.exportState !is ExportState.Working,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (state.exportState is ExportState.Working) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(9.dp))
                    Text("正在合并")
                } else {
                    Text(if (state.photos.size < 2) "再选一张" else "保存到相册")
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun StitchedPreview(
    photos: List<PhotoItem>,
    direction: MergeDirection,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            when (direction) {
                MergeDirection.Vertical -> LazyColumn(
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(photos, key = { it.id }) { PhotoPreview(it.uri, Modifier.fillMaxWidth()) }
                }
                MergeDirection.Horizontal -> LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(photos, key = { it.id }) { PhotoPreview(it.uri, Modifier.fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
private fun PhotoPreview(uri: Uri, modifier: Modifier = Modifier) {
    val bitmap = rememberUriBitmap(uri)
    val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: 1f
    Box(modifier.aspectRatio(ratio).background(Hairline.copy(alpha = .35f))) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ReorderStrip(
    photos: List<PhotoItem>,
    onPick: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(70.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
            ReorderThumbnail(photo, index, photos.lastIndex, onRemove, onMove)
        }
        item {
            Surface(
                onClick = onPick,
                modifier = Modifier.size(70.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("＋", color = Accent, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { contentDescription = "继续添加照片" })
                }
            }
        }
    }
}

@Composable
private fun ReorderThumbnail(
    photo: PhotoItem,
    index: Int,
    lastIndex: Int,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    var drag by remember(index) { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .size(70.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .semantics { contentDescription = "第 ${index + 1} 张照片，长按拖动排序" }
            .then(
                Modifier.pointerInput(photo.id, index) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = { drag = 0f },
                        onDragCancel = { drag = 0f },
                    ) { change, amount ->
                        change.consume()
                        drag += amount.x
                        if (drag > threshold && index < lastIndex) {
                            onMove(index, index + 1)
                            drag = 0f
                        } else if (drag < -threshold && index > 0) {
                            onMove(index, index - 1)
                            drag = 0f
                        }
                    }
                },
            ),
    ) {
        val bitmap = rememberUriBitmap(photo.uri)
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Surface(
            onClick = { onRemove(photo.id) },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp),
            shape = CircleShape,
            color = Ink.copy(alpha = .76f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("×", color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
            shape = CircleShape,
            color = Ink.copy(alpha = .72f),
        ) {
            Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun DirectionSwitch(selected: MergeDirection, onSelected: (MergeDirection) -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(4.dp)) {
            DirectionOption("纵向", MergeDirection.Vertical, selected, onSelected)
            DirectionOption("横向", MergeDirection.Horizontal, selected, onSelected)
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
        Text(label, color = if (active) Accent else MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun rememberUriBitmap(uri: Uri): Bitmap? {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    val longest = max(info.size.width, info.size.height)
                    val scale = (longest / 900f).coerceAtLeast(1f)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSize(
                        (info.size.width / scale).roundToInt().coerceAtLeast(1),
                        (info.size.height / scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }.getOrNull()
        }
    }
    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }
    return bitmap
}
