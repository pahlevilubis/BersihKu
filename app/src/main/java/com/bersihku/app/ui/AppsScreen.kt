package com.bersihku.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bersihku.app.data.AppInfo
import com.bersihku.app.data.Perms
import com.bersihku.app.data.formatAgo
import com.bersihku.app.data.formatCount
import com.bersihku.app.data.formatSize

private enum class AppFilter(val label: String) {
    JARANG("Jarang dipakai (30+ hari)"),
    BESAR("Paling besar"),
    SEMUA("Semua (A–Z)"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var filter by remember { mutableStateOf(AppFilter.JARANG) }
    var showSystem by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val selection = vm.selectionOf(SEL_APPS)

    // Setelah dialog uninstall ditutup → lanjut ke aplikasi berikutnya di antrean
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.nextUninstall()?.let { Perms.uninstall(ctx, it) }
    }
    LaunchedEffect(vm.hasUsageAccess) {
        if (vm.hasUsageAccess) vm.loadApps()
    }

    val apps = vm.apps
    val shown = remember(apps, filter, showSystem) {
        val base = (apps ?: emptyList()).filter { showSystem || !it.system }
        when (filter) {
            AppFilter.JARANG -> base.filter { it.idleDays >= 30 }.sortedByDescending { it.idleDays }
            AppFilter.BESAR -> base.sortedByDescending { it.totalBytes }
            AppFilter.SEMUA -> base.sortedBy { it.label.lowercase() }
        }
    }
    val selectedApps = remember(apps, selection) { (apps ?: emptyList()).filter { it.pkg in selection } }

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = "Aplikasi",
                subtitle = apps?.let { list ->
                    val user = list.filter { !it.system }
                    "${formatCount(user.size)} aplikasi terpasang • ${formatSize(user.sumOf { it.totalBytes })}"
                },
                onBack = { vm.back() },
                actions = {
                    if (vm.hasUsageAccess) {
                        IconButton(onClick = { vm.loadApps(force = true) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selectedApps.isNotEmpty()) {
                BottomActionBar(selectedApps.size, selectedApps.sumOf { it.totalBytes }) {
                    DangerButton("Hapus aplikasi") { confirm = true }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (!vm.hasUsageAccess) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(16.dp)
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Butuh izin \"Akses penggunaan\"", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Supaya bisa tahu aplikasi mana yang sudah lama tidak dibuka dan berapa ukurannya, " +
                                "BersihKu perlu izin ini. Di halaman berikutnya, pilih BersihKu lalu nyalakan izinnya, " +
                                "kemudian tekan Kembali.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { Perms.openUsageSettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Beri Izin")
                        }
                    }
                }
            }
            return@Scaffold
        }

        if (apps == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Membaca daftar aplikasi…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp),
        ) {
            item {
                InfoBanner(
                    "Centang aplikasi yang tidak perlu lalu tekan Hapus. Untuk membersihkan cache saja " +
                        "(misalnya WhatsApp, TikTok, Chrome), ketuk ⚙ di samping aplikasi → Penyimpanan → Hapus cache.",
                    null,
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (f in AppFilter.entries) {
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(f.label) },
                        )
                    }
                    FilterChip(
                        selected = showSystem,
                        onClick = { showSystem = !showSystem },
                        label = { Text("Tampilkan aplikasi bawaan") },
                    )
                }
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        if (filter == AppFilter.JARANG) Icons.Filled.CheckCircle else Icons.Filled.Apps,
                        if (filter == AppFilter.JARANG) "Semua aplikasi masih dipakai" else "Tidak ada aplikasi",
                        if (filter == AppFilter.JARANG) "Tidak ada aplikasi yang lebih dari 30 hari tidak dibuka." else "",
                    )
                }
            }
            items(shown, key = { it.pkg }) { app ->
                AppRow(
                    app = app,
                    checked = app.pkg in selection,
                    onToggle = { vm.toggle(SEL_APPS, app.pkg) },
                    onSettings = { Perms.openAppInfo(ctx, app.pkg) },
                )
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Hapus ${formatCount(selectedApps.size)} aplikasi?",
            text = "Android akan meminta konfirmasi untuk tiap aplikasi. Data di dalam aplikasi tersebut ikut terhapus. " +
                "Total ${formatSize(selectedApps.sumOf { it.totalBytes })}.",
            confirmText = "Lanjut",
            onDismiss = { confirm = false },
        ) {
            confirm = false
            vm.queueUninstall(selectedApps.map { it.pkg })
            vm.setSelection(SEL_APPS, emptySet())
            vm.nextUninstall()?.let { Perms.uninstall(ctx, it) }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onToggle: () -> Unit, onSettings: () -> Unit) {
    val canRemove = !app.system
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.background)
            .clickable(onClick = if (canRemove) onToggle else onSettings)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canRemove) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        } else {
            Spacer(Modifier.width(48.dp))
        }
        val icon = app.icon
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(44.dp))
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Apps, null) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val usage = if (app.neverUsed) {
                "Belum pernah dibuka • dipasang ${formatAgo(app.installed)}"
            } else {
                "Terakhir dibuka ${formatAgo(app.lastUsed)}"
            }
            Text(
                usage,
                style = MaterialTheme.typography.bodySmall,
                color = if (app.idleDays >= 90) AmberSoft else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append("Ukuran ${formatSize(app.totalBytes)}")
                    if (app.cacheBytes > 1_000_000L) append(" • cache ${formatSize(app.cacheBytes)}")
                    if (app.system) append(" • bawaan HP")
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Pengaturan aplikasi", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
