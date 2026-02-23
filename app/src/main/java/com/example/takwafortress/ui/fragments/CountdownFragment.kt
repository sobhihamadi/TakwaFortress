package com.example.takwafortress.ui.fragments


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.takwafortress.R
import com.example.takwafortress.ui.viewmodels.FortressStatusViewModel
//import com.example.takwafortress.ui.viewmodels.FortressStatusState

/**
 * Countdown Fragment - Displays fortress countdown timer.
 * Can be embedded in any activity to show remaining time.
 */
class CountdownFragment : Fragment() {

    private lateinit var viewModel: FortressStatusViewModel

    // UI Elements
    private lateinit var countdownText: TextView
    private lateinit var daysText: TextView
    private lateinit var hoursText: TextView
    private lateinit var minutesText: TextView
    private lateinit var secondsText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressPercentText: TextView
    private lateinit var statusText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_countdown, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[FortressStatusViewModel::class.java]

        initViews(view)
        setupObservers()
    }

    /**
     * Initializes views.
     */
    private fun initViews(view: View) {
        countdownText = view.findViewById(R.id.countdownText)
        daysText = view.findViewById(R.id.daysText)
        hoursText = view.findViewById(R.id.hoursText)
        minutesText = view.findViewById(R.id.minutesText)
        secondsText = view.findViewById(R.id.secondsText)
        progressBar = view.findViewById(R.id.progressBar)
        progressPercentText = view.findViewById(R.id.progressPercentText)
        statusText = view.findViewById(R.id.statusText)
    }

    /**
     * Sets up observers.
     */
    private fun setupObservers() {
        // Observe fortress status
//        viewModel.fortressStatus.observe(viewLifecycleOwner) { state ->
//            when (state) {
//                is FortressStatusState.Active -> {
//                    statusText.text = state.policy.getCommitmentPlan().displayName
//                    progressBar.progress = state.progressPercentage.toInt()
//                    progressPercentText.text = "${state.progressPercentage.toInt()}%"
//                }
//                is FortressStatusState.Inactive -> {
//                    statusText.text = "Fortress Inactive"
//                    countdownText.text = "00:00:00:00"
//                }
//                else -> {
//                    // Handle other states
//                }
//            }
//        }

        // Observe remaining time (updates every second)
        viewModel.remainingTime.observe(viewLifecycleOwner) { time ->
            // Update combined countdown
            countdownText.text = time.formatted

            // Update individual components
            daysText.text = String.format("%02d", time.days)
            hoursText.text = String.format("%02d", time.hours)
            minutesText.text = String.format("%02d", time.minutes)
            secondsText.text = String.format("%02d", time.seconds)
        }
    }

    companion object {
        /**
         * Creates a new instance of CountdownFragment.
         */
        fun newInstance(): CountdownFragment {
            return CountdownFragment()
        }
    }
}