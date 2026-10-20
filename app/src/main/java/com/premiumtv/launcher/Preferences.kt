package com.premiumtv.launcher

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import org.json.JSONArray

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val lowMemory: Boolean = manager.isLowRamDevice ||
        ActivityManager.MemoryInfo().let { manager.getMemoryInfo(it); it.totalMem < 1_600_000_000L }

    var favorites: List<String>
        get() = readList("favorites")
        set(value) { writeList("favorites", value); prefs.edit().putBoolean("favorites_initialized", true).apply() }
    val favoritesInitialized get() = prefs.getBoolean("favorites_initialized", false)
    var order: List<String>
        get() = readList("order")
        set(value) = writeList("order", value)
    var hidden: Set<String>
        get() = prefs.getStringSet("hidden", emptySet())!!.toSet()
        set(value) { prefs.edit().putStringSet("hidden", value.toSet()).apply() }
    var accentName: String
        get() = prefs.getString("accent", "ice") ?: "ice"
        set(value) { prefs.edit().putString("accent", value).apply() }
    val accent: Int get() = Color.parseColor(when (accentName) {
        "pearl" -> "#ECE9E2"
        "gold" -> "#DDC69C"
        "sage" -> "#B5CDBE"
        else -> "#BFD5FF"
    })
    var wallpaper: String
        get() = prefs.getString("wallpaper", "graphite") ?: "graphite"
        set(value) { prefs.edit().putString("wallpaper", value).apply() }
    var animations: Boolean
        get() = prefs.getBoolean("animations", !lowMemory)
        set(value) { prefs.edit().putBoolean("animations", value).apply() }
    var language: String
        get() = prefs.getString("language", "auto") ?: "auto"
        set(value) { prefs.edit().putString("language", value).apply() }
    var margin: Int
        get() = prefs.getInt("margin", 32).coerceIn(24, 48)
        set(value) { prefs.edit().putInt("margin", value.coerceIn(24, 48)).apply() }

    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites.filterNot { it == key } else favorites + key
    }
    private fun readList(key: String): List<String> = try {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until array.length()).map { array.getString(it) }.distinct()
    } catch (_: Exception) { emptyList() }
    private fun writeList(key: String, value: List<String>) {
        prefs.edit().putString(key, JSONArray(value.distinct()).toString()).apply()
    }
    companion object { const val FILE = "noir_preferences" }
}
