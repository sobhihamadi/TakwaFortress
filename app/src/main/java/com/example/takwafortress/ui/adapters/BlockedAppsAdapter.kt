package com.example.takwafortress.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.takwafortress.R
import com.example.takwafortress.services.filtering.AppInfo

class BlockedAppsAdapter(
    private var apps: List<AppInfo>,
    private val onToggle: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<BlockedAppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.appNameText)
        val packageName: TextView = view.findViewById(R.id.packageNameText)
        val blockCheckbox: CheckBox = view.findViewById(R.id.statusText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]

        holder.appName.text = app.appName
        holder.packageName.text = app.packageName
        holder.blockCheckbox.isChecked = app.isBlocked

        holder.blockCheckbox.setOnCheckedChangeListener { _, isChecked ->
            onToggle(app, isChecked)
        }

        holder.itemView.setOnClickListener {
            holder.blockCheckbox.isChecked = !holder.blockCheckbox.isChecked
        }
    }

    override fun getItemCount() = apps.size

    fun updateApps(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }
}