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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IMergeApp(viewModel: MergeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    AutoUpdateEffect { message ->
        scope.launch { snackbarHost.showSnackbar(message) }
    }

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

    BackHandler(enabled = state.screen == Screen.Merge) {
        viewModel.closeMerge()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { BrandTitle() },
                navigationIcon = {
                    if (state.screen == Screen.Merge) {
                        IconButton(onClick = viewModel::closeMerge) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                expandedHeight = 56.dp,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                // The gallery draws its own full-bleed photo wall; only the merge page is inset here.
                .padding(horizontal = if (state.screen == Screen.Gallery) 0.dp else 20.dp),
        ) {
            when (state.screen) {
                Screen.Gallery -> GalleryScreen(
                    access = access,
                    galleryLoaded = state.galleryLoaded,
                    photos = state.gallery,
                    selected = state.selected,
                    onToggle = { photo ->
                        if (viewModel.toggleSelection(photo) == SelectionResult.LimitReached) {
                            scope.launch {
                                snackbarHost.showSnackbar(
                                    resources.getString(R.string.selection_limit_reached, MergeViewModel.MAX_PHOTOS),
                                )
                            }
                        }
                    },
                    onMerge = viewModel::openMerge,
                    onRequestAccess = { permissionLauncher.launch(photoAccessPermissions()) },
                    onOpenSettings = { context.openAppSettings() },
                    modifier = Modifier.weight(1f),
                )
                Screen.Merge -> MergeScreen(
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
private fun BrandTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
