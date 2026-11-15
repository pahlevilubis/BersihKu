package com.bersihku.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BersihKuRoot(vm: MainViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }

    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg == null) return@LaunchedEffect
        val r = snackbar.showSnackbar(
            message = msg.text,
            actionLabel = msg.actionLabel,
            withDismissAction = msg.actionLabel == null,
            duration = SnackbarDuration.Long,
        )
        if (r == SnackbarResult.ActionPerformed) msg.action?.invoke()
        vm.consumeMessage()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val s = vm.screen) {
            Screen.Home -> HomeScreen(vm)
            is Screen.FileList -> FileListScreen(vm, s.key)
            is Screen.Folder -> FolderScreen(vm, s.path)
            Screen.AppList -> AppsScreen(vm)
            Screen.TrashBin -> TrashScreen(vm)
            Screen.MovePicker -> MovePickerScreen(vm)
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 72.dp),
        )
        vm.busy?.let { BusyOverlay(it) }
    }
}
