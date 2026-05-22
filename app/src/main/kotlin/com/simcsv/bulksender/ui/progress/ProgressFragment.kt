package com.simcsv.bulksender.ui.progress

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simcsv.bulksender.BulkSenderApp
import com.simcsv.bulksender.R
import com.simcsv.bulksender.databinding.FragmentProgressBinding
import com.simcsv.bulksender.sms.SmsSenderService
import com.simcsv.bulksender.sms.SmsQueue
import java.io.Serializable

class ProgressFragment : Fragment(), SmsSenderService.ProgressListener {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        SmsSenderService.progressListener = this
        startService()

        binding.btnPause.setOnClickListener {
            if (SmsSenderService.isPaused) {
                sendServiceAction(SmsSenderService.ACTION_RESUME)
                binding.btnPause.text = "Pause"
            } else {
                sendServiceAction(SmsSenderService.ACTION_PAUSE)
                binding.btnPause.text = "Resume"
            }
        }

        binding.btnStop.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Stop Sending")
                .setMessage("Are you sure you want to stop sending messages?")
                .setPositiveButton("Stop") { _, _ -> stopService() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        updateUI(
            SmsSenderService.sentCount,
            SmsSenderService.failedCount,
            SmsSenderService.totalCount,
            SmsSenderService.currentRecipient
        )
    }

    private fun startService() {
        val settings = (requireActivity().application as BulkSenderApp).loadSettings()
        val intent = Intent(requireContext(), SmsSenderService::class.java).apply {
            action = SmsSenderService.ACTION_START
            putExtra(SmsSenderService.EXTRA_SETTINGS, settings as Serializable)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    private fun stopService() {
        sendServiceAction(SmsSenderService.ACTION_STOP)
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(requireContext(), SmsSenderService::class.java).apply {
            this.action = action
        }
        requireContext().startService(intent)
    }

    private fun updateUI(sent: Int, failed: Int, total: Int, current: String) {
        val binding = _binding ?: return
        binding.tvCurrentRecipient.text = if (current.isBlank()) "—" else current
        binding.tvSentCount.text = sent.toString()
        binding.tvFailedCount.text = failed.toString()
        binding.tvTotal.text = total.toString()
        val pending = (total - sent - failed).coerceAtLeast(0)
        binding.tvPending.text = pending.toString()
        if (total > 0) {
            val progress = ((sent + failed) * 100 / total)
            binding.progressBar.progress = progress
            binding.tvProgress.text = "$progress%"
            val remaining = pending
            val etaSeconds = remaining * ((requireContext().getSharedPreferences("simcsv_prefs", 0)
                .getInt("delay_seconds", 10)))
            val etaMin = etaSeconds / 60
            val etaSec = etaSeconds % 60
            binding.tvEta.text = if (etaMin > 0) "${etaMin}m ${etaSec}s" else "${etaSec}s"
        }
    }

    override fun onProgress(sent: Int, failed: Int, total: Int, current: String) {
        activity?.runOnUiThread { updateUI(sent, failed, total, current) }
    }

    override fun onComplete() {
        activity?.runOnUiThread {
            _binding?.let {
                it.tvStatus.text = "Sending Complete"
                it.btnPause.isEnabled = false
                it.btnStop.isEnabled = false
                it.btnDone.visibility = View.VISIBLE
                it.btnDone.setOnClickListener { findNavController().navigate(R.id.action_progress_to_logs) }
            }
        }
    }

    override fun onStopped() {
        activity?.runOnUiThread {
            _binding?.let {
                it.tvStatus.text = "Stopped"
                it.btnPause.isEnabled = false
                it.btnStop.isEnabled = false
                it.btnDone.visibility = View.VISIBLE
                it.btnDone.setOnClickListener { findNavController().navigateUp() }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        SmsSenderService.progressListener = null
        _binding = null
    }
}
