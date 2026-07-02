package com.haroldadmin.imerge.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.haroldadmin.imerge.BuildConfig
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AutoUpdateEffect(onMessage: (String) -> Unit) {
    if (BuildConfig.DEBUG) return
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { UpdateManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var pendingInstall by remember { mutableStateOf<File?>(null) }
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var installStartedPath by remember { mutableStateOf<String?>(null) }
    var installPermissionRequested by remember { mutableStateOf(false) }

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
                onMessage("需要允许 iMerge 安装更新")
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
                    Uri.parse("package:${context.packageName}"),
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
        runCatching {
            manager.checkForUpdate()?.let { update -> update to manager.enqueue(update) }
        }.getOrNull()?.let { (update, result) ->
            if (result.startedNew) {
                onMessage("发现 iMerge ${update.versionName}，正在后台下载")
            }
        }
    }

    LaunchedEffect(isResumed) {
        if (isResumed) manager.completedApk()?.let(requestInstall)
    }
}
