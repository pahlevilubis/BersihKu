package com.bersihku.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.bersihku.app.data.Scanner
import com.bersihku.app.data.Storage
import com.bersihku.app.data.Trash
import com.bersihku.app.data.formatAgo
import com.bersihku.app.data.formatCount
import com.bersihku.app.data.formatSize
import com.bersihku.app.data.openFile
import com.bersihku.app.data.shortFolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val items = vm.trashItems
    val selection = vm.selectionOf(SEL_TRASH)
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val total = items.sumOf { it.size }
    val sorted = remember(items) { items.sortedByDescending { it.deletedAt } }
    val selected = remember(items, selection) { items.filter { it.id in selection } }
    val root = Storage.root.absolutePath

    LaunchedEffect(Unit) { vm.refreshTrash() }

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = "Tempat Sampah",
                subtitle = "${formatCount(items.size)} item • ${formatSize(total)}",
                onBack = { vm.back() },
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                BottomActionBar(selected.size, selected.sumOf { it.size }) {
                    OutlinedButton(onClick = { vm.restore(selected.map { it.id }.toSet()) }) {
                        Icon(Icons.Filled.Restore, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Kembalikan")
                    }
                    DangerButton("Hapus") { confirmDelete = true }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (items.isEmpty()) {
            EmptyState(
                Icons.Filled.CheckCircle,
                "Tempat Sampah kosong",
                "File yang kamu hapus lewat BersihKu disimpan di sini dulu selama ${Trash.KEEP_DAYS} hari, " +
                    "jadi masih bisa dikembalikan kalau salah hapus.",
                Modifier.padding(pad),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp),
        ) {
            item {
                InfoBanner(
                    "File di sini masih makan memori sampai Tempat Sampah dikosongkan. " +
                        "Otomatis terhapus permanen ${Trash.KEEP_DAYS} hari setelah dipindah.",
                    null,
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = {
                            vm.setSelection(SEL_TRASH, if (selected.size == items.size) emptySet() else items.map { it.id }.toSet())
                        },
                        label = { Text(if (selected.size == items.size) "Batal pilih" else "Pilih semua") },
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { confirmEmpty = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text("Kosongkan (${formatSize(total)})") }
                }
            }
            items(sorted, key = { it.id }) { item ->
                TrashRow(
                    item = item,
                    root = root,
                    checked = item.id in selection,
                    onToggle = { vm.toggle(SEL_TRASH, item.id) },
                    onOpen = { if (!item.isDir) openFile(ctx, item.file.path, item.name) },
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus permanen ${formatCount(selected.size)} item?",
            text = "Total ${formatSize(selected.sumOf { it.size })}. File yang dihapus permanen tidak bisa dikembalikan lagi.",
            confirmText = "Hapus permanen",
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            vm.deleteFromTrash(selected.map { it.id }.toSet())
        }
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = "Kosongkan Tempat Sampah?",
            text = "Semua ${formatCount(items.size)} item (${formatSize(total)}) akan dihapus permanen dan tidak bisa dikembalikan.",
            confirmText = "Kosongkan",
            onDismiss = { confirmEmpty = false },
        ) {
            confirmEmpty = false
            vm.emptyTrash()
        }
    }
}

@Composable
private fun TrashRow(item: Trash.Item, root: String, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.background)
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        FileThumb(
            item.file.path,
            if (item.isDir) FileType.LAINNYA else Scanner.typeOf(item.name),
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpen),
            isDir = item.isDir,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatSize(item.size)} • dihapus ${formatAgo(item.deletedAt)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Dari ${shortFolder(item.original.substringBeforeLast('/'), root)} • terhapus otomatis " +
                    if (item.daysLeft <= 0) "hari ini" else "${item.daysLeft} hari lagi",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
