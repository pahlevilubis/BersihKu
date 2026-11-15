package com.bersihku.app.data

import android.content.Context
import java.io.File

/** Buat folder, pindahkan, ganti nama, dan rapikan file. */
object Organizer {

    class Outcome(val moves: List<Moved>, val failed: Int, val skipped: Int)

    /** Nama sub-folder untuk tiap jenis file saat "Rapikan". */
    private val tidyNames = mapOf(
        FileType.FOTO to "Foto",
        FileType.VIDEO to "Video",
        FileType.AUDIO to "Musik & Suara",
        FileType.DOKUMEN to "Dokumen",
        FileType.APK to "Installer APK",
        FileType.ARSIP to "Arsip",
        FileType.LAINNYA to "Lainnya",
    )

    /** null = nama boleh dipakai, selain itu pesan kesalahannya. */
    fun checkName(name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Nama tidak boleh kosong"
            n.contains('/') -> "Nama tidak boleh mengandung tanda /"
            n == "." || n == ".." -> "Nama tidak valid"
            n.length > 120 -> "Nama terlalu panjang"
            else -> null
        }
    }

    /** Buat folder baru. Mengembalikan path folder, atau pesan kesalahan. */
    fun createFolder(parent: String, name: String): Result<String> {
        checkName(name)?.let { return Result.failure(IllegalArgumentException(it)) }
        val f = File(parent, name.trim())
        if (f.exists()) return Result.failure(IllegalArgumentException("Sudah ada file/folder dengan nama itu"))
        return if (f.mkdirs()) Result.success(f.absolutePath) else Result.failure(IllegalStateException("Folder gagal dibuat"))
    }

    /** Folder yang boleh jadi tujuan pindah. */
    fun isAllowedDestination(root: String, path: String): Boolean {
        if (path != root && !path.startsWith("$root/")) return false
        val rel = path.removePrefix(root).lowercase()
        if (rel == "/android" || rel.startsWith("/android/")) return false
        if (path == Trash.dir.absolutePath || path.startsWith(Trash.dir.absolutePath + "/")) return false
        return true
    }

    fun move(ctx: Context, targets: List<Trash.Target>, destDir: String): Outcome {
        val moves = ArrayList<Moved>()
        var failed = 0
        var skipped = 0
        File(destDir).mkdirs()
        for (t in targets) {
            val src = File(t.path)
            if (!src.exists()) {
                failed++
                continue
            }
            if (src.parentFile?.absolutePath == destDir) {
                skipped++
                continue
            }
            if (t.isDir && (destDir == t.path || destDir.startsWith(t.path + "/"))) {
                failed++
                continue
            }
            val dest = uniqueFile(File(destDir, src.name))
            var ok = src.renameTo(dest)
            if (!ok && !t.isDir) {
                // Cadangan: salin lalu hapus aslinya (kalau beda penyimpanan)
                ok = try {
                    src.copyTo(dest)
                    if (dest.length() == src.length()) {
                        src.delete()
                        true
                    } else {
                        dest.delete()
                        false
                    }
                } catch (e: Exception) {
                    dest.delete()
                    false
                }
            }
            if (ok) moves.add(Moved(t.path, dest.absolutePath, t.isDir, t.size)) else failed++
        }
        scanMedia(ctx, moves.map { it.from } + moves.map { it.to })
        return Outcome(moves, failed, skipped)
    }

    /** Ganti nama. Mengembalikan hasil pemindahan (lokasi lama → baru), atau pesan kesalahan. */
    fun rename(ctx: Context, path: String, isDir: Boolean, size: Long, newName: String): Result<Moved> {
        checkName(newName)?.let { return Result.failure(IllegalArgumentException(it)) }
        val src = File(path)
        val dest = File(src.parentFile, newName.trim())
        if (dest.absolutePath == src.absolutePath) return Result.failure(IllegalArgumentException("Namanya masih sama"))
        // Memori HP tidak membedakan huruf besar/kecil, jadi "foto" → "Foto" lewat nama sementara
        val caseOnly = dest.absolutePath.equals(src.absolutePath, ignoreCase = true)
        if (!caseOnly && dest.exists()) return Result.failure(IllegalArgumentException("Sudah ada file/folder dengan nama itu"))
        val ok = if (caseOnly) {
            val tmp = File(src.parentFile, ".bersihku_${System.currentTimeMillis()}")
            src.renameTo(tmp) && tmp.renameTo(dest)
        } else {
            src.renameTo(dest)
        }
        if (!ok) return Result.failure(IllegalStateException("Gagal mengganti nama"))
        scanMedia(ctx, listOf(path, dest.absolutePath))
        return Result.success(Moved(path, dest.absolutePath, isDir, size))
    }

    /** Folder yang boleh dirapikan otomatis (bukan folder sistem / milik aplikasi chat / kamera). */
    fun canTidy(root: String, folder: String): Boolean {
        if (folder == root || !folder.startsWith("$root/")) return false
        val rel = folder.removePrefix(root).lowercase()
        if (rel.startsWith("/android") || rel.startsWith("/dcim")) return false
        if (rel.contains("whatsapp") || rel.contains("telegram")) return false
        if (folder.startsWith(Trash.dir.absolutePath)) return false
        if (Guard.isProtectedFile(root, "$folder/x")) return false
        return true
    }

    /** Rencana rapikan: nama sub-folder → file yang akan dimasukkan ke sana. */
    fun tidyPlan(root: String, folder: String): List<Pair<String, List<File>>> {
        if (!canTidy(root, folder)) return emptyList()
        val here = folder.substringAfterLast('/')
        val files = File(folder).listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") && !Guard.isProtectedFile(root, it.absolutePath) }
            ?: return emptyList()
        return files
            .groupBy { tidyNames.getValue(Scanner.typeOf(it.name)) }
            .filterKeys { !it.equals(here, ignoreCase = true) }
            .toList()
            .sortedByDescending { it.second.size }
    }

    fun tidy(ctx: Context, folder: String, plan: List<Pair<String, List<File>>>): Outcome {
        val moves = ArrayList<Moved>()
        var failed = 0
        var skipped = 0
        for ((sub, files) in plan) {
            val out = move(ctx, files.map { Trash.Target(it.absolutePath, it.length(), false) }, "$folder/$sub")
            moves.addAll(out.moves)
            failed += out.failed
            skipped += out.skipped
        }
        return Outcome(moves, failed, skipped)
    }
}
