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
}

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
