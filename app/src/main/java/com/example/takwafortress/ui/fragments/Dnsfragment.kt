package com.example.takwafortress.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.services.filtering.ContentFilteringService
import com.example.takwafortress.services.filtering.ContentFilterResult
import com.example.takwafortress.ui.viewmodels.FortressStatusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BG_DARK    = Color.parseColor("#1A1A2E")
private val CARD_BG    = Color.parseColor("#16213E")
private val GREEN      = Color.parseColor("#4CAF50")
private val BLUE       = Color.parseColor("#2196F3")
private val RED        = Color.parseColor("#F44336")
private val ORANGE     = Color.parseColor("#FF9800")
private val TEXT_WHITE = Color.WHITE
private val TEXT_GREY  = Color.parseColor("#9E9E9E")

class DnsFragment : Fragment() {

    private lateinit var viewModel: FortressStatusViewModel
    private lateinit var contentFilteringService: ContentFilteringService

    private var isExpired = false

    private lateinit var dnsLayerText: TextView
    private lateinit var chromeLayerText: TextView
    private lateinit var browserLayerText: TextView
    private lateinit var normalContent: LinearLayout

    companion object {
        private const val ARG_EXPIRED = "arg_expired"
        fun newInstance(startExpired: Boolean = false) = DnsFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_EXPIRED, startExpired) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isExpired = arguments?.getBoolean(ARG_EXPIRED, false) ?: false
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        contentFilteringService = ContentFilteringService(requireContext())
        viewModel = ViewModelProvider(requireActivity(), object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return FortressStatusViewModel(requireContext()) as T
            }
        })[FortressStatusViewModel::class.java]

        return buildUi()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.isCommitmentExpired.observe(viewLifecycleOwner) { applyExpiredMode(it) }
        refreshStatus()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(BG_DARK)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        content.addView(makeLabel("🌐 Content Filtering", 22f, TEXT_WHITE).also { it.setPadding(0, 0, 0, dp(16)) })

        // Status card
        val statusCard = makeCard()
        statusCard.addView(makeLabel("Protection Layers", 15f, TEXT_WHITE).also { it.setPadding(0, 0, 0, dp(10)) })
        dnsLayerText     = makeLabel("⏳ Layer 1 – DNS Filter: Checking...", 14f, TEXT_GREY)
        chromeLayerText  = makeLabel("⏳ Layer 2 – Chrome SafeSearch: Checking...", 14f, TEXT_GREY)
        browserLayerText = makeLabel("⏳ Layer 3 – Browser Blocking: Checking...", 14f, TEXT_GREY)
        statusCard.addView(dnsLayerText); statusCard.addView(chromeLayerText); statusCard.addView(browserLayerText)
        content.addView(statusCard)

        // Normal action content (dimmed when expired)
        normalContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val activateBtn = Button(requireContext()).apply {
            text = "🚀 Activate Full Protection"; textSize = 16f; setTextColor(TEXT_WHITE); setBackgroundColor(GREEN)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(16); bottomMargin = dp(8) }
        }
        val testBtn = Button(requireContext()).apply {
            text = "🧪 Test DNS Filtering"; textSize = 15f; setTextColor(TEXT_WHITE); setBackgroundColor(BLUE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(8); bottomMargin = dp(16) }
        }
        val progressBar = ProgressBar(requireContext()).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }
        }
        val testResultCard = makeCard().apply { visibility = View.GONE }
        testResultCard.addView(makeLabel("📊 Test Result", 16f, TEXT_WHITE).also { it.setPadding(0, 0, 0, dp(8)) })
        val testResultText = makeLabel("", 14f, TEXT_GREY)
        testResultCard.addView(testResultText)

        normalContent.addView(activateBtn); normalContent.addView(testBtn)
        normalContent.addView(progressBar); normalContent.addView(testResultCard)
        content.addView(normalContent)

        // How it works card
        val howCard = makeCard()
        howCard.addView(makeLabel("ℹ️ How It Works", 16f, TEXT_WHITE).also { it.setPadding(0, 0, 0, dp(10)) })
        howCard.addView(makeLabel("3-Layer Protection:\n\n1️⃣ DNS Filtering\n   • CleanBrowsing Adult Filter\n   • Blocks adult domains on all apps\n\n2️⃣ Chrome Management\n   • SafeSearch forced\n   • Incognito disabled\n   • DoH disabled\n\n3️⃣ Browser Blocking\n   • Only Chrome allowed\n   • All other browsers suspended/hidden", 13f, TEXT_GREY))
        content.addView(howCard)

        // Button listeners
        activateBtn.setOnClickListener {
            if (isExpired) { showExpiredToast(); return@setOnClickListener }
            progressBar.visibility = View.VISIBLE; activateBtn.isEnabled = false
            lifecycleScope.launch {
                val result = contentFilteringService.activateFullProtection()
                progressBar.visibility = View.GONE; activateBtn.isEnabled = true
                when (result) {
                    is ContentFilterResult.Success -> { Toast.makeText(requireContext(), "✅ Protection activated!", Toast.LENGTH_SHORT).show(); refreshStatus() }
                    is ContentFilterResult.Failed  -> Toast.makeText(requireContext(), "❌ Failed: ${result.reason}", Toast.LENGTH_LONG).show()
                    else -> Toast.makeText(requireContext(), "❌ Device Owner required", Toast.LENGTH_SHORT).show()
                }
            }
        }

        testBtn.setOnClickListener {
            if (isExpired) { showExpiredToast(); return@setOnClickListener }
            progressBar.visibility = View.VISIBLE; testBtn.isEnabled = false
            lifecycleScope.launch {
                val blocked = isDnsBlocking("pornhub.com")
                progressBar.visibility = View.GONE; testBtn.isEnabled = true
                testResultCard.visibility = View.VISIBLE
                testResultText.text = if (blocked) "✅ DNS is working correctly!\nAdult domains are being blocked."
                else "⚠️ DNS filter may not be active.\nTry tapping Activate Full Protection."
                testResultText.setTextColor(if (blocked) GREEN else ORANGE)
            }
        }

        scroll.addView(content)
        return scroll
    }

    fun applyExpiredMode(expired: Boolean) {
        isExpired = expired
        normalContent.alpha = if (expired) 0.35f else 1.0f
    }

    private fun refreshStatus() {
        val status = contentFilteringService.getProtectionStatus()
        dnsLayerText.text = if (status.dnsFilterActive) "✅ Layer 1 – DNS Filter: Active" else "❌ Layer 1 – DNS Filter: Inactive"
        dnsLayerText.setTextColor(if (status.dnsFilterActive) GREEN else RED)
        chromeLayerText.text = if (status.chromeManagedActive) "✅ Layer 2 – Chrome SafeSearch: Active" else "❌ Layer 2 – Chrome SafeSearch: Inactive"
        chromeLayerText.setTextColor(if (status.chromeManagedActive) GREEN else RED)
        browserLayerText.text = if (status.browsersBlocked > 0) "✅ Layer 3 – Browser Blocking: Active" else "❌ Layer 3 – Browser Blocking: Inactive"
        browserLayerText.setTextColor(if (status.browsersBlocked > 0) GREEN else RED)
    }

    private suspend fun isDnsBlocking(domain: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val ip = java.net.InetAddress.getByName(domain).hostAddress ?: return@withContext false
            ip == "0.0.0.0" || ip.startsWith("185.228")
        } catch (e: java.net.UnknownHostException) { true }
        catch (e: Exception) { false }
    }

    private fun showExpiredToast() { Toast.makeText(requireContext(), "Commitment ended — this feature is disabled", Toast.LENGTH_SHORT).show() }
    private fun makeCard() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(CARD_BG); setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(10) }
    }
    private fun makeLabel(text: String, size: Float, color: Int, gravity: Int = Gravity.START) =
        TextView(requireContext()).apply { this.text = text; textSize = size; setTextColor(color); this.gravity = gravity }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}