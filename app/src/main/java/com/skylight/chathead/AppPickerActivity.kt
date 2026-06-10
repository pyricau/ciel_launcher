package com.skylight.chathead

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * Full-screen launcher-style list of every launchable app with a checkbox each.
 * Checked apps are saved (live) to [AppPrefs] and become the icons shown in the
 * chathead's radial menu. Enforces [AppPrefs.MAX_APPS].
 */
class AppPickerActivity : Activity() {

    private class AppRow(val component: String, val label: String, val icon: Drawable)

    private lateinit var apps: List<AppRow>
    private val selected = linkedSetOf<String>()
    private lateinit var titleView: TextView
    private lateinit var adapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        titleView = findViewById(R.id.picker_title)
        findViewById<Button>(R.id.picker_done).setOnClickListener { finish() }

        selected.addAll(AppPrefs.getSelected(this))
        apps = loadLaunchableApps()

        adapter = AppsAdapter()
        val list = findViewById<ListView>(R.id.app_list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> toggle(apps[position]) }

        updateTitle()
    }

    private fun loadLaunchableApps(): List<AppRow> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                AppRow(
                    component = component.flattenToString(),
                    label = it.loadLabel(pm).toString(),
                    icon = it.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun toggle(app: AppRow) {
        if (selected.contains(app.component)) {
            selected.remove(app.component)
        } else {
            if (selected.size >= AppPrefs.MAX_APPS) {
                Toast.makeText(
                    this,
                    "You can pick up to ${AppPrefs.MAX_APPS} apps",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            selected.add(app.component)
        }
        AppPrefs.setSelected(this, selected)
        adapter.notifyDataSetChanged()
        updateTitle()
    }

    private fun updateTitle() {
        titleView.text = "Choose apps (${selected.size}/${AppPrefs.MAX_APPS})"
    }

    private inner class AppsAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.row_app, parent, false)
            val app = apps[position]
            row.findViewById<ImageView>(R.id.app_icon).setImageDrawable(app.icon)
            row.findViewById<TextView>(R.id.app_label).text = app.label
            row.findViewById<CheckBox>(R.id.app_check).isChecked =
                selected.contains(app.component)
            return row
        }
    }
}
