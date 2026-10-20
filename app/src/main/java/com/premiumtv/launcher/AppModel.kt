package com.premiumtv.launcher

import android.content.ComponentName

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val television: Boolean,
    val updateTime: Long
) {
    val component: ComponentName get() = ComponentName(packageName, activityName)
    val iconKey: String get() = packageName + ":" + activityName + ":" + updateTime
}

enum class Section { FAVORITES, ALL }

sealed class HomeItem(val key: String) {
    class Header(val title: String, key: String) : HomeItem(key)
    class Message(val text: String, key: String) : HomeItem(key)
    class Tile(val app: AppEntry, val section: Section, val favorite: Boolean) :
        HomeItem(section.name + ":" + app.packageName)
}

/** Stable merge: saved order first, then newly discovered apps in discovery order. */
object AppOrder {
    fun merge(saved: List<String>, available: List<String>): List<String> {
        val present = available.toHashSet()
        val result = LinkedHashSet<String>()
        saved.filterTo(result) { it in present }
        result.addAll(available)
        return result.toList()
    }

    fun move(order: List<String>, from: Int, to: Int): List<String> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }
}
