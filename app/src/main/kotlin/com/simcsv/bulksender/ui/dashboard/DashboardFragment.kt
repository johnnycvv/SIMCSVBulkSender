package com.simcsv.bulksender.ui.dashboard

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.simcsv.bulksender.R
import com.simcsv.bulksender.databinding.FragmentDashboardBinding
import com.simcsv.bulksender.permission.PermissionManager
import com.simcsv.bulksender.sms.SimSelector
import com.simcsv.bulksender.sms.SmsQueue

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnImportCsv.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_import)
        }
        binding.btnViewLogs.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_logs)
        }
        binding.btnGoToQueue.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_preview)
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            binding.tvTodaySent.text      = stats.todaySent.toString()
            binding.tvTodayDelivered.text = stats.todayDelivered.toString()
            binding.tvTodayFailed.text    = stats.todayFailed.toString()
            binding.tvAllTimeSent.text      = stats.allTimeSent.toString()
            binding.tvAllTimeDelivered.text = stats.allTimeDelivered.toString()
            binding.tvAllTimeFailed.text    = stats.allTimeFailed.toString()
        }

        updateSimStatus()
        updateQueueStats()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateSimStatus()
        updateQueueStats()
        viewModel.loadStats(requireContext())
    }

    private fun updateSimStatus() {
        binding.tvSimStatus.text    = SimSelector.getSimCountLabel(requireContext())
        binding.tvSimAvailable.text = if (SimSelector.isSimAvailable(requireContext()))
            "Ready" else "Not Ready"
    }

    private fun updateQueueStats() {
        binding.tvContactsLoaded.text = SmsQueue.totalCount.toString()
        binding.tvQueueSize.text      = SmsQueue.pendingCount.toString()
        binding.btnGoToQueue.visibility =
            if (SmsQueue.totalCount > 0) View.VISIBLE else View.GONE
    }

    private fun checkPermissions() {
        val missing = PermissionManager.getMissingPermissions(requireContext())
        if (missing.isNotEmpty()) {
            binding.cardPermissions.visibility = View.VISIBLE
            binding.tvPermissionsStatus.text   = "Missing ${missing.size} permission(s)"
            binding.btnGrantPermissions.setOnClickListener {
                PermissionManager.requestAllPermissions(this)
            }
        } else {
            binding.cardPermissions.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
