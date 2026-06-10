package com.skylight.chathead

import android.content.Context

/**
 * Stores which apps the user picked to show in the chathead's radial menu.
 *
 * Each app is stored as a flattened ComponentName string (e.g.
 * "com.android.calculator2/.Calculator").
 *
 * [MAX_APPS] is derived from a minimum tap-target size: the menu icons shrink
 * as more apps are added, but never below 48dp (the Android accessibility
 * minimum). With a compact ring hugging the chathead, 8 apps (plus the fixed
 * Back and Home actions = 10 icons total) is the most that keeps every icon at
 * or above that minimum with comfortable spacing.
 */
object AppPrefs {

    const val MAX_APPS = 8

    private const val PREFS = "chathead_prefs"
    private const val KEY_APPS = "selected_apps"

    fun getSelected(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_APPS, emptySet()).orEmpty()

    fun setSelected(context: Context, apps: Set<String>) {
        prefs(context).edit().putStringSet(KEY_APPS, apps).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
