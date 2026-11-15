package com.bersihku.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bersihku.app.data.FileType
import com.bersihku.app.data.Keys
import com.bersihku.app.data.Safety
import com.bersihku.app.data.formatCount
import com.bersihku.app.data.formatSize
import java.io.File

@Suppress("DEPRECATION")
fun typeIcon(t: FileType): ImageVector = when (t) {
    FileType.FOTO -> Icons.Filled.Image
    FileType.VIDEO -> Icons.Filled.Movie
    FileType.AUDIO -> Icons.Filled.MusicNote
    FileType.DOKUMEN -> Icons.Filled.Description
    FileType.APK -> Icons.Filled.Android
    FileType.ARSIP -> Icons.Filled.Archive
    FileType.LAINNYA -> Icons.Filled.InsertDriveFile
}

fun categoryIcon(key: String): ImageVector = when {
    key == Keys.SAMPAH -> Icons.Filled.CleaningServices
    key == Keys.SAMPAH_GALERI -> Icons.Filled.DeleteSweep
    key == Keys.KEMBAR -> Icons.Filled.ContentCopy
    key == Keys.APK -> Icons.Filled.Android
    key == Keys.WHATSAPP -> Icons.Filled.Forum
    key == Keys.TELEGRAM -> Icons.AutoMirrored.Filled.Send
    key == Keys.BESAR -> Icons.Filled.Storage
    key == Keys.SCREENSHOT -> Icons.Filled.Screenshot
    key == Keys.DOWNLOAD -> Icons.Filled.Download
    key.startsWith(Keys.JENIS) ->
        runCatching { typeIcon(FileType.valueOf(key.removePrefix(Keys.JENIS))) }.getOrDefault(Icons.Filled.Folder)
    else -> Icons.Filled.Folder
}

/** Gambar kecil: foto/video tampil thumbnail-nya, jenis lain tampil ikon. */
@Composable
fun FileThumb(
    path: String,
    type: FileType,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    isDir: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isDir) Icons.Filled.Folder else typeIcon(type),
            contentDescription = null,
            tint = if (isDir) AmberSoft else typeColor(type),
            modifier = Modifier.size(size * 0.5f),
        )
        if (!isDir && (type == FileType.FOTO || type == FileType.VIDEO)) {
            val ctx = LocalContext.current
            val request = remember(path) {
                ImageRequest.Builder(ctx).data(File(path)).size(192).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (type == FileType.VIDEO) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun SafetyChip(safety: Safety) {
    val text = if (safety == Safety.AMAN) "Aman dihapus" else "Cek dulu"
    val color = if (safety == Safety.AMAN) Mint else AmberSoft
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Bar bawah yang muncul saat ada yang dipilih. */
@Composable
fun BottomActionBar(count: Int, bytes: Long, content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("${formatCount(count)} dipilih", style = MaterialTheme.typography.titleSmall)
                Text(formatSize(bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}

/**
 * Konfirmasi hapus.
 * [permanentOnly] = file aman (sampah dll) → langsung dihapus permanen.
 * Selain itu → dipindah ke Tempat Sampah, kecuali pengguna mencentang "hapus permanen".
 */
@Composable
fun DeleteDialog(
    count: Int,
    bytes: Long,
    permanentOnly: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (permanent: Boolean) -> Unit,
) {
    var permanent by remember { mutableStateOf(permanentOnly) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, null) },
        title = { Text("Hapus ${formatCount(count)} item?") },
        text = {
            Column {
                Text("Total ${formatSize(bytes)}.", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (permanentOnly) {
                    Text("File ini tidak dibutuhkan, jadi langsung dihapus dan memori langsung lega.")
                } else {
                    Text(
                        if (permanent) {
                            "File akan dihapus permanen dan TIDAK bisa dikembalikan."
                        } else {
                            "File dipindah ke Tempat Sampah BersihKu dulu. Masih bisa dikembalikan selama 7 hari."
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { permanent = !permanent },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = permanent, onCheckedChange = { permanent = it })
                        Text("Langsung hapus permanen", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(permanent) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Hapus") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Layar gelap + tulisan saat sedang menghapus (sentuhan diblokir). */
@Composable
fun BusyOverlay(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Card {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(text)
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Mint, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Satu tombol di bar bawah (ikon + tulisan). */
class BarAction(
    val label: String,
    val icon: ImageVector,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/** Bar bawah untuk file yang dipilih: jumlah + tombol Pindahkan / Ganti nama / Hapus. */
@Composable
fun FileActionBar(count: Int, bytes: Long, onClear: () -> Unit, actions: List<BarAction>) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${formatCount(count)} dipilih • ${formatSize(bytes)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("Batal") }
            }
            Row(Modifier.fillMaxWidth()) {
                for (a in actions) {
                    val color = if (a.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = a.onClick)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(a.icon, contentDescription = null, tint = color)
                        Spacer(Modifier.height(2.dp))
                        Text(a.label, style = MaterialTheme.typography.labelMedium, color = color)
                    }
                }
            }
        }
    }
}

/**
 * Kotak isian nama (folder baru / ganti nama).
 * [onConfirm] mengembalikan pesan kesalahan, atau null kalau berhasil.
 */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmText: String,
    selectWholeName: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> String?,
) {
    val selEnd = if (selectWholeName) initial.length else initial.substringBeforeLast('.', initial).length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, selEnd))) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    singleLine = true,
                    isError = error != null,
                    placeholder = { Text("Ketik nama…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                val e = error
                if (e != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                LaunchedEffect(Unit) {
                    runCatching { focus.requestFocus() }
                }
            }
        },
        confirmButton = {
            Button(onClick = { error = onConfirm(value.text) }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
