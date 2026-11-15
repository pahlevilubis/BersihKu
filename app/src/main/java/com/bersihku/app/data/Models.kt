package com.bersihku.app.data

enum class FileType(val label: String) {
    FOTO("Foto"),
    VIDEO("Video"),
    AUDIO("Musik & suara"),
    DOKUMEN("Dokumen"),
    APK("Installer APK"),
    ARSIP("Arsip ZIP/RAR"),
    LAINNYA("Lainnya"),
}

class ScannedFile(
    val path: String,
    val size: Long,
    val modified: Long,
    val type: FileType,
) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = path.substringBeforeLast('/')
}

/** AMAN = boleh langsung dihapus permanen; CEK_DULU = dipindah ke Tempat Sampah dulu. */
enum class Safety { AMAN, CEK_DULU }

object Keys {
    const val SAMPAH = "sampah"
    const val SAMPAH_GALERI = "sampah_galeri"
    const val KEMBAR = "kembar"
    const val APK = "apk"
    const val WHATSAPP = "whatsapp"
    const val TELEGRAM = "telegram"
    const val BESAR = "besar"
    const val SCREENSHOT = "screenshot"
    const val DOWNLOAD = "download"
    const val JENIS = "jenis:"
}

data class Category(
    val key: String,
    val title: String,
    val description: String,
    val safety: Safety,
    val files: List<ScannedFile>,
    /** File yang otomatis dicentang saat daftar dibuka. */
    val preselected: Set<String>,
    /** Khusus file kembar: tiap grup berisi file yang isinya sama. File pertama = yang disimpan. */
    val dupGroups: List<List<ScannedFile>> = emptyList(),
) {
    val totalSize: Long = files.sumOf { it.size }

    /** Ukuran yang bisa dilegakan (file kembar: hanya salinan tambahannya). */
    val headlineSize: Long =
        if (key == Keys.KEMBAR) dupGroups.sumOf { g -> g.drop(1).sumOf { it.size } } else totalSize

    fun without(gone: (String) -> Boolean): Category {
        if (key == Keys.KEMBAR) {
            val groups = dupGroups.map { g -> g.filterNot { gone(it.path) } }.filter { it.size > 1 }
            return copy(
                files = groups.flatten(),
                preselected = groups.flatMap { g -> g.drop(1) }.map { it.path }.toHashSet(),
                dupGroups = groups,
            )
        }
        return copy(
            files = files.filterNot { gone(it.path) },
            preselected = preselected.filterTo(HashSet()) { !gone(it) },
        )
    }

    /** Ganti lokasi file yang sudah dipindah (map mengembalikan null kalau file tidak dipindah). */
    fun remap(map: (String) -> String?): Category {
        fun moved(f: ScannedFile): ScannedFile {
            val n = map(f.path) ?: return f
            return ScannedFile(n, f.size, f.modified, f.type)
        }
        return copy(
            files = files.map { moved(it) },
            preselected = preselected.mapTo(HashSet()) { map(it) ?: it },
            dupGroups = dupGroups.map { g -> g.map { moved(it) } },
        )
    }
}

/** Satu file/folder yang berhasil dipindah. */
data class Moved(val from: String, val to: String, val isDir: Boolean, val size: Long)

class ScanResult(
    val root: String,
    val allFiles: List<ScannedFile>,
    /** Ukuran total tiap folder (termasuk isi sub-folder). */
    val dirSizes: HashMap<String, Long>,
    val categories: List<Category>,
    val finishedAt: Long,
) {
    val scannedBytes: Long = allFiles.sumOf { it.size }
    val typeSizes: Map<FileType, Long> = allFiles.groupBy { it.type }.mapValues { e -> e.value.sumOf { it.size } }

    /** Hasil baru setelah sebagian file/folder dihapus. */
    fun without(removedFiles: Set<String>, removedDirs: List<String>): ScanResult {
        fun gone(p: String): Boolean =
            p in removedFiles || removedDirs.any { d -> p == d || p.startsWith("$d/") }

        val (removed, kept) = allFiles.partition { gone(it.path) }
        val sizes = HashMap(dirSizes)
        for (f in removed) {
            var d = f.parent
            while (d.length >= root.length) {
                val cur = sizes[d]
                if (cur != null) sizes[d] = (cur - f.size).coerceAtLeast(0L)
                if (d == root || !d.contains('/')) break
                d = d.substringBeforeLast('/')
            }
        }
        for (rd in removedDirs) {
            sizes.keys.removeAll { it == rd || it.startsWith("$rd/") }
        }
        return ScanResult(
            root = root,
            allFiles = kept,
            dirSizes = sizes,
            categories = categories.map { it.without(::gone) },
            finishedAt = finishedAt,
        )
    }

    /** Hasil baru setelah file/folder dipindah ke tempat lain. */
    fun relocate(moves: List<Moved>): ScanResult {
        if (moves.isEmpty()) return this
        val sizes = HashMap(dirSizes)
        fun addUp(start: String, delta: Long) {
            var d = start
            while (d.length >= root.length) {
                sizes[d] = ((sizes[d] ?: 0L) + delta).coerceAtLeast(0L)
                if (d == root || !d.contains('/')) break
                d = d.substringBeforeLast('/')
            }
        }

        val fileMap = HashMap<String, String>()
        val dirMap = ArrayList<Pair<String, String>>()
        for (m in moves) {
            val size = if (m.isDir) (sizes[m.from] ?: m.size) else m.size
            addUp(m.from.substringBeforeLast('/'), -size)
            addUp(m.to.substringBeforeLast('/'), size)
            if (m.isDir) {
                val inside = sizes.keys.filter { it == m.from || it.startsWith(m.from + "/") }
                for (k in inside) {
                    val v = sizes.remove(k) ?: 0L
                    sizes[m.to + k.removePrefix(m.from)] = v
                }
                dirMap.add(m.from to m.to)
            } else {
                fileMap[m.from] = m.to
            }
        }

        fun newPath(p: String): String? {
            fileMap[p]?.let { return it }
            for ((from, to) in dirMap) {
                if (p.startsWith("$from/")) return to + p.removePrefix(from)
            }
            return null
        }

        val files = allFiles.map { f ->
            val n = newPath(f.path)
            if (n == null) f else ScannedFile(n, f.size, f.modified, f.type)
        }
        // Kategori yang berdasarkan lokasi (WhatsApp, Download, dll): file yang sudah dipindah dikeluarkan
        val byLocation = setOf(
            Keys.SAMPAH, Keys.SAMPAH_GALERI, Keys.WHATSAPP, Keys.TELEGRAM, Keys.SCREENSHOT, Keys.DOWNLOAD,
        )
        val cats = categories.map { c ->
            if (c.key in byLocation) c.without { newPath(it) != null } else c.remap { newPath(it) }
        }
        return ScanResult(root, files, sizes, cats, finishedAt)
    }
}

class ScanProgress(
    /** 0 = memeriksa file, 1 = mengelompokkan, 2 = mencari file kembar */
    val phase: Int,
    val files: Int,
    val percent: Int,
    val current: String,
)

data class StorageInfo(val total: Long, val free: Long) {
    val used: Long get() = (total - free).coerceAtLeast(0L)
    val usedFraction: Float get() = if (total > 0) used.toFloat() / total else 0f
}
