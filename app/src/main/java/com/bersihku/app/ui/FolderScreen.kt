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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.bersihku.app.data.Organizer
import com.bersihku.app.data.Scanner
import com.bersihku.app.data.Storage
import com.bersihku.app.data.Trash
import com.bersihku.app.data.formatAgo
import com.bersihku.app.data.formatCount
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
    /** false = ukuran folder belum diketahui (belum diperiksa) → tampilkan jumlah isinya. */
    val sizeKnown: Boolean,
    val itemCount: Int,
    val modified: Long,
    val type: FileType,
    val selectable: Boolean,
    val note: String?,
)

private class FolderData(val entries: List<Entry>, val tidyPlan: List<Pair<String, List<File>>>)

private enum class FolderSort(val label: String) {
    UKURAN("Paling besar dulu"),
    NAMA("Nama (A–Z)"),
    BARU("Paling baru dulu"),
}

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
                val known = sizes?.get(p)
                Entry(
                    path = p,
                    name = f.name,
                    isDir = true,
                    size = known ?: 0L,
                    sizeKnown = known != null,
                    itemCount = if (known == null) (f.list()?.size ?: -1) else -1,
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
                    sizeKnown = true,
                    itemCount = -1,
                    modified = f.lastModified(),
                    type = Scanner.typeOf(f.name),
                    selectable = !Guard.isProtectedFile(root, p),
                    note = null,
                )
            }
        }
}

@Composable
fun FolderScreen(vm: MainViewModel, path: String) {
    val ctx = LocalContext.current
    val r = vm.result
    val root = r?.root ?: Storage.root.absolutePath
    val selKey = selFolderKey(path)
    val selection = vm.selectionOf(selKey)
    var confirmDelete by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Entry?>(null) }
    var confirmTidy by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var sort by remember(path) { mutableStateOf(if (r != null) FolderSort.UKURAN else FolderSort.NAMA) }

    val data by produceState<FolderData?>(null, path, r, vm.fsVersion) {
        value = withContext(Dispatchers.IO) {
            FolderData(listEntries(root, path, r?.dirSizes), Organizer.tidyPlan(root, path))
        }
    }
    val d = data
    val list = remember(d, sort) {
        val entries = d?.entries
        when {
            entries == null -> null
            sort == FolderSort.UKURAN -> entries.sortedWith(
                compareByDescending<Entry> { it.size }.thenBy { it.name.lowercase() }
            )
            sort == FolderSort.NAMA -> entries.sortedWith(compareBy<Entry>({ !it.isDir }, { it.name.lowercase() }))
            else -> entries.sortedByDescending { it.modified }
        }
    }
    val folderSize = r?.dirSizes?.get(path) ?: d?.entries?.sumOf { it.size } ?: 0L
    val selected = remember(list, selection) { list?.filter { it.path in selection } ?: emptyList() }
    val title = if (path == root) "Memori HP" else path.substringAfterLast('/')
    val tidyCount = d?.tidyPlan?.sumOf { it.second.size } ?: 0

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = title,
                subtitle = "${formatSize(folderSize)} • ${path.removePrefix(root).ifEmpty { "/" }}",
                onBack = { vm.back() },
                actions = {
                    IconButton(onClick = { newFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Folder baru")
                    }
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Urutkan")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        for (s in FolderSort.entries) {
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                onClick = {
                                    sort = s
                                    sortMenu = false
                                },
                                leadingIcon = if (s == sort) {
                                    { Icon(Icons.Filled.CheckCircle, null, tint = Mint) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                val actions = buildList {
                    add(
                        BarAction("Pindahkan", Icons.Filled.DriveFileMove) {
                            vm.startMove(selKey, selected.map { Trash.Target(it.path, it.size, it.isDir) })
                        }
                    )
                    if (selected.size == 1) {
                        add(BarAction("Ganti nama", Icons.Filled.Edit) { renaming = selected[0] })
                    }
                    add(BarAction("Hapus", Icons.Filled.Delete, danger = true) { confirmDelete = true })
                }
                FileActionBar(
                    count = selected.size,
                    bytes = selected.sumOf { it.size },
                    onClear = { vm.setSelection(selKey, emptySet()) },
                    actions = actions,
                )
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
                        "Ketuk folder untuk membukanya. Centang file/folder untuk dipindah, diganti nama, atau dihapus. " +
                            "Tombol 📁+ di atas untuk membuat folder baru. Folder utama (DCIM, Download, Android, dll) dilindungi.",
                        null,
                    )
                }
            }
            if (tidyCount >= 3) {
                item { TidyCard(tidyCount, d?.tidyPlan ?: emptyList()) { confirmTidy = true } }
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

    if (confirmDelete) {
        val targets = remember(selected) { selected.map { Trash.Target(it.path, it.size, it.isDir) } }
        DeleteDialog(
            count = targets.size,
            bytes = targets.sumOf { it.size },
            permanentOnly = false,
            onDismiss = { confirmDelete = false },
        ) { permanent ->
            confirmDelete = false
            vm.delete(targets, permanent)
        }
    }

    if (newFolder) {
        NameDialog(
            title = "Folder baru",
            initial = "",
            confirmText = "Buat",
            selectWholeName = true,
            onDismiss = { newFolder = false },
        ) { name ->
            val err = vm.createFolder(path, name)
            if (err == null) newFolder = false
            err
        }
    }

    renaming?.let { e ->
        NameDialog(
            title = if (e.isDir) "Ganti nama folder" else "Ganti nama file",
            initial = e.name,
            confirmText = "Simpan",
            selectWholeName = e.isDir,
            onDismiss = { renaming = null },
        ) { name ->
            val err = vm.rename(selKey, e.path, e.isDir, e.size, name)
            if (err == null) renaming = null
            err
        }
    }

    if (confirmTidy) {
        val plan = d?.tidyPlan ?: emptyList()
        AlertDialog(
            onDismissRequest = { confirmTidy = false },
            icon = { Icon(Icons.Filled.AutoAwesome, null, tint = Mint) },
            title = { Text("Rapikan folder ini?") },
            text = {
                Column {
                    Text("File akan dikelompokkan ke sub-folder sesuai jenisnya:")
                    Spacer(Modifier.height(8.dp))
                    for ((sub, files) in plan) {
                        Text(
                            "• ${formatCount(files.size)} file → folder \"$sub\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Hanya file yang ada langsung di folder ini. Sub-folder yang sudah ada tidak diubah, " +
                            "dan tidak ada file yang dihapus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirmTidy = false
                    vm.tidy(path, plan)
                }) { Text("Rapikan") }
            },
            dismissButton = { TextButton(onClick = { confirmTidy = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun TidyCard(count: Int, plan: List<Pair<String, List<File>>>, onTidy: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Mint)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Rapikan otomatis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatCount(count)} file bisa dikelompokkan: " +
                        plan.joinToString(", ") { "${it.first} (${it.second.size})" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onTidy) { Text("Rapikan") }
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
            val pct = if (parentSize > 0L && e.sizeKnown) (e.size * 100 / parentSize).toInt() else 0
            val info = e.note ?: buildString {
                if (e.isDir && !e.sizeKnown) {
                    append(if (e.itemCount >= 0) "${formatCount(e.itemCount)} item" else "folder")
                } else {
                    append(formatSize(e.size))
                    if (pct > 0) append(" • $pct%")
                }
                if (!e.isDir) append(" • ${formatAgo(e.modified)}")
            }
            Text(
                info,
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
