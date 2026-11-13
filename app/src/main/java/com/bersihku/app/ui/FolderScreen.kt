package com.bersihku.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bersihku.app.data.FileType
import com.bersihku.app.data.Guard
import com.bersihku.app.data.Scanner
import com.bersihku.app.data.Storage
import com.bersihku.app.data.Trash
import com.bersihku.app.data.formatAgo
import com.bersihku.app.data.formatSize
import com.bersihku.app.data.openFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private class Entry(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val type: FileType,
    val selectable: Boolean,
    val note: String?,
)

private fun listEntries(root: String, path: String, sizes: Map<String, Long>?): List<Entry> {
    val children = File(path).listFiles() ?: return emptyList()
    val trashPath = Trash.dir.absolutePath
    return children
        .filter { it.absolutePath != trashPath }
        .map { f ->
            val p = f.absolutePath
            if (f.isDirectory) {
                val rel = p.removePrefix(root).lowercase()
                val locked = rel == "/android/data" || rel == "/android/obb"
                Entry(
                    path = p,
                    name = f.name,
                    isDir = true,
                    size = sizes?.get(p) ?: 0L,
                    modified = f.lastModified(),
                    type = FileType.LAINNYA,
                    selectable = !locked && !Guard.isProtectedDir(root, p),
                    note = if (locked) "Data aplikasi — bersihkan lewat menu Aplikasi" else null,
                )
            } else {
                Entry(
                    path = p,
                    name = f.name,
                    isDir = false,
                    size = f.length(),
                    modified = f.lastModified(),
                    type = Scanner.typeOf(f.name),
                    selectable = !Guard.isProtectedFile(root, p),
                    note = null,
                )
            }
        }
        .sortedByDescending { it.size }
}

@Composable
fun FolderScreen(vm: MainViewModel, path: String) {
    val ctx = LocalContext.current
    val r = vm.result
    val root = r?.root ?: Storage.root.absolutePath
    val selKey = selFolderKey(path)
    val selection = vm.selectionOf(selKey)
    var confirm by remember { mutableStateOf(false) }

    val entries by produceState<List<Entry>?>(null, path, r) {
        value = withContext(Dispatchers.IO) { listEntries(root, path, r?.dirSizes) }
    }
    val list = entries
    val folderSize = r?.dirSizes?.get(path) ?: list?.sumOf { it.size } ?: 0L
    val selected = remember(list, selection) { list?.filter { it.path in selection } ?: emptyList() }
    val title = if (path == root) "Semua folder" else path.substringAfterLast('/')

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = title,
                subtitle = "${formatSize(folderSize)} • ${path.removePrefix(root).ifEmpty { "/" }}",
                onBack = { vm.back() },
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                BottomActionBar(selected.size, selected.sumOf { it.size }) {
                    DangerButton("Hapus") { confirm = true }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
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
            if (path == root) {
                item {
                    InfoBanner(
                        "Ketuk folder untuk membukanya. Centang file atau folder yang mau dihapus. " +
                            "Folder utama (DCIM, Download, Android, dll) dan cadangan chat dilindungi.",
                        null,
                    )
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(Icons.Filled.FolderOpen, "Folder kosong", "Tidak ada file di folder ini.")
                }
            }
            items(list, key = { it.path }) { e ->
                EntryRow(
                    e = e,
                    parentSize = folderSize,
                    checked = e.path in selection,
                    onToggle = { vm.toggle(selKey, e.path) },
                    onClick = {
                        if (e.isDir) {
                            if (e.note == null) vm.open(Screen.Folder(e.path))
                        } else if (e.selectable) {
                            vm.toggle(selKey, e.path)
                        } else {
                            openFile(ctx, e.path)
                        }
                    },
                    onOpenFile = { openFile(ctx, e.path) },
                )
            }
        }
    }

    if (confirm) {
        val targets = remember(selected) { selected.map { Trash.Target(it.path, it.size, it.isDir) } }
        DeleteDialog(
            count = targets.size,
            bytes = targets.sumOf { it.size },
            permanentOnly = false,
            onDismiss = { confirm = false },
        ) { permanent ->
            confirm = false
            vm.delete(targets, permanent)
        }
    }
}

@Composable
private fun EntryRow(
    e: Entry,
    parentSize: Long,
    checked: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onOpenFile: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (e.selectable) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        } else {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Dilindungi",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        FileThumb(
            e.path,
            e.type,
            if (e.isDir) Modifier else Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenFile),
            isDir = e.isDir,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val pct = if (parentSize > 0L) (e.size * 100 / parentSize).toInt() else 0
            Text(
                e.note ?: buildString {
                    append(formatSize(e.size))
                    if (pct > 0) append(" • $pct%")
                    if (!e.isDir) append(" • ${formatAgo(e.modified)}")
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (e.note == null) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (parentSize > 0L && e.size > 0L) {
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((e.size.toFloat() / parentSize).coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .background(if (e.isDir) AmberSoft else typeColor(e.type))
                    )
                }
            }
        }
        if (e.isDir && e.note == null) {
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
