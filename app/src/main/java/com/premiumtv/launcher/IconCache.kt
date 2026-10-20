package com.premiumtv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Bounded memory and disk cache. Only visible RecyclerView cells request icons. */
class IconCache(context: Context, lowMemory: Boolean) {
    private val pm = context.applicationContext.packageManager
    private val directory = File(context.cacheDir, "app-icons").apply { mkdirs() }
    private val size = (context.resources.displayMetrics.density * 64).toInt().coerceIn(64, 192)
    private val memory = object : LruCache<String, Bitmap>(if (lowMemory) 2 * 1024 * 1024 else 4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val waiting = mutableMapOf<String, MutableList<WeakReference<ImageView>>>()
    private var generation = 0
    private var closed = false

    init { worker.execute { prune() } }

    fun bind(app: AppEntry, target: ImageView) {
        val key = app.iconKey + ":" + size
        target.tag = key
        val cached = memory.get(key)
        if (cached != null) { target.setImageBitmap(cached); return }
        target.setImageResource(R.drawable.ic_launcher)
        waiting[key]?.let { it.add(WeakReference(target)); return }
        waiting[key] = mutableListOf(WeakReference(target))
        val token = generation
        worker.execute {
            var bitmap: Bitmap? = null
            try {
                val file = File(directory, digest(key) + ".png")
                bitmap = if (file.isFile) BitmapFactory.decodeFile(file.path) else null
                if (bitmap == null) {
                    val icon = pm.getActivityIcon(app.component)
                    val rendered = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    bitmap = rendered
                    icon.setBounds(0, 0, size, size)
                    icon.draw(Canvas(rendered))
                    val temp = File(directory, file.name + ".tmp")
                    temp.outputStream().use { rendered.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (!temp.renameTo(file)) temp.delete()
                    prune()
                } else {
                    file.setLastModified(System.currentTimeMillis())
                }
            } catch (_: Exception) {
                // The bundled mark remains visible if an icon is malformed or an app vanishes.
            } catch (_: OutOfMemoryError) {
                bitmap = null
                memory.evictAll()
            }
            val loaded = bitmap
            main.post {
                if (closed || token != generation) return@post
                if (loaded != null) memory.put(key, loaded)
                waiting.remove(key)?.forEach { ref ->
                    ref.get()?.takeIf { it.tag == key }?.let { view ->
                        if (loaded != null) view.setImageBitmap(loaded)
                    }
                }
            }
        }
    }

    fun trim() { memory.evictAll() }
    fun clear() {
        generation++
        memory.evictAll()
        waiting.clear()
        worker.execute { directory.listFiles()?.forEach { it.delete() } }
    }
    fun close() {
        closed = true
        generation++
        waiting.clear()
        memory.evictAll()
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun prune() {
        val files = directory.listFiles()?.filter { it.extension == "png" }?.sortedBy { it.lastModified() } ?: return
        var bytes = files.sumOf { it.length() }
        for (file in files) {
            if (bytes <= 12L * 1024 * 1024) break
            val length = file.length()
            if (file.delete()) bytes -= length
        }
    }
}
