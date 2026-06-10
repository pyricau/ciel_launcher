package com.skylight.chathead

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Full-screen list for choosing what shows in the chathead's radial menu:
 * launchable apps (checkbox each) plus custom deep-link shortcuts (e.g. a Trello
 * board URL) added via a dialog. Selections are saved live to [AppPrefs] and
 * share the [AppPrefs.MAX_ITEMS] budget.
 *
 * (Listing an app's own dynamic shortcuts isn't possible here: Android only lets
 * the default launcher read other apps' shortcuts, and that's the Skylight app,
 * not us. Deep links achieve the same thing without that permission.)
 */
class AppPickerActivity : Activity() {

    private class AppRow(val component: String, val label: String, val icon: Drawable)

    /** One list entry: the add-action, a saved shortcut, or an installed app. */
    private class Row(
        val type: Int,
        val app: AppRow? = null,
        val shortcut: AppPrefs.LinkShortcut? = null,
        val shortcutIcon: Drawable? = null
    )

    private lateinit var apps: List<AppRow>
    private val selectedApps = linkedSetOf<String>()
    private val shortcuts = mutableListOf<AppPrefs.LinkShortcut>()

    private val rows = mutableListOf<Row>()
    private lateinit var titleView: TextView
    private lateinit var adapter: RowsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        titleView = findViewById(R.id.picker_title)
        findViewById<Button>(R.id.picker_done).setOnClickListener { finish() }

        selectedApps.addAll(AppPrefs.getSelected(this))
        shortcuts.addAll(AppPrefs.getShortcuts(this))
        apps = loadLaunchableApps()

        adapter = RowsAdapter()
        val list = findViewById<ListView>(R.id.app_list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> onRowClick(rows[position]) }

        rebuildRows()
    }

    // Hide the chathead while this picker is in front (and during its dialogs,
    // since a dialog doesn't pause the activity). Shown again on pause.
    override fun onResume() {
        super.onResume()
        OverlayService.instance?.setPickerHidden(true)
    }

    override fun onPause() {
        super.onPause()
        OverlayService.instance?.setPickerHidden(false)
    }

    private fun selectedCount() = selectedApps.size + shortcuts.count { it.enabled }

    private fun loadLaunchableApps(): List<AppRow> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                AppRow(component.flattenToString(), it.loadLabel(pm).toString(), it.loadIcon(pm))
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun rebuildRows() {
        rows.clear()
        rows.add(Row(TYPE_ADD))
        shortcuts.forEach {
            val icon = when {
                it.emoji.isNotBlank() -> EmojiIcon.toDrawable(this, it.emoji)
                it.kind == AppPrefs.KIND_HTTP -> getDrawable(R.drawable.ic_http_shortcut)!!
                else -> iconForUrl(it.url)
            }
            rows.add(Row(TYPE_SHORTCUT, shortcut = it, shortcutIcon = icon))
        }
        apps.forEach { rows.add(Row(TYPE_APP, app = it)) }
        adapter.notifyDataSetChanged()
        titleView.text = "Apps & shortcuts (${selectedCount()}/${AppPrefs.MAX_ITEMS})"
    }

    private fun onRowClick(row: Row) {
        when (row.type) {
            TYPE_ADD -> showAddDialog()
            TYPE_SHORTCUT -> {
                // Row tap toggles whether the (kept) shortcut shows in the ring.
                val shortcut = row.shortcut!!
                val index = shortcuts.indexOf(shortcut)
                if (index < 0) return
                if (shortcut.enabled) {
                    shortcuts[index] = shortcut.copy(enabled = false)
                } else {
                    if (selectedCount() >= AppPrefs.MAX_ITEMS) {
                        tooMany(); return
                    }
                    shortcuts[index] = shortcut.copy(enabled = true)
                }
                AppPrefs.setShortcuts(this, shortcuts)
                rebuildRows()
            }
            TYPE_APP -> {
                val component = row.app!!.component
                if (selectedApps.contains(component)) {
                    selectedApps.remove(component)
                } else {
                    if (selectedCount() >= AppPrefs.MAX_ITEMS) {
                        tooMany(); return
                    }
                    selectedApps.add(component)
                }
                AppPrefs.setSelected(this, selectedApps)
                rebuildRows()
            }
        }
    }

    private fun tooMany() {
        Toast.makeText(this, "You can pick up to ${AppPrefs.MAX_ITEMS} items", Toast.LENGTH_SHORT).show()
    }

    private fun removeShortcut(shortcut: AppPrefs.LinkShortcut) {
        shortcuts.remove(shortcut)
        AppPrefs.setShortcuts(this, shortcuts)
        rebuildRows()
    }

    /** Activates an entry directly (the picker doubles as a launcher). */
    private fun launchRow(row: Row) {
        val shortcut = row.shortcut
        if (shortcut != null && shortcut.kind == AppPrefs.KIND_HTTP) {
            // Fire the request and stay in the picker (handy for testing it).
            HttpAction.fire(this, shortcut.url, shortcut.label)
            return
        }
        val intent = if (row.type == TYPE_APP) {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName.unflattenFromString(row.app!!.component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(row.shortcut!!.url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            finish()
        } catch (t: Throwable) {
            Toast.makeText(this, "Couldn't open it", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddDialog() {
        if (selectedCount() >= AppPrefs.MAX_ITEMS) {
            tooMany(); return
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val labelInput = EditText(this).apply {
            hint = "Label (e.g. Work board)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val urlInput = EditText(this).apply {
            hint = "https://…  or  http://192.168.0.x/…"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val openRadio = RadioButton(this).apply {
            id = View.generateViewId()
            text = "Open in app"
        }
        val httpRadio = RadioButton(this).apply {
            id = View.generateViewId()
            text = "HTTP request"
        }
        val kindGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(openRadio)
            addView(httpRadio)
            check(openRadio.id)
        }
        // Emoji icon (optional): type one via the keyboard's emoji panel, or tap a preset.
        val emojiInput = EditText(this).apply {
            hint = "Icon: pick an emoji (optional)"
            setSingleLine()
            textSize = 18f
        }
        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("💡", "🔌", "🌡️", "🔔", "🚪", "📺", "🎵", "🌀", "✅", "🏠", "▶️", "📋").forEach { e ->
            presetRow.addView(TextView(this).apply {
                text = e
                textSize = 24f
                setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
                setOnClickListener {
                    emojiInput.setText(e)
                    emojiInput.setSelection(emojiInput.text.length)
                }
            })
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(labelInput)
            addView(urlInput)
            addView(kindGroup)
            addView(emojiInput)
            addView(HorizontalScrollView(this@AppPickerActivity).apply { addView(presetRow) })
        }
        AlertDialog.Builder(this)
            .setTitle("Add URL shortcut")
            .setView(ScrollView(this).apply { addView(container) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val kind = if (httpRadio.isChecked) AppPrefs.KIND_HTTP else AppPrefs.KIND_OPEN
                addShortcut(
                    labelInput.text.toString().trim(),
                    urlInput.text.toString().trim(),
                    kind,
                    emojiInput.text.toString().trim()
                )
            }
            .show()
    }

    private fun addShortcut(label: String, rawUrl: String, kind: String, emoji: String) {
        if (label.isEmpty() || rawUrl.isEmpty()) {
            Toast.makeText(this, "Enter both a label and a URL", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedCount() >= AppPrefs.MAX_ITEMS) {
            tooMany(); return
        }
        val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            "https://$rawUrl"
        }
        shortcuts.add(AppPrefs.LinkShortcut(label, url, kind = kind, emoji = emoji))
        AppPrefs.setShortcuts(this, shortcuts)
        rebuildRows()
    }

    /**
     * Icon of the *specific* app that handles the URL (e.g. Trello), else a link
     * glyph. Picks the handler that isn't a general-purpose browser.
     */
    private fun iconForUrl(url: String): Drawable {
        val fallback = getDrawable(R.drawable.ic_link)!!
        return runCatching {
            val pm = packageManager
            val handlers = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(url)), 0)
            val browsers = pm.queryIntentActivities(
                Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com/")), 0
            ).mapTo(HashSet()) { it.activityInfo.packageName }
            handlers.firstOrNull {
                val p = it.activityInfo.packageName
                p != "android" && p !in browsers
            }?.loadIcon(pm) ?: fallback
        }.getOrDefault(fallback)
    }

    private inner class RowsAdapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) =
            if (rows[position].type == TYPE_ADD) 0 else 1

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = rows[position]
            if (row.type == TYPE_ADD) {
                return convertView ?: layoutInflater.inflate(R.layout.row_add, parent, false)
            }
            val view = convertView ?: layoutInflater.inflate(R.layout.row_app, parent, false)
            val icon = view.findViewById<ImageView>(R.id.app_icon)
            val label = view.findViewById<TextView>(R.id.app_label)
            val subtitle = view.findViewById<TextView>(R.id.app_subtitle)
            val check = view.findViewById<CheckBox>(R.id.app_check)
            val delete = view.findViewById<ImageView>(R.id.app_delete)
            val launch = view.findViewById<ImageView>(R.id.app_launch)
            launch.setOnClickListener { launchRow(row) }
            if (row.type == TYPE_SHORTCUT) {
                icon.setImageDrawable(row.shortcutIcon)
                label.text = row.shortcut!!.label
                subtitle.text = row.shortcut.url
                subtitle.visibility = View.VISIBLE
                check.visibility = View.VISIBLE
                check.isChecked = row.shortcut.enabled
                delete.visibility = View.VISIBLE
                delete.setOnClickListener { removeShortcut(row.shortcut) }
            } else {
                icon.setImageDrawable(row.app!!.icon)
                label.text = row.app.label
                subtitle.visibility = View.GONE
                check.visibility = View.VISIBLE
                check.isChecked = selectedApps.contains(row.app.component)
                delete.visibility = View.GONE
                delete.setOnClickListener(null)
            }
            return view
        }
    }

    companion object {
        private const val TYPE_ADD = 0
        private const val TYPE_SHORTCUT = 1
        private const val TYPE_APP = 2
    }
}
