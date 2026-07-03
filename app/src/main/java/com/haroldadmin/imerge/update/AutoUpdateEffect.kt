package com.haroldadmin.imerge.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.haroldadmin.imerge.BuildConfig
import com.haroldadmin.imerge.R
import kotlinx.coroutines.launch
import java.io.File

/**
 * Runs the automatic update check and returns a trigger for manual checks
 * (which download immediately, even on metered networks).
 */
@Composable
fun AutoUpdateEffect(onMessage: (String) -> Unit): () -> Unit {
    if (BuildConfig.DEBUG) return {}
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { UpdateManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var pendingInstall by remember { mutableStateOf<File?>(null) }
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var installStartedPath by remember { mutableStateOf<String?>(null) }
    var installPermissionRequested by remember { mutableStateOf(false) }
    val needPermissionMessage = stringResource(R.string.update_need_permission)

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        pendingInstall?.let { apk ->
            if (activity.packageManager.canRequestPackageInstalls()) {
                installStartedPath = apk.absolutePath
                activity.startActivity(manager.installIntent(apk))
                pendingInstall = null
            } else {
                pendingInstall = null
                onMessage(needPermissionMessage)
            }
        }
    }

    val requestInstall: (File) -> Unit = { apk ->
        if (installStartedPath == apk.absolutePath) {
            Unit
        } else if (activity.packageManager.canRequestPackageInstalls()) {
            installStartedPath = apk.absolutePath
            activity.startActivity(manager.installIntent(apk))
        } else if (!installPermissionRequested) {
            installPermissionRequested = true
            pendingInstall = apk
            settingsLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri(),
                ),
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed = when (event) {
                Lifecycle.Event.ON_RESUME -> true
                Lifecycle.Event.ON_PAUSE -> false
                else -> isResumed
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(manager, isResumed) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (!isResumed || !manager.isPendingDownload(id)) return
                scope.launch { manager.completedApk()?.let(requestInstall) }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        manager.completedApk()?.let {
            requestInstall(it)
            return@LaunchedEffect
        }
        runCatching { manager.checkForUpdate() }.getOrNull()?.let { update ->
            if (!manager.isSkipped(update.versionCode)) pendingUpdate = update
        }
    }

    LaunchedEffect(isResumed) {
        if (isResumed) manager.completedApk()?.let(requestInstall)
    }

    val manualCheck: () -> Unit = {
        scope.launch {
            onMessage(resources.getString(R.string.update_checking))
            val readyApk = manager.completedApk()
            if (readyApk != null) {
                requestInstall(readyApk)
            } else {
                runCatching { manager.checkForUpdate() }
                    .onFailure { onMessage(resources.getString(R.string.update_check_failed)) }
                    .onSuccess { update ->
                        if (update == null) {
                            onMessage(resources.getString(R.string.update_latest))
                        } else {
                            pendingUpdate = null
                            manager.enqueue(update, allowMetered = true)
                            onMessage(resources.getString(R.string.update_downloading, update.versionName))
                        }
                    }
            }
        }
    }

    pendingUpdate?.let { update ->
        val scheduledMessage = stringResource(R.string.update_scheduled)
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            title = { Text(stringResource(R.string.update_found_title, update.versionName)) },
            text = {
                if (update.changelog.isNotBlank()) {
                    Text(
                        update.changelog,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingUpdate = null
                        manager.enqueue(update)
                        onMessage(scheduledMessage)
                    },
                ) { Text(stringResource(R.string.update_download)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        manager.skipVersion(update.versionCode)
                        pendingUpdate = null
                    },
                ) { Text(stringResource(R.string.update_later)) }
            },
        )
    }

    return manualCheck
}
