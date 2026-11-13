package com.bersihku.app.data

import android.content.Context
import android.media.MediaScannerConnection
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Tempat Sampah BersihKu: file yang dihapus dipindah ke folder tersembunyi dulu,
 * bisa dikembalikan, dan otomatis terhapus permanen setelah [KEEP_DAYS] hari.
 */
object Trash {
    const val KEEP_DAYS = 7

    val dir: File get() = File(Storage.root, ".BersihKu_Sampah")
    private val indexFile: File get() = File(dir, ".daftar.json")
    private var counter = 0

    data class Item(
        val id: String,
        val original: String,
        val size: Long,
        val deletedAt: Long,
        val isDir: Boolean,
    ) {
        val file: File get() = File(Trash.dir, id)
        val name: String get() = original.substringAfterLast('/')
        val daysLeft: Int
            get() = (Trash.KEEP_DAYS - ((System.currentTimeMillis() - deletedAt) / DAY).toInt()).coerceAtLeast(0)
    }

    data class Target(val path: String, val size: Long, val isDir: Boolean)

    data class Outcome(val donePaths: List<String>, val doneDirs: List<String>, val bytes: Long, val failed: Int)

    @Synchronized
    fun load(): List<Item> {
        val f = indexFile
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Item(
                    id = o.getString("id"),
                    original = o.getString("asal"),
                    size = o.getLong("ukuran"),
                    deletedAt = o.getLong("waktu"),
                    isDir = o.optBoolean("folder", false),
                )
            }.filter { it.file.exists() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(items: List<Item>) {
        ensureDir()
        val arr = JSONArray()
        for (it in items) {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("asal", it.original)
                    .put("ukuran", it.size)
                    .put("waktu", it.deletedAt)
                    .put("folder", it.isDir)
            )
        }
        val text = arr.toString()
        val tmp = File(dir, ".daftar.tmp")
        try {
            tmp.writeText(text)
            if (!tmp.renameTo(indexFile)) indexFile.writeText(text)
        } catch (e: Exception) {
            try { indexFile.writeText(text) } catch (_: Exception) { }
        }
    }

    private fun ensureDir() {
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) try { nomedia.createNewFile() } catch (_: Exception) { }
    }

    /** Pindahkan ke Tempat Sampah (bisa dikembalikan). */
    @Synchronized
    fun moveIn(ctx: Context, targets: List<Target>): Outcome {
        ensureDir()
        val items = load().toMutableList()
        val donePaths = ArrayList<String>()
        val doneDirs = ArrayList<String>()
        var bytes = 0L
        var failed = 0
        for (t in targets) {
            val src = File(t.path)
            if (!src.exists()) {
                if (t.isDir) doneDirs.add(t.path) else donePaths.add(t.path)
                continue
            }
            val id = "${System.currentTimeMillis()}_${counter++}_${src.name}"
            val dest = File(dir, id)
            if (src.renameTo(dest)) {
                items.add(Item(id, t.path, t.size, System.currentTimeMillis(), t.isDir))
                if (t.isDir) doneDirs.add(t.path) else donePaths.add(t.path)
                bytes += t.size
            } else {
                failed++
            }
        }
        save(items)
        notifyMedia(ctx, donePaths)
        return Outcome(donePaths, doneDirs, bytes, failed)
    }

    /** Hapus permanen langsung (tanpa lewat Tempat Sampah). */
    fun deleteNow(ctx: Context, targets: List<Target>): Outcome {
        val donePaths = ArrayList<String>()
        val doneDirs = ArrayList<String>()
        var bytes = 0L
        var failed = 0
        for (t in targets) {
            val f = File(t.path)
            val ok = if (!f.exists()) true else if (t.isDir) f.deleteRecursively() else f.delete()
            if (ok || !f.exists()) {
                if (t.isDir) doneDirs.add(t.path) else donePaths.add(t.path)
                bytes += t.size
            } else {
                failed++
            }
        }
        notifyMedia(ctx, donePaths)
        return Outcome(donePaths, doneDirs, bytes, failed)
    }

    @Synchronized
    fun restore(ctx: Context, ids: Set<String>): Int {
        val items = load().toMutableList()
        val restored = ArrayList<String>()
        val iter = items.iterator()
        while (iter.hasNext()) {
            val item = iter.next()
            if (item.id !in ids) continue
            var target = File(item.original)
            if (target.exists()) target = uniqueName(target)
            target.parentFile?.mkdirs()
            if (item.file.renameTo(target)) {
                iter.remove()
                restored.add(target.absolutePath)
            }
        }
        save(items)
        notifyMedia(ctx, restored)
        return restored.size
    }

    @Synchronized
    fun deleteForever(ids: Set<String>): Long {
        val items = load().toMutableList()
        var bytes = 0L
        val iter = items.iterator()
        while (iter.hasNext()) {
            val item = iter.next()
            if (item.id !in ids) continue
            val f = item.file
            val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
            if (ok || !f.exists()) {
                iter.remove()
                bytes += item.size
            }
        }
        save(items)
        return bytes
    }

    /** Kosongkan semuanya, termasuk file yang tidak tercatat. */
    @Synchronized
    fun emptyAll(): Long {
        val bytes = load().sumOf { it.size }
        dir.listFiles()?.forEach { f ->
            if (f.name == ".nomedia") return@forEach
            if (f.isDirectory) f.deleteRecursively() else f.delete()
        }
        save(emptyList())
        return bytes
    }

    /** Hapus otomatis yang sudah lebih dari [KEEP_DAYS] hari. */
    fun purgeOld() {
        val old = load().filter { it.daysLeft <= 0 }.map { it.id }.toSet()
        if (old.isNotEmpty()) deleteForever(old)
    }

    private fun uniqueName(f: File): File {
        val base = f.nameWithoutExtension
        val ext = f.extension
        var i = 1
        while (true) {
            val c = File(f.parentFile, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            if (!c.exists()) return c
            i++
        }
    }

    private fun notifyMedia(ctx: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        try {
            MediaScannerConnection.scanFile(ctx.applicationContext, paths.take(500).toTypedArray(), null, null)
        } catch (_: Exception) {
        }
    }
}
