package com.haroldadmin.imerge

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haroldadmin.imerge.ui.Accent
import com.haroldadmin.imerge.ui.GalleryScreen
import com.haroldadmin.imerge.ui.IMergeTheme
import com.haroldadmin.imerge.ui.MergeScreen
import com.haroldadmin.imerge.ui.PhotoAccess
import com.haroldadmin.imerge.ui.PhotoViewerScreen
import com.haroldadmin.imerge.update.AutoUpdateEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
private fun IMergeApp(viewModel: MergeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val checkUpdates = AutoUpdateEffect { message ->
        scope.launch { snackbarHost.showSnackbar(message) }
    }
    FullscreenSystemBarsEffect(hidden = state.screen == Screen.PhotoViewer)

    var access by remember { mutableStateOf(photoAccessOf(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        access = photoAccessOf(context)
        if (access != PhotoAccess.None) viewModel.onPhotoAccessGranted()
    }
    var accessAutoRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(access) {
        if (access != PhotoAccess.None) viewModel.onPhotoAccessGranted()
    }
    LaunchedEffect(Unit) {
        if (!accessAutoRequested && access == PhotoAccess.None) {
            accessAutoRequested = true
            permissionLauncher.launch(photoAccessPermissions())
        }
    }
    // Partial access can be adjusted from system settings while the app is paused.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = photoAccessOf(context)
                if (access != PhotoAccess.None) viewModel.onPhotoAccessGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val deleteFlow = rememberSourceDeletionFlow(viewModel, snackbarHost)

    LaunchedEffect(state.exportState) {
        when (val export = state.exportState) {
            is ExportState.Failed -> {
                snackbarHost.showSnackbar(export.message)
                viewModel.dismissExportError()
            }
            else -> Unit
        }
    }

    BackHandler(enabled = state.screen != Screen.Gallery || state.selectionMode) {
        when (state.screen) {
            Screen.Gallery -> viewModel.cancelSelection()
            Screen.PhotoViewer -> viewModel.closePhoto()
            Screen.Merge -> viewModel.closeMerge()
        }
    }

    val galleryGridState = rememberLazyGridState(
        cacheWindow = LazyLayoutCacheWindow(
            aheadFraction = 2f,
            behindFraction = 2f,
        ),
    )
    val galleryAtTop by remember {
        derivedStateOf { !galleryGridState.canScrollBackward }
    }
    val appBarBackground = MaterialTheme.colorScheme.background
    val statusBarInsets = WindowInsets.statusBarsIgnoringVisibility
    val statusBarHeight = statusBarInsets.asPaddingValues().calculateTopPadding()
    val appBarHeight = statusBarHeight + 56.dp
    val showAppBar = state.screen == Screen.Merge || state.selectionMode || galleryAtTop

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (state.screen == Screen.PhotoViewer) Modifier.clearAndSetSemantics { }
                    else Modifier,
                ),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier
                        .offset(y = if (showAppBar) 0.dp else -appBarHeight)
                        .drawBehind {
                            val top = statusBarHeight.toPx()
                            drawRect(
                                color = appBarBackground,
                                topLeft = Offset(0f, top),
                                size = Size(size.width, (size.height - top).coerceAtLeast(0f)),
                            )
                        },
                    windowInsets = statusBarInsets,
                    title = {
                        if (state.screen != Screen.Merge && state.selectionMode) {
                            Text(stringResource(R.string.gallery_selection_count, state.selected.size))
                        } else {
                            BrandTitle(onClick = checkUpdates)
                        }
                    },
                    navigationIcon = {
                        when {
                            state.screen == Screen.Merge -> IconButton(onClick = viewModel::closeMerge) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                            state.screen != Screen.Merge && state.selectionMode -> IconButton(
                                onClick = viewModel::cancelSelection,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cancel_selection),
                                )
                            }
                        }
                    },
                    actions = {
                        if (state.screen != Screen.Merge &&
                            state.selectionMode &&
                            state.selected.size >= MergeViewModel.MIN_PHOTOS
                        ) {
                            IconButton(onClick = viewModel::openMerge) {
                                MergeActionIcon()
                            }
                        }
                    },
                    expandedHeight = 56.dp,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
        ) { scaffoldPadding ->
            when (state.screen) {
                Screen.Gallery, Screen.PhotoViewer -> GalleryScreen(
                    access = access,
                    galleryLoaded = state.galleryLoaded,
                    photos = state.gallery,
                    selected = state.selected,
                    gridState = galleryGridState,
                    selectionMode = state.selectionMode,
                    onOpenPhoto = viewModel::openPhoto,
                    onToggleSelection = { photo ->
                        if (viewModel.toggleSelection(photo) == SelectionResult.LimitReached) {
                            scope.launch {
                                snackbarHost.showSnackbar(
                                    resources.getString(R.string.selection_limit_reached, MergeViewModel.MAX_PHOTOS),
                                )
                            }
                        }
                    },
                    onRequestAccess = { permissionLauncher.launch(photoAccessPermissions()) },
                    onOpenSettings = { context.openAppSettings() },
                    contentPadding = scaffoldPadding,
                    modifier = Modifier.fillMaxSize(),
                )
                Screen.Merge -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                        .padding(horizontal = 20.dp),
                ) {
                    MergeScreen(
                        photos = state.selected,
                        direction = state.direction,
                        exportState = state.exportState,
                        onRemove = viewModel::removeSelected,
                        onMove = viewModel::moveSelected,
                        onDirection = viewModel::setDirection,
                        onExport = viewModel::export,
                        onAddMore = viewModel::closeMerge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (state.screen == Screen.PhotoViewer) {
            PhotoViewerScreen(
                photos = state.gallery,
                initialPhotoKey = state.viewedPhotoKey,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    (state.exportState as? ExportState.Saved)?.let { export ->
        MergeSuccessDialog(
            export = export,
            onKeep = {
                viewModel.retainSourcePhotos()
                scope.launch { snackbarHost.showSnackbar(resources.getString(R.string.originals_kept)) }
            },
            onDelete = { deleteFlow.request(export.sourceUris) },
        )
    }
}

@Composable
private fun FullscreenSystemBarsEffect(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, hidden) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hidden) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (hidden) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun MergeActionIcon() {
    val description = stringResource(R.string.merge_selected_photos)
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = description },
    ) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        val radius = 2.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(1.dp.toPx(), 3.dp.toPx()),
            size = Size(8.dp.toPx(), 7.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            style = stroke,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(1.dp.toPx(), 14.dp.toPx()),
            size = Size(8.dp.toPx(), 7.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(10.dp.toPx(), 6.5.dp.toPx()),
            end = Offset(15.dp.toPx(), 12.dp.toPx()),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(10.dp.toPx(), 17.5.dp.toPx()),
            end = Offset(15.dp.toPx(), 12.dp.toPx()),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(15.dp.toPx(), 8.dp.toPx()),
            size = Size(8.dp.toPx(), 8.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            style = stroke,
        )
    }
}

@Composable
private fun BrandTitle(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = stringResource(R.string.update_check_label), onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Box(
            Modifier
                .padding(start = 3.dp)
                .offset(y = 6.dp)
                .size(6.dp)
                .background(Accent, CircleShape),
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
        title = { Text(stringResource(R.string.merge_success_title), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.merge_success_body),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.merge_success_path, export.fileName),
                    color = Accent,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.merge_success_delete_prompt, export.sourceUris.size),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.delete_originals), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) { Text(stringResource(R.string.keep_originals)) }
        },
    )
}

// ---------------------------------------------------------------------------
// Photo access helpers
// ---------------------------------------------------------------------------

private fun photoAccessOf(context: Context): PhotoAccess {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_MEDIA_IMAGES) ->
            PhotoAccess.Full
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.Partial
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_EXTERNAL_STORAGE) ->
            PhotoAccess.Full
        else -> PhotoAccess.None
    }
}

private fun photoAccessPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:$packageName".toUri(),
        ),
    )
}

// ---------------------------------------------------------------------------
// Source photo deletion
// ---------------------------------------------------------------------------

private class SourceDeletionFlow(val request: (List<Uri>) -> Unit)

@Composable
private fun rememberSourceDeletionFlow(
    viewModel: MergeViewModel,
    snackbarHost: SnackbarHostState,
): SourceDeletionFlow {
    val context = LocalContext.current
    val resources = LocalResources.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()

    val batchDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val export = viewModel.state.value.exportState as? ExportState.Saved
        if (export != null && result.resultCode == Activity.RESULT_OK) {
            val deleted = deleteTargets(context, export.sourceUris).map { it.sourceUri }
            viewModel.sourcePhotosDeleted(deleted)
            scope.launch {
                snackbarHost.showSnackbar(deletionMessage(resources, deleted.size, export.sourceUris.size))
            }
        } else {
            viewModel.retainSourcePhotos()
            scope.launch { snackbarHost.showSnackbar(resources.getString(R.string.delete_cancelled)) }
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
                    if (legacyDeleted.isEmpty()) resources.getString(R.string.delete_cancelled)
                    else deletionMessage(resources, legacyDeleted.size, total),
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
                    if (attempt.notAllowed && allDeleted.isEmpty()) resources.getString(R.string.delete_not_allowed)
                    else deletionMessage(resources, allDeleted.size, total),
                )
            }
        }
    }

    return remember(viewModel) {
        SourceDeletionFlow { sources ->
            val targets = deleteTargets(context, sources)
            if (targets.isEmpty()) {
                viewModel.retainSourcePhotos()
                scope.launch { snackbarHost.showSnackbar(resources.getString(R.string.delete_unsupported)) }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    val request = MediaStore.createDeleteRequest(resolver, targets.map { it.mediaUri })
                    batchDeleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }.onFailure {
                    viewModel.retainSourcePhotos()
                    scope.launch { snackbarHost.showSnackbar(resources.getString(R.string.delete_request_failed)) }
                }
            } else {
                continueLegacyDelete(targets, emptyList())
            }
        }
    }
}

private data class DeleteTarget(val sourceUri: Uri, val mediaUri: Uri)

private data class LegacyDeleteAttempt(
    val deleted: List<Uri>,
    val remaining: List<DeleteTarget> = emptyList(),
    val permission: IntentSender? = null,
    val notAllowed: Boolean = false,
)

private fun deleteTargets(context: Context, sources: List<Uri>): List<DeleteTarget> =
    sources.mapNotNull { source ->
        val mediaUri = when {
            source.authority == MediaStore.AUTHORITY -> source
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
            return@withContext LegacyDeleteAttempt(deleted = deleted, notAllowed = true)
        }
    }
    LegacyDeleteAttempt(deleted = deleted)
}

private fun deletionMessage(resources: android.content.res.Resources, deleted: Int, total: Int): String = when {
    deleted <= 0 -> resources.getString(R.string.deleted_none)
    deleted == total -> resources.getString(R.string.deleted_all, deleted)
    else -> resources.getString(R.string.deleted_partial, deleted)
}
