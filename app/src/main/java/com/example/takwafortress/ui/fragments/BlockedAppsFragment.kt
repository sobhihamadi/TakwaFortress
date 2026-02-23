package com.example.takwafortress.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.takwafortress.R
import com.example.takwafortress.services.filtering.AppSuspensionService
import com.example.takwafortress.services.monitoring.AppInstallMonitorService
import kotlinx.coroutines.launch

/**
 * Blocked Apps Fragment - Shows list of blocked/suspended apps.
 */
class BlockedAppsFragment : Fragment() {

    private lateinit var appSuspensionService: AppSuspensionService
    private lateinit var appInstallMonitorService: AppInstallMonitorService

    // UI Elements
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var suspendedCountText: TextView
    private lateinit var hiddenCountText: TextView

    private val blockedAppsList = mutableListOf<BlockedAppItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_blocked_apps_old_backup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appSuspensionService = AppSuspensionService(requireContext())
        appInstallMonitorService = AppInstallMonitorService(requireContext())

        initViews(view)
        loadBlockedApps()
    }

    /**
     * Initializes views.
     */
    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.blockedAppsRecyclerView)
        emptyStateText = view.findViewById(R.id.emptyStateText)
        suspendedCountText = view.findViewById(R.id.suspendedCountText)
        hiddenCountText = view.findViewById(R.id.hiddenCountText)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    /**
     * Loads blocked apps.
     */
    private fun loadBlockedApps() {
        lifecycleScope.launch {
            blockedAppsList.clear()}
//
//            // Get suspension report
////            val report = appSuspensionService.getSuspensionReport()
//
//            // Add browsers (suspended)
//            report.browsersList.forEach { app ->
//                blockedAppsList.add(
//                    BlockedAppItem(
//                        packageName = app.packageName,
//                        appName = app.appName,
//                        status = if (app.isSuspended) "Suspended" else "Not Suspended",
//                        statusColor = if (app.isSuspended) "#4CAF50" else "#FF9800",
//                        icon = "🌐"
//                    )
//                )
//            }
//
//            // Add nuclear apps (hidden)
//            report.nuclearAppsList.forEach { app ->
//                blockedAppsList.add(
//                    BlockedAppItem(
//                        packageName = app.packageName,
//                        appName = app.appName,
//                        status = if (app.isHidden) "Hidden" else "Visible",
//                        statusColor = if (app.isHidden) "#4CAF50" else "#F44336",
//                        icon = "🚫"
//                    )
//                )
//            }
//
//            // Update counts
//            suspendedCountText.text = "Suspended: ${report.suspendedBrowsers}"
//            hiddenCountText.text = "Hidden: ${report.hiddenNuclearApps}"
//
//            // Update UI
//            if (blockedAppsList.isEmpty()) {
//                emptyStateText.visibility = View.VISIBLE
//                recyclerView.visibility = View.GONE
//            } else {
//                emptyStateText.visibility = View.GONE
//                recyclerView.visibility = View.VISIBLE
//                recyclerView.adapter = BlockedAppsAdapter(blockedAppsList)
//            }
//        }
//    }
        }

    /**
     * Adapter for blocked apps list.
     */
    private class BlockedAppsAdapter(
        private val apps: List<BlockedAppItem>
    ) : RecyclerView.Adapter<BlockedAppsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconText: TextView = view.findViewById(R.id.appIconText)
            val nameText: TextView = view.findViewById(R.id.appNameText)
            val packageText: TextView = view.findViewById(R.id.packageNameText)
            val statusText: TextView = view.findViewById(R.id.statusText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_blocked_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.iconText.text = app.icon
            holder.nameText.text = app.appName
            holder.packageText.text = app.packageName
            holder.statusText.text = app.status
        }

        override fun getItemCount() = apps.size
    }

    companion object {
        fun newInstance(): BlockedAppsFragment {
            return BlockedAppsFragment()
        }
    }
}

/**
 * Data class for blocked app item.
 */
data class BlockedAppItem(
    val packageName: String,
    val appName: String,
    val status: String,
    val statusColor: String,
    val icon: String
)