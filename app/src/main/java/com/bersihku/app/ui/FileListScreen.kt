package com.bersihku.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.bersihku.app.data.DAY
import com.bersihku.app.data.Keys
import com.bersihku.app.data.Safety
import com.bersihku.app.data.ScannedFile
import com.bersihku.app.data.Scanner
import com.bersihku.app.data.Trash
import com.bersihku.app.data.formatAgo
import com.bersihku.app.data.formatCount
import com.bersihku.app.data.formatSize
import com.bersihku.app.data.openFile
import com.bersihku.app.data.shortFolder

private enum class SortBy(val label: String) {
    BESAR("Paling besar dulu"),
    LAMA("Paling lama dulu"),
    BARU("Paling baru dulu"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(vm: MainViewModel, key: String) {
    val ctx = LocalContext.current
    val r = vm.result
    val cat = remember(r, key) { vm.category(key) }
    if (r == null || cat == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val selKey = selListKey(key)
    val selection = vm.selectionOf(selKey)
    val isDup = key == Keys.KEMBAR
    var filter by remember(key) { mutableStateOf<String?>(null) }
    var sort by remember(key) { mutableStateOf(SortBy.BESAR) }
    var sortMenu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    val labeled = remember(cat) { cat.files.map { it to Scanner.subLabel(key, it) } }
    val chips = remember(labeled) {
        labeled.groupBy { it.second }
            .map { (label, list) -> label to list.sumOf { it.first.size } }
            .sortedByDescending { it.second }
    }
    val shown: List<ScannedFile> = remember(labeled, filter, sort) {
        val base = labeled.filter { filter == null || it.second == filter }.map { it.first }
        when (sort) {
            SortBy.BESAR -> base.sortedByDescending { it.size }
            SortBy.LAMA -> base.sortedBy { it.modified }
            SortBy.BARU -> base.sortedByDescending { it.modified }
        }
    }
    val selectedFiles = remember(selection, cat) { cat.files.filter { it.path in selection } }
    val selectedBytes = remember(selectedFiles) { selectedFiles.sumOf { it.size } }

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = cat.title,
                subtitle = "${formatCount(cat.files.size)} file • ${formatSize(cat.totalSize)}",
                onBack = { vm.back() },
                actions = {
                    if (!isDup) {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Urutkan")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            for (s in SortBy.entries) {
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    onClick = { sort = s; sortMenu = false },
                                    leadingIcon = if (s == sort) {
                                        { Icon(Icons.Filled.CheckCircle, null, tint = Mint) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selection.isNotEmpty()) {
                BottomActionBar(selectedFiles.size, selectedBytes) {
                    DangerButton("Hapus") {
                        if (isDup) {
                            // Pengaman: tiap grup minimal 1 file tetap disimpan
                            val fixed = selection.toMutableSet()
                            var adjusted = false
                            for (g in cat.dupGroups) {
                                if (g.all { it.path in fixed }) {
                                    fixed.remove(g[0].path)
                                    adjusted = true
                                }
                            }
                            if (adjusted) {
                                vm.setSelection(selKey, fixed)
                                vm.toast("1 salinan dari tiap file tetap disimpan.")
                            }
                        }
                        confirm = true
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp),
        ) {
            item { InfoBanner(cat.description, cat.safety) }

            if (!isDup && chips.size > 1) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = filter == null,
                            onClick = { filter = null },
                            label = { Text("Semua") },
                        )
                        for ((label, size) in chips) {
                            FilterChip(
                                selected = filter == label,
                                onClick = { filter = if (filter == label) null else label },
                                label = { Text("$label • ${formatSize(size)}") },
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isDup) {
                        AssistChip(
                            onClick = { vm.setSelection(selKey, cat.preselected) },
                            label = { Text("Pilih semua salinan") },
                        )
                    } else {
                        AssistChip(
                            onClick = { vm.setSelection(selKey, selection + shown.map { it.path }) },
                            label = { Text("Pilih semua") },
                        )
                        AssistChip(
                            onClick = {
                                val limit = System.currentTimeMillis() - 90 * DAY
                                vm.setSelection(selKey, selection + shown.filter { it.modified < limit }.map { it.path })
                            },
                            label = { Text("Pilih yang > 3 bulan") },
                        )
                    }
                    if (selection.isNotEmpty()) {
                        AssistChip(
                            onClick = { vm.setSelection(selKey, emptySet()) },
                            label = { Text("Batal pilih") },
                        )
                    }
                }
            }

            if (isDup) {
                cat.dupGroups.forEachIndexed { gi, g ->
                    item(key = "grup_$gi") {
                        GroupHeader(gi + 1, g.size, g[0].size)
                    }
                    items(g, key = { it.path }) { f ->
                        val checked = f.path in selection
                        FileRow(
                            f = f,
                            root = r.root,
                            checked = checked,
                            badge = if (!checked) "Disimpan" else null,
                            onToggle = { vm.toggle(selKey, f.path) },
                            onOpen = { openFile(ctx, f.path) },
                        )
                    }
                }
            } else {
                items(shown, key = { it.path }) { f ->
                    FileRow(
                        f = f,
                        root = r.root,
                        checked = f.path in selection,
                        badge = null,
                        onToggle = { vm.toggle(selKey, f.path) },
                        onOpen = { openFile(ctx, f.path) },
                    )
                }
            }
        }
    }

    if (confirm) {
        val targets = remember(selection, cat) {
            cat.files.filter { it.path in selection }.map { Trash.Target(it.path, it.size, false) }
        }
        DeleteDialog(
            count = targets.size,
            bytes = targets.sumOf { it.size },
            permanentOnly = cat.safety == Safety.AMAN,
            onDismiss = { confirm = false },
        ) { permanent ->
            confirm = false
            vm.delete(targets, permanent)
        }
    }
}

@Composable
fun InfoBanner(text: String, safety: Safety?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Info,
                null,
                tint = if (safety == Safety.CEK_DULU) AmberSoft else Mint,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text, style = MaterialTheme.typography.bodySmall)
                if (safety != null) {
                    Spacer(Modifier.height(6.dp))
                    SafetyChip(safety)
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(number: Int, count: Int, size: Long) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "File kembar #$number — $count salinan, masing-masing ${formatSize(size)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun FileRow(
    f: ScannedFile,
    root: String,
    checked: Boolean,
    badge: String?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.background)
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        FileThumb(
            f.path,
            f.type,
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpen),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatSize(f.size)} • ${formatAgo(f.modified)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = Mint.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                        Text(
                            badge,
                            color = Mint,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Text(
                shortFolder(f.parent, root),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpen) {
            Icon(Icons.Filled.Visibility, contentDescription = "Lihat file", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
