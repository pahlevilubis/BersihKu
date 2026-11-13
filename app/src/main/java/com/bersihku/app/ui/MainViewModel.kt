package com.bersihku.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bersihku.app.data.AppInfo
import com.bersihku.app.data.Apps
import com.bersihku.app.data.Category
import com.bersihku.app.data.FileType
import com.bersihku.app.data.Keys
import com.bersihku.app.data.Perms
import com.bersihku.app.data.Safety
import com.bersihku.app.data.ScanProgress
import com.bersihku.app.data.ScanResult
import com.bersihku.app.data.Scanner
import com.bersihku.app.data.Storage
import com.bersihku.app.data.StorageInfo
import com.bersihku.app.data.Trash
import com.bersihku.app.data.formatCount
import com.bersihku.app.data.formatSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data class FileList(val key: String) : Screen
    data class Folder(val path: String) : Screen
    data object AppList : Screen
    data object TrashBin : Screen
}

class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

const val SEL_TRASH = "trash"
const val SEL_APPS = "apps"
fun selListKey(key: String) = "list:$key"
fun selFolderKey(path: String) = "folder:$path"

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()

    var stack by mutableStateOf<List<Screen>>(listOf(Screen.Home))
        private set
    val screen: Screen get() = stack.last()

    var hasFileAccess by mutableStateOf(false)
        private set
    var hasUsageAccess by mutableStateOf(false)
        private set
    var storage by mutableStateOf(StorageInfo(0L, 0L))
        private set
    var result by mutableStateOf<ScanResult?>(null)
        private set
    var progress by mutableStateOf<ScanProgress?>(null)
        private set
    var trashItems by mutableStateOf<List<Trash.Item>>(emptyList())
        private set
    var apps by mutableStateOf<List<AppInfo>?>(null)
        private set
    var appsLoading by mutableStateOf(false)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<UiMessage?>(null)
        private set

    /** Pilihan (centang) per layar. */
    val selections = mutableStateMapOf<String, Set<String>>()

    private var scanJob: Job? = null
    private val uninstallQueue = ArrayDeque<String>()

    init {
        refreshPermissions()
        storage = Storage.read(ctx)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (Perms.hasFileAccess(ctx)) Trash.purgeOld()
                } catch (_: Exception) {
                }
            }
            refreshTrash()
        }
    }

    // ---------------- Navigasi ----------------

    fun open(s: Screen) {
        if (s is Screen.FileList) {
            val k = selListKey(s.key)
            if (k !in selections) selections[k] = category(s.key)?.preselected ?: emptySet()
        }
        stack = stack + s
    }

    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    // ---------------- Izin & info memori ----------------

    fun refreshPermissions() {
        hasFileAccess = Perms.hasFileAccess(ctx)
        hasUsageAccess = Perms.hasUsageAccess(ctx)
    }

    fun onResume() {
        val hadFiles = hasFileAccess
        refreshPermissions()
        storage = Storage.read(ctx)
        if (!hadFiles && hasFileAccess) refreshTrash()
        apps?.let { list -> apps = list.filter { Apps.isInstalled(ctx, it.pkg) } }
    }

    // ---------------- Pemeriksaan ----------------

    fun startScan() {
        if (scanJob?.isActive == true) return
        selections.clear()
        progress = ScanProgress(0, 0, 0, "")
        scanJob = viewModelScope.launch {
            try {
                result = Scanner.scan { p -> progress = p }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message = UiMessage("Pemeriksaan gagal: ${e.message ?: "kesalahan tidak dikenal"}")
            } finally {
                progress = null
                storage = Storage.read(ctx)
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    fun category(key: String): Category? {
        val r = result ?: return null
        if (key.startsWith(Keys.JENIS)) {
            val t = runCatching { FileType.valueOf(key.removePrefix(Keys.JENIS)) }.getOrNull() ?: return null
            return Scanner.typeCategory(r, t)
        }
        return r.categories.firstOrNull { it.key == key }
    }

    /** File yang aman dibersihkan sekaligus (sampah, isi tempat sampah galeri, APK, salinan kembar). */
    fun safeTargets(): List<Trash.Target> {
        val r = result ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<Trash.Target>()
        for (c in r.categories) {
            if (c.safety != Safety.AMAN) continue
            for (f in c.files) {
                if (f.path in c.preselected && seen.add(f.path)) out.add(Trash.Target(f.path, f.size, false))
            }
        }
        return out
    }

    // ---------------- Pilihan ----------------

    fun selectionOf(key: String): Set<String> = selections[key] ?: emptySet()

    fun setSelection(key: String, sel: Set<String>) {
        selections[key] = sel
    }

    fun toggle(key: String, path: String) {
        val s = selectionOf(key)
        selections[key] = if (path in s) s - path else s + path
    }

    // ---------------- Hapus ----------------

    fun delete(targets: List<Trash.Target>, permanent: Boolean) {
        if (targets.isEmpty()) return
        viewModelScope.launch {
            busy = if (permanent) "Menghapus ${formatCount(targets.size)} item…" else "Memindahkan ke Tempat Sampah…"
            val out = withContext(Dispatchers.IO) {
                if (permanent) Trash.deleteNow(ctx, targets) else Trash.moveIn(ctx, targets)
            }
            val done = HashSet<String>(out.donePaths)
            done.addAll(out.doneDirs)
            result = withContext(Dispatchers.Default) { result?.without(HashSet(out.donePaths), out.doneDirs) }
            for (k in selections.keys.toList()) {
                val s = selections[k] ?: continue
                if (s.any { it in done }) selections[k] = s.filterTo(HashSet()) { it !in done }
            }
            storage = Storage.read(ctx)
            refreshTrash()
            busy = null
            val n = done.size
            val failNote = if (out.failed > 0) " (${formatCount(out.failed)} gagal)" else ""
            message = when {
                n == 0 -> UiMessage("Gagal menghapus. File mungkin sedang dipakai aplikasi lain.")
                permanent -> UiMessage("Berhasil! ${formatSize(out.bytes)} sudah lega.$failNote")
                else -> UiMessage(
                    "${formatCount(n)} item (${formatSize(out.bytes)}) dipindah ke Tempat Sampah.$failNote " +
                        "Memori lega setelah dikosongkan.",
                    "Kosongkan",
                ) { emptyTrash() }
            }
        }
    }

    // ---------------- Tempat Sampah ----------------

    fun refreshTrash() {
        viewModelScope.launch {
            trashItems = withContext(Dispatchers.IO) {
                try {
                    Trash.load()
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    fun restore(ids: Set<String>) {
        viewModelScope.launch {
            busy = "Mengembalikan file…"
            val n = withContext(Dispatchers.IO) { Trash.restore(ctx, ids) }
            selections[SEL_TRASH] = emptySet()
            refreshTrash()
            storage = Storage.read(ctx)
            busy = null
            message = UiMessage("${formatCount(n)} item dikembalikan ke tempat asalnya.")
        }
    }

    fun deleteFromTrash(ids: Set<String>) {
        viewModelScope.launch {
            busy = "Menghapus permanen…"
            val bytes = withContext(Dispatchers.IO) { Trash.deleteForever(ids) }
            selections[SEL_TRASH] = emptySet()
            refreshTrash()
            storage = Storage.read(ctx)
            busy = null
            message = UiMessage("Berhasil! ${formatSize(bytes)} sudah lega.")
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            busy = "Mengosongkan Tempat Sampah…"
            val bytes = withContext(Dispatchers.IO) { Trash.emptyAll() }
            selections[SEL_TRASH] = emptySet()
            refreshTrash()
            storage = Storage.read(ctx)
            busy = null
            message = UiMessage("Tempat Sampah kosong. ${formatSize(bytes)} sudah lega.")
        }
    }

    // ---------------- Aplikasi ----------------

    fun loadApps(force: Boolean = false) {
        if (appsLoading) return
        if (apps != null && !force) return
        viewModelScope.launch {
            appsLoading = true
            apps = Apps.load(ctx)
            appsLoading = false
        }
    }

    fun queueUninstall(pkgs: List<String>) {
        uninstallQueue.clear()
        uninstallQueue.addAll(pkgs)
    }

    fun nextUninstall(): String? = uninstallQueue.removeFirstOrNull()

    // ---------------- Pesan ----------------

    fun toast(text: String) {
        message = UiMessage(text)
    }

    fun consumeMessage() {
        message = null
    }
}
