package com.skylight.chathead

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores what the user picked to show in the chathead's radial menu: launchable
 * apps (by flattened ComponentName) and custom deep-link shortcuts (label + URL,
 * e.g. a Trello board link).
 *
 * [MAX_ITEMS] is derived from a minimum tap-target size: the menu icons shrink
 * as more items are added, but never below 48dp (the Android accessibility
 * minimum). With a compact ring hugging the chathead, 8 items (plus the fixed
 * Back and Home actions = 10 icons total) is the most that keeps every icon at
 * or above that minimum with comfortable spacing. Apps and shortcuts share that
 * budget.
 */
object AppPrefs {

    const val MAX_ITEMS = 8

    private const val PREFS = "chathead_prefs"
    private const val KEY_APPS = "selected_apps"
    private const val KEY_SHORTCUTS = "custom_shortcuts"

    /**
     * A user-defined deep-link shortcut, e.g. a Trello board URL. [enabled]
     * controls whether it currently shows in the ring; disabled shortcuts are
     * kept in the list but hidden.
     */
    data class LinkShortcut(val label: String, val url: String, val enabled: Boolean = true)

    fun getSelected(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_APPS, emptySet()).orEmpty()

    fun setSelected(context: Context, apps: Set<String>) {
        prefs(context).edit().putStringSet(KEY_APPS, apps).apply()
    }

    fun getShortcuts(context: Context): List<LinkShortcut> {
        val raw = prefs(context).getString(KEY_SHORTCUTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LinkShortcut(o.getString("label"), o.getString("url"), o.optBoolean("enabled", true))
            }
        }.getOrDefault(emptyList())
    }

    fun setShortcuts(context: Context, shortcuts: List<LinkShortcut>) {
        val arr = JSONArray()
        shortcuts.forEach {
            arr.put(JSONObject().put("label", it.label).put("url", it.url).put("enabled", it.enabled))
        }
        prefs(context).edit().putString(KEY_SHORTCUTS, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
