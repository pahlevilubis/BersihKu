package com.bersihku.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bersihku.app.data.Organizer
import com.bersihku.app.data.Storage
import com.bersihku.app.data.formatCount
import com.bersihku.app.data.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Pilih folder tujuan untuk file/folder yang mau dipindah. */
@Composable
fun MovePickerScreen(vm: MainViewModel) {
    val targets = vm.pendingMove
    val root = Storage.root.absolutePath
    var path by remember { mutableStateOf(root) }
    var newFolder by remember { mutableStateOf(false) }
    val movingDirs = remember(targets) { targets.filter { it.isDir }.map { it.path } }

    BackHandler {
        if (path != root) path = path.substringBeforeLast('/') else vm.cancelMove()
    }

    val dirs by produceState<List<File>?>(null, path, vm.fsVersion) {
        value = withContext(Dispatchers.IO) {
            File(path).listFiles()
                ?.filter { f ->
                    val p = f.absolutePath
                    f.isDirectory &&
                        !f.name.startsWith(".") &&
                        Organizer.isAllowedDestination(root, p) &&
                        movingDirs.none { d -> p == d || p.startsWith("$d/") }
                }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        }
    }

    if (targets.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }

    val allHere = targets.all { it.path.substringBeforeLast('/') == path }
    val folderName = if (path == root) "Memori HP" else path.substringAfterLast('/')
    val crumbs = if (path == root) "Memori HP" else "Memori HP › " + path.removePrefix("$root/").replace("/", " › ")

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = "Pindahkan ke…",
                subtitle = crumbs,
                onBack = { if (path != root) path = path.substringBeforeLast('/') else vm.cancelMove() },
                actions = {
                    IconButton(onClick = { newFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Folder baru")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        "${formatCount(targets.size)} item (${formatSize(targets.sumOf { it.size })}) akan dipindah ke:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        folderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.cancelMove() }) { Text("Batal") }
                        Button(
                            onClick = { vm.moveTo(path) },
                            enabled = !allHere,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (allHere) "Sudah di folder ini" else "Pindah ke sini") }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        val list = dirs
        if (list == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp),
        ) {
            item {
                InfoBanner(
                    "Buka folder tujuan, lalu tekan \"Pindah ke sini\". Belum ada foldernya? Tekan \"Buat folder baru\".",
                    null,
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { newFolder = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Mint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CreateNewFolder, null, tint = Mint) }
                    Spacer(Modifier.width(12.dp))
                    Text("Buat folder baru di sini", style = MaterialTheme.typography.bodyLarge, color = Mint)
                }
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "Tidak ada sub-folder di sini. Tekan \"Pindah ke sini\" atau buat folder baru.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(list, key = { it.absolutePath }) { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { path = f.absolutePath }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Folder, null, tint = AmberSoft) }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        f.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (newFolder) {
        NameDialog(
            title = "Folder baru di \"$folderName\"",
            initial = "",
            confirmText = "Buat",
            selectWholeName = true,
            onDismiss = { newFolder = false },
        ) { name ->
            val err = vm.createFolder(path, name) { created -> path = created }
            if (err == null) newFolder = false
            err
        }
    }
}
