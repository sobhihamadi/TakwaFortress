//package com.example.takwafortress.ui.activities
//
//import android.accounts.AccountManager
//import android.content.Intent
//import android.os.Bundle
//import android.provider.Settings
//import android.widget.Button
//import android.widget.TextView
//import androidx.appcompat.app.AlertDialog
//import androidx.appcompat.app.AppCompatActivity
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import com.example.takwafortress.R
////import com.example.takwafortress.services.security.AccountInfo
//import com.example.takwafortress.services.security.WirelessAdbService
//
//class AccountRemovalActivity : AppCompatActivity() {
//
//    private lateinit var wirelessAdbService: WirelessAdbService
////    private lateinit var accountsList: MutableList<AccountInfo>
//
//    // UI Elements
//    private lateinit var titleText: TextView
//    private lateinit var descriptionText: TextView
//    private lateinit var accountCountText: TextView
//    private lateinit var accountsRecyclerView: RecyclerView
//    private lateinit var openAccountsButton: Button
//    private lateinit var checkAgainButton: Button
//    private lateinit var continueButton: Button
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_account_removal)
//
//        wirelessAdbService = WirelessAdbService(this)
////        accountsList = mutableListOf()
////
////        initViews()
////        setupListeners()
////        checkAccounts()
//    }
//
//    private fun initViews() {
//        titleText = findViewById(R.id.titleText)
//        descriptionText = findViewById(R.id.descriptionText)
//        accountCountText = findViewById(R.id.accountCountText)
//        accountsRecyclerView = findViewById(R.id.accountsRecyclerView)
//        openAccountsButton = findViewById(R.id.openAccountsButton)
//        checkAgainButton = findViewById(R.id.checkAgainButton)
//        continueButton = findViewById(R.id.continueButton)
//
//        accountsRecyclerView.layoutManager = LinearLayoutManager(this)
//    }
//
//    private fun setupListeners() {
//        openAccountsButton.setOnClickListener {
//            openAccountsSettings()
//        }
//
//        checkAgainButton.setOnClickListener {
//            checkAccounts()
//        }
//
////        continueButton.setOnClickListener {
////            if (accountsList.isEmpty()) {
////                navigateToDeviceOwnerSetup()
////            } else {
////                showAccountsStillExistDialog()
////            }
////        }
////    }
//
//    private fun checkAccounts() {
//        accountsList.clear()
//        accountsList.addAll(wirelessAdbService.getDeviceAccounts())
//
//        if (accountsList.isEmpty()) {
//            showNoAccountsState()
//        } else {
//            showAccountsFoundState()
//        }
//    }
//
//    private fun showAccountsFoundState() {
//        accountCountText.text = "⚠️ ${accountsList.size} Account(s) Found"
//        accountCountText.setTextColor(getColor(android.R.color.holo_orange_dark))
//
//        descriptionText.text = """
//            We detected ${accountsList.size} account(s) on your device.
//
//            ⚠️ You MUST remove ALL accounts before activating Device Owner.
//
//            Why? Android prevents Device Owner activation if any accounts exist (security measure).
//
//            ✅ After activation, you can add them back immediately.
//        """.trimIndent()
//
//        continueButton.isEnabled = false
//        continueButton.text = "❌ Remove Accounts First"
//
//        // Show accounts in RecyclerView
//        accountsRecyclerView.adapter = AccountsAdapter(accountsList)
//    }
//
//    private fun showNoAccountsState() {
//        accountCountText.text = "✅ No Accounts Found"
//        accountCountText.setTextColor(getColor(android.R.color.holo_green_dark))
//
//        descriptionText.text = """
//            Perfect! No accounts detected.
//
//            ✅ You can now proceed to Device Owner activation.
//
//            💡 Tip: This check prevents activation errors.
//        """.trimIndent()
//
//        continueButton.isEnabled = true
//        continueButton.text = "Continue to Setup ✅"
//
//        // Hide RecyclerView
//        accountsRecyclerView.adapter = null
//    }
//
//    private fun openAccountsSettings() {
//        try {
//            val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
//            startActivity(intent)
//
//            AlertDialog.Builder(this)
//                .setTitle("📋 Remove All Accounts")
//                .setMessage("""
//                    Steps to remove accounts:
//
//                    1. Tap each account
//                    2. Tap "Remove Account"
//                    3. Confirm removal
//                    4. Repeat for ALL accounts
//                    5. Return to this app
//                    6. Tap "Check Again"
//
//                    Don't worry - you can add them back after activation!
//                """.trimIndent())
//                .setPositiveButton("OK", null)
//                .show()
//        } catch (e: Exception) {
//            AlertDialog.Builder(this)
//                .setTitle("❌ Error")
//                .setMessage("Could not open accounts settings. Please open Settings → Accounts manually.")
//                .setPositiveButton("OK", null)
//                .show()
//        }
//    }
//
//    private fun showAccountsStillExistDialog() {
//        AlertDialog.Builder(this)
//            .setTitle("⚠️ Accounts Still Exist")
//            .setMessage("""
//                You still have ${accountsList.size} account(s) on your device.
//
//                Device Owner activation will FAIL if you continue.
//
//                Please remove all accounts first.
//            """.trimIndent())
//            .setPositiveButton("Remove Accounts") { _, _ ->
//                openAccountsSettings()
//            }
//            .setNegativeButton("Cancel", null)
//            .show()
//    }
//
//    private fun navigateToDeviceOwnerSetup() {
//        val intent = Intent(this, DeviceOwnerSetupActivity::class.java)
//        startActivity(intent)
//        finish()
//    }
//
//    override fun onResume() {
//        super.onResume()
//        // Re-check accounts when user returns from settings
//        checkAgainButton.postDelayed({
//            checkAccounts()
//        }, 500)
//    }
//
//    /**
//     * Adapter for displaying accounts list.
//     */
//    private class AccountsAdapter(
//        private val accounts: List<AccountInfo>
//    ) : RecyclerView.Adapter<AccountsAdapter.ViewHolder>() {
//
//        class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
//            val accountNameText: TextView = view.findViewById(R.id.accountNameText)
//            val accountTypeText: TextView = view.findViewById(R.id.accountTypeText)
//        }
//
//        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
//            val view = android.view.LayoutInflater.from(parent.context)
//                .inflate(R.layout.item_account, parent, false)
//            return ViewHolder(view)
//        }
//
//        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//            val account = accounts[position]
//            holder.accountNameText.text = account.name
//            holder.accountTypeText.text = getAccountTypeDisplay(account.type)
//        }
//
//        override fun getItemCount() = accounts.size
//
//        private fun getAccountTypeDisplay(type: String): String {
//            return when {
//                type.contains("google", ignoreCase = true) -> "📧 Google Account"
//                type.contains("samsung", ignoreCase = true) -> "📱 Samsung Account"
//                type.contains("exchange", ignoreCase = true) -> "💼 Work Account"
//                type.contains("whatsapp", ignoreCase = true) -> "💬 WhatsApp"
//                else -> "📋 $type"
//            }
//        }
//    }
//}