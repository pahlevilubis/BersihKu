package com.bersihku.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bersihku.app.data.Category
import com.bersihku.app.data.FileType
import com.bersihku.app.data.Keys
import com.bersihku.app.data.Perms
import com.bersihku.app.data.ScanProgress
import com.bersihku.app.data.ScanResult
import com.bersihku.app.data.StorageInfo
import com.bersihku.app.data.Trash
import com.bersihku.app.data.formatAgo
import com.bersihku.app.data.formatCount
import com.bersihku.app.data.formatSize

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val r = vm.result
    val p = vm.progress
    val legacyPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshPermissions()
    }
    val safe = remember(r) { vm.safeTargets() }
    val safeBytes = remember(safe) { safe.sumOf { it.size } }
    var confirmSafe by remember { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    val sys = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = sys.calculateTopPadding() + 12.dp,
            bottom = sys.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header() }
        item {
            StorageCard(vm.storage, if (p == null) r else null) { t ->
                vm.open(Screen.FileList(Keys.JENIS + t.name))
            }
        }

        if (!vm.hasFileAccess) {
            item {
                PermissionCard {
                    if (Build.VERSION.SDK_INT >= 30) {
                        Perms.openAllFilesSettings(ctx)
                    } else {
                        legacyPerm.launch(
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        )
                    }
                }
            }
        } else if (p != null) {
            item { ScanProgressCard(p) { vm.cancelScan() } }
        } else {
            item { ScanButton(r) { vm.startScan() } }
        }

        if (r != null && p == null) {
            item { SafeCleanCard(safe.size, safeBytes) { confirmSafe = true } }
            val cats = r.categories.filter { it.files.isNotEmpty() }.sortedByDescending { it.headlineSize }
            if (cats.isNotEmpty()) {
                item { SectionTitle("Hasil pemeriksaan", "Ketuk untuk melihat & memilih file") }
            }
            items(cats, key = { it.key }) { c ->
                CategoryCard(c) { vm.open(Screen.FileList(c.key)) }
            }
            item { FoldersCard(r) { path -> vm.open(Screen.Folder(path)) } }
        }

        item { SectionTitle("Lainnya", null) }
        item {
            NavCard(
                Icons.Filled.Apps,
                Mint,
                "Aplikasi jarang dipakai",
                "Lihat aplikasi yang lama tidak dibuka beserta ukurannya, lalu hapus yang tidak perlu.",
            ) { vm.open(Screen.AppList) }
        }
        if (vm.trashItems.isNotEmpty()) {
            item {
                TrashCard(vm.trashItems, onOpen = { vm.open(Screen.TrashBin) }, onEmpty = { confirmEmptyTrash = true })
            }
        }
        item { Footer() }
    }

    if (confirmSafe) {
        DeleteDialog(
            count = safe.size,
            bytes = safeBytes,
            permanentOnly = true,
            onDismiss = { confirmSafe = false },
        ) {
            confirmSafe = false
            vm.delete(safe, permanent = true)
        }
    }
    if (confirmEmptyTrash) {
        ConfirmDialog(
            title = "Kosongkan Tempat Sampah?",
            text = "Semua ${formatCount(vm.trashItems.size)} item (${formatSize(vm.trashItems.sumOf { it.size })}) " +
                "akan dihapus permanen dan tidak bisa dikembalikan.",
            confirmText = "Kosongkan",
            onDismiss = { confirmEmptyTrash = false },
        ) {
            confirmEmptyTrash = false
            vm.emptyTrash()
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Mint)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("BersihKu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Bersihkan memori HP dengan mudah",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String?) {
    Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StorageCard(info: StorageInfo, r: ScanResult?, onType: (FileType) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Memori HP", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatSize(info.used), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "terpakai dari ${formatSize(info.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            val other = if (r != null) (info.used - r.scannedBytes).coerceAtLeast(0L) else 0L
            val segments: List<Pair<Color, Long>> = if (r == null) {
                listOf((if (info.usedFraction > 0.9f) Danger else Mint) to info.used)
            } else {
                FileType.entries.map { typeColor(it) to (r.typeSizes[it] ?: 0L) } + (SystemGray to other)
            }
            UsageBar(segments, info.total)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info.usedFraction > 0.9f) {
                    Icon(Icons.Filled.Warning, null, tint = Danger, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Memori hampir penuh! Sisa ${formatSize(info.free)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger,
                    )
                } else {
                    Text(
                        "Sisa ${formatSize(info.free)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (r != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Apa yang makan memori? (ketuk untuk lihat file-nya)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                val types = FileType.entries
                    .filter { (r.typeSizes[it] ?: 0L) > 0L }
                    .sortedByDescending { r.typeSizes[it] ?: 0L }
                for (t in types) {
                    LegendRow(typeColor(t), t.label, r.typeSizes[t] ?: 0L) { onType(t) }
                }
                LegendRow(SystemGray, "Aplikasi, data aplikasi & sistem", other, null)
            }
        }
    }
}

@Composable
private fun UsageBar(segments: List<Pair<Color, Long>>, total: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (total > 0L) {
            var usedWeight = 0f
            for ((color, size) in segments) {
                val w = size.toFloat() / total
                if (w > 0.003f) {
                    usedWeight += w
                    Box(
                        Modifier
                            .weight(w)
                            .fillMaxHeight()
                            .background(color)
                    )
                }
            }
            val rest = 1f - usedWeight
            if (rest > 0.003f) Spacer(Modifier.weight(rest))
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, size: Long, onClick: (() -> Unit)?) {
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(formatSize(size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (onClick != null) {
            Icon(
                Icons.Filled.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Spacer(Modifier.width(18.dp))
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = AmberSoft)
                Spacer(Modifier.width(8.dp))
                Text("Izinkan BersihKu memeriksa file", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Supaya bisa mencari file sampah, foto & video besar, dan file kembar, BersihKu perlu izin " +
                    "\"Akses semua file\". Di halaman berikutnya, nyalakan tombol izinnya lalu tekan Kembali.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Semua pemeriksaan dilakukan di HP-mu sendiri. BersihKu tidak memakai internet sama sekali.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text("Beri Izin") }
        }
    }
}

@Composable
private fun ScanButton(r: ScanResult?, onScan: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (r == null) {
            Button(
                onClick = onScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Filled.Search, null)
                Spacer(Modifier.width(8.dp))
                Text("Periksa HP Sekarang", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Butuh sekitar 1 menit. Tidak ada file yang dihapus tanpa persetujuanmu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Periksa Ulang")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Terakhir diperiksa ${timeAgo(r.finishedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun timeAgo(t: Long): String {
    val min = ((System.currentTimeMillis() - t) / 60_000L).toInt()
    return when {
        min < 1 -> "baru saja"
        min < 60 -> "$min menit lalu"
        min < 24 * 60 -> "${min / 60} jam lalu"
        else -> formatAgo(t)
    }
}

@Composable
private fun ScanProgressCard(p: ScanProgress, onCancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (p.phase) {
                            0 -> "Memeriksa isi HP…"
                            1 -> "Mengelompokkan file…"
                            else -> "Mencari file kembar… ${p.percent}%"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${formatCount(p.files)} file sudah diperiksa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (p.phase == 0 && p.current.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    p.current,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (p.phase == 2) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { p.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Batal") }
            }
        }
    }
}

@Composable
private fun SafeCleanCard(count: Int, bytes: Long, onClean: () -> Unit) {
    if (bytes <= 0L) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Mint)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Tidak ada file sampah. Untuk melegakan lebih banyak, cek kategori di bawah.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Mint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bisa langsung dibersihkan (aman)", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                formatSize(bytes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Mint,
            )
            Text(
                "${formatCount(count)} file: file sampah, isi tempat sampah galeri, installer APK, " +
                    "dan salinan file kembar (1 salinan tetap disimpan).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClean,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.CleaningServices, null)
                Spacer(Modifier.width(8.dp))
                Text("Bersihkan Sekarang", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun CategoryCard(c: Category, onClick: () -> Unit) {
    val tint = if (c.safety == com.bersihku.app.data.Safety.AMAN) Mint else AmberSoft
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(categoryIcon(c.key), null, tint = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    c.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SafetyChip(c.safety)
                    Spacer(Modifier.width(8.dp))
                    val countText = if (c.key == Keys.KEMBAR) {
                        "${formatCount(c.files.size - c.dupGroups.size)} salinan"
                    } else {
                        "${formatCount(c.files.size)} file"
                    }
                    Text(countText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatSize(c.headlineSize), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FoldersCard(r: ScanResult, onOpen: (String) -> Unit) {
    val top = remember(r) {
        r.dirSizes.entries
            .filter { it.key.substringBeforeLast('/') == r.root && it.value > 0L }
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }
    }
    val rootSize = (r.dirSizes[r.root] ?: 0L).coerceAtLeast(1L)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FolderOpen, null, tint = AmberSoft)
                Spacer(Modifier.width(8.dp))
                Text("Folder paling besar", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Lihat folder mana yang paling banyak makan memori.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            for ((path, size) in top) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpen(path) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            path.substringAfterLast('/'),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(formatSize(size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    UsageBar(listOf(AmberSoft to size), rootSize)
                }
            }
            TextButton(onClick = { onOpen(r.root) }) {
                Text("Jelajahi semua folder")
                Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NavCard(icon: ImageVector, tint: Color, title: String, text: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrashCard(items: List<Trash.Item>, onOpen: () -> Unit, onEmpty: () -> Unit) {
    val total = items.sumOf { it.size }
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Danger.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.RestoreFromTrash, null, tint = Danger)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tempat Sampah BersihKu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatCount(items.size)} item • ${formatSize(total)}. Masih makan memori sampai dikosongkan " +
                            "(otomatis setelah ${Trash.KEEP_DAYS} hari).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpen) { Text("Lihat isinya") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onEmpty) { Text("Kosongkan") }
            }
        }
    }
}

@Composable
private fun Footer() {
    Text(
        "BersihKu 1.0 • dibuat oleh Pahlevi Lubis",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
}
