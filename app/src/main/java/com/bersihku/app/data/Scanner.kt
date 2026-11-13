package com.bersihku.app.data

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.EnumSet

object Scanner {

    private const val BIG_FILE = 50L * 1_000_000
    private const val MIN_DUP_SIZE = 64L * 1024
    private val junkExt = setOf("tmp", "temp", "log", "crdownload", "part", "partial")
    private val dupTypes = setOf(FileType.FOTO, FileType.VIDEO, FileType.AUDIO, FileType.DOKUMEN, FileType.ARSIP)

    fun typeOf(name: String): FileType {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "dng" -> FileType.FOTO
            "mp4", "mkv", "3gp", "mov", "avi", "webm", "m4v" -> FileType.VIDEO
            "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "wma", "mid", "midi" -> FileType.AUDIO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "odt", "epub" -> FileType.DOKUMEN
            "apk", "apks", "xapk", "apkm" -> FileType.APK
            "zip", "rar", "7z", "tar", "gz" -> FileType.ARSIP
            else -> FileType.LAINNYA
        }
    }

    /** Label sub-kelompok untuk tombol filter di daftar file. */
    fun subLabel(key: String, f: ScannedFile): String {
        val p = f.path.lowercase()
        return when (key) {
            Keys.WHATSAPP -> when {
                p.contains("/.statuses/") -> "Status"
                p.contains("/whatsapp images/") -> "Foto"
                p.contains("/whatsapp video") -> "Video"
                p.contains("/whatsapp documents/") -> "Dokumen"
                p.contains("/whatsapp voice notes/") -> "Voice note"
                p.contains("/whatsapp audio/") -> "Audio"
                p.contains("/whatsapp stickers/") -> "Stiker"
                p.contains("/whatsapp animated gifs/") -> "GIF"
                else -> "Lainnya"
            }
            Keys.TELEGRAM -> when {
                p.contains("/telegram images/") -> "Foto"
                p.contains("/telegram video/") -> "Video"
                p.contains("/telegram documents/") -> "Dokumen"
                p.contains("/telegram audio/") -> "Audio"
                else -> f.type.label
            }
            Keys.SCREENSHOT -> if (p.contains("/screenshots/")) "Screenshot" else "Rekaman layar"
            else -> f.type.label
        }
    }

    suspend fun scan(onProgress: (ScanProgress) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        val scope = this
        val rootFile = Storage.root
        val root = rootFile.absolutePath
        val skip = hashSetOf("$root/Android/data", "$root/Android/obb", Trash.dir.absolutePath)
        val files = ArrayList<ScannedFile>(40_000)
        val dirSizes = HashMap<String, Long>(8_192)
        val stack = ArrayList<Long>()
        var count = 0
        var lastReport = 0L

        // ---------- 1. Jelajahi semua file ----------
        try {
            Files.walkFileTree(
                rootFile.toPath(),
                EnumSet.noneOf(FileVisitOption::class.java),
                Int.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (!scope.isActive) return FileVisitResult.TERMINATE
                        if (dir.toString() in skip) return FileVisitResult.SKIP_SUBTREE
                        stack.add(0L)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (!attrs.isRegularFile) return FileVisitResult.CONTINUE
                        val p = file.toString()
                        val size = attrs.size()
                        files.add(ScannedFile(p, size, attrs.lastModifiedTime().toMillis(), typeOf(p.substringAfterLast('/'))))
                        if (stack.isNotEmpty()) stack[stack.size - 1] = stack[stack.size - 1] + size
                        count++
                        val now = SystemClock.uptimeMillis()
                        if (now - lastReport > 120) {
                            lastReport = now
                            onProgress(ScanProgress(0, count, 0, p.removePrefix(root)))
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult =
                        FileVisitResult.CONTINUE

                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        val s = if (stack.isNotEmpty()) stack.removeAt(stack.size - 1) else 0L
                        dirSizes[dir.toString()] = s
                        if (stack.isNotEmpty()) stack[stack.size - 1] = stack[stack.size - 1] + s
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (e: Exception) {
            // Folder yang tidak bisa dibaca dilewati saja
        }
        scope.ensureActive()

        // ---------- 2. Kelompokkan ----------
        onProgress(ScanProgress(1, count, 0, ""))
        val now = System.currentTimeMillis()
        val junk = ArrayList<ScannedFile>()
        val galleryTrash = ArrayList<ScannedFile>()
        val apk = ArrayList<ScannedFile>()
        val wa = ArrayList<ScannedFile>()
        val tg = ArrayList<ScannedFile>()
        val big = ArrayList<ScannedFile>()
        val shots = ArrayList<ScannedFile>()
        val oldDownload = ArrayList<ScannedFile>()
        val dupCandidates = ArrayList<ScannedFile>()

        for (f in files) {
            if (Guard.isProtectedFile(root, f.path)) continue
            val rel = f.path.removePrefix(root).lowercase()
            val name = rel.substringAfterLast('/')
            val ext = name.substringAfterLast('.', "")

            if (name.startsWith(".trashed-") || rel.startsWith("/android/.trash/")) {
                galleryTrash.add(f); continue
            }
            if (rel.contains("/.thumbnails/") ||
                rel.startsWith("/lost.dir/") ||
                ext in junkExt ||
                (name.startsWith(".pending-") && now - f.modified > DAY)
            ) {
                junk.add(f); continue
            }

            if (f.type == FileType.APK) apk.add(f)
            val isWa = rel.contains("/whatsapp/media/") || rel.contains("/whatsapp business/media/")
            val isTg = rel.startsWith("/telegram/") || rel.contains("/org.telegram.messenger/")
            if (isWa) wa.add(f) else if (isTg) tg.add(f)
            if (f.size >= BIG_FILE) big.add(f)
            if (rel.contains("/screenshots/") || rel.contains("/screen recordings/") || rel.contains("/screenrecorder/")) shots.add(f)
            if (rel.startsWith("/download/") && f.type != FileType.APK && now - f.modified > 90 * DAY) oldDownload.add(f)

            val appPrivate = rel.startsWith("/android/") && !isWa && !isTg
            if (f.size >= MIN_DUP_SIZE && f.type in dupTypes && !appPrivate) dupCandidates.add(f)
        }

        // ---------- 3. Cari file kembar (isi sama persis) ----------
        onProgress(ScanProgress(2, count, 0, ""))
        val sameSize = dupCandidates.groupBy { it.size }.values.filter { it.size > 1 }
        val total = sameSize.sumOf { it.size }.coerceAtLeast(1)
        var done = 0
        val groups = ArrayList<List<ScannedFile>>()
        for (g in sameSize) {
            scope.ensureActive()
            val byHead = g.groupBy { hash(it.path, headOnly = true) }
                .filter { (k, v) -> k != null && v.size > 1 }.values
            for (h in byHead) {
                val byFull = h.groupBy { hash(it.path, headOnly = false) }
                    .filter { (k, v) -> k != null && v.size > 1 }.values
                groups.addAll(byFull)
            }
            done += g.size
            onProgress(ScanProgress(2, count, done * 100 / total, ""))
        }
        val dupGroups = groups
            .map { g -> g.sortedWith(compareBy<ScannedFile>({ keepRank(root, it.path) }, { it.modified })) }
            .sortedByDescending { g -> g[0].size * (g.size - 1) }

        // ---------- 4. Susun kategori ----------
        fun all(list: List<ScannedFile>) = list.map { it.path }.toHashSet()
        val categories = listOf(
            Category(
                Keys.SAMPAH, "File sampah",
                "Sisa thumbnail galeri, file sementara, log, dan download yang gagal. Tidak dibutuhkan, aman dihapus.",
                Safety.AMAN, junk.sortedByDescending { it.size }, all(junk),
            ),
            Category(
                Keys.SAMPAH_GALERI, "Isi tempat sampah Galeri",
                "Foto & video yang sudah kamu hapus, tapi masih disimpan di tempat sampah Galeri / Google Foto (biasanya 30 hari).",
                Safety.AMAN, galleryTrash.sortedByDescending { it.size }, all(galleryTrash),
            ),
            Category(
                Keys.KEMBAR, "File kembar",
                "File yang isinya sama persis tersimpan lebih dari sekali. 1 salinan tetap disimpan, sisanya bisa dihapus.",
                Safety.AMAN, dupGroups.flatten(), dupGroups.flatMap { it.drop(1) }.map { it.path }.toHashSet(), dupGroups,
            ),
            Category(
                Keys.APK, "File installer APK",
                "File pemasang aplikasi. Aplikasinya tetap terpasang walaupun file ini dihapus.",
                Safety.AMAN, apk.sortedByDescending { it.size }, all(apk),
            ),
            Category(
                Keys.WHATSAPP, "Foto & video WhatsApp",
                "Foto, video, voice note, dan dokumen dari chat WhatsApp. Pilih yang sudah tidak perlu.",
                Safety.CEK_DULU, wa.sortedByDescending { it.size }, emptySet(),
            ),
            Category(
                Keys.TELEGRAM, "File Telegram",
                "File yang tersimpan dari Telegram.",
                Safety.CEK_DULU, tg.sortedByDescending { it.size }, emptySet(),
            ),
            Category(
                Keys.BESAR, "File besar (50 MB ke atas)",
                "Biasanya video. Cek dulu sebelum menghapus.",
                Safety.CEK_DULU, big.sortedByDescending { it.size }, emptySet(),
            ),
            Category(
                Keys.SCREENSHOT, "Screenshot & rekaman layar",
                "Tangkapan layar yang sering menumpuk tanpa sadar.",
                Safety.CEK_DULU, shots.sortedByDescending { it.size }, emptySet(),
            ),
            Category(
                Keys.DOWNLOAD, "Download lama",
                "File di folder Download yang sudah lebih dari 3 bulan.",
                Safety.CEK_DULU, oldDownload.sortedByDescending { it.size }, emptySet(),
            ),
        )

        ScanResult(root, files, dirSizes, categories, System.currentTimeMillis())
    }

    /** Daftar semua file dengan jenis tertentu (untuk analisa per jenis). */
    fun typeCategory(r: ScanResult, type: FileType): Category {
        val list = r.allFiles
            .filter { it.type == type && !Guard.isProtectedFile(r.root, it.path) }
            .sortedByDescending { it.size }
        return Category(
            Keys.JENIS + type.name, "Semua ${type.label.lowercase()}",
            "Semua file ${type.label.lowercase()} di HP, dari yang paling besar. Cek dulu sebelum menghapus.",
            Safety.CEK_DULU, list, emptySet(),
        )
    }

    /** Makin kecil = makin layak disimpan (dianggap file asli). */
    private fun keepRank(root: String, path: String): Int {
        val rel = path.removePrefix(root).lowercase()
        return when {
            rel.startsWith("/dcim/camera/") -> 0
            rel.startsWith("/dcim/") || rel.startsWith("/pictures/") || rel.startsWith("/movies/") ||
                rel.startsWith("/music/") || rel.startsWith("/documents/") -> 1
            rel.contains("/whatsapp") && rel.contains("/sent/") -> 4
            rel.contains("/whatsapp") || rel.contains("telegram") -> 3
            else -> 2
        }
    }

    private val hex = "0123456789abcdef".toCharArray()

    private fun hash(path: String, headOnly: Boolean): String? = try {
        val md = MessageDigest.getInstance("MD5")
        val buf = ByteArray(64 * 1024)
        FileInputStream(path).use { input ->
            if (headOnly) {
                val n = input.read(buf)
                if (n > 0) md.update(buf, 0, n)
            } else {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
        }
        val d = md.digest()
        val out = CharArray(d.size * 2)
        for (i in d.indices) {
            val v = d[i].toInt() and 0xFF
            out[i * 2] = hex[v ushr 4]
            out[i * 2 + 1] = hex[v and 0x0F]
        }
        String(out)
    } catch (e: Exception) {
        null
    }
}
