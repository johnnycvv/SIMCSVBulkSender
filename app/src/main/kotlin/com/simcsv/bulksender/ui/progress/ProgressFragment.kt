package com.simcsv.bulksender.ui.progress

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simcsv.bulksender.BulkSenderApp
import com.simcsv.bulksender.R
import com.simcsv.bulksender.databinding.FragmentProgressBinding
import com.simcsv.bulksender.sms.SmsSenderService
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*

class ProgressFragment : Fragment(), SmsSenderService.Companion.ProgressListener {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    private val cooldownHandler  = Handler(Looper.getMainLooper())
    private val cooldownRunnable = object : Runnable {
        override fun run() {
            updateCooldownTimer()
            cooldownHandler.postDelayed(this, 1000)
        }
    }

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
                sendAction(SmsSenderService.ACTION_RESUME)
                binding.btnPause.text = "Pause"
                binding.tvStatus.text = "Sending in Progress..."
            } else {
                sendAction(SmsSenderService.ACTION_PAUSE)
                binding.btnPause.text = "Resume"
                binding.tvStatus.text = "Paused"
            }
        }

        binding.btnStop.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Stop Sending")
                .setMessage("Stop sending? Progress is saved to Logs.")
                .setPositiveButton("Stop") { _, _ -> sendAction(SmsSenderService.ACTION_STOP) }
                .setNegativeButton("Cancel", null)
                .show()
        }

        updateUI(
            SmsSenderService.sentCount,
            SmsSenderService.failedCount,
            SmsSenderService.deliveredCount,
            SmsSenderService.totalCount,
            SmsSenderService.currentRecipient
        )
    }

    private fun startService() {
        val settings = (requireActivity().application as BulkSenderApp).loadSettings()
        ContextCompat.startForegroundService(
            requireContext(),
            Intent(requireContext(), SmsSenderService::class.java).apply {
                action = SmsSenderService.ACTION_START
                putExtra(SmsSenderService.EXTRA_SETTINGS, settings as Serializable)
            }
        )
    }

    private fun sendAction(action: String) {
        requireContext().startService(
            Intent(requireContext(), SmsSenderService::class.java).apply { this.action = action }
        )
    }

    private fun updateUI(sent: Int, failed: Int, delivered: Int, total: Int, current: String) {
        val b = _binding ?: return
        b.tvCurrentRecipient.text = current.ifBlank { "-" }
        b.tvSentCount.text        = sent.toString()
        b.tvFailedCount.text      = failed.toString()
        b.tvDeliveredCount.text   = delivered.toString()
        b.tvTotal.text            = total.toString()
        val pending = (total - sent - failed).coerceAtLeast(0)
        b.tvPending.text = pending.toString()
        if (total > 0) {
            val pct = ((sent + failed) * 100 / total)
            b.progressBar.progress = pct
            b.tvProgress.text = "$pct%"
            val delay = requireContext()
                .getSharedPreferences("simcsv_prefs", 0).getInt("delay_seconds", 10)
            b.tvEta.text = formatEta(pending * delay)
        }
    }

    private fun updateCooldownTimer() {
        val b = _binding ?: return
        val endsAt = SmsSenderService.cooldownEndsAt
        if (SmsSenderService.isBatchCooling && endsAt > 0) {
            val remaining = ((endsAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            b.cardCooldown.isVisible = true
            b.tvCooldownTimer.text   = "Cooldown: ${formatEta(remaining.toInt())} remaining"
        } else {
            b.cardCooldown.isVisible = false
        }
    }

    private fun formatEta(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else  -> "${s}s"
        }
    }

    override fun onProgress(sent: Int, failed: Int, delivered: Int, total: Int, current: String) {
        activity?.runOnUiThread { updateUI(sent, failed, delivered, total, current) }
    }

    override fun onDailyCapReached(cap: Int) {
        activity?.runOnUiThread {
            val b = _binding ?: return@runOnUiThread
            b.tvStatus.text         = "Daily Cap Reached"
            b.cardWarning.isVisible = true
            b.tvWarning.text        = "Daily send cap of $cap messages reached."
            b.btnPause.isEnabled    = false
            b.btnStop.isEnabled     = false
            b.btnDone.isVisible     = true
            b.btnDone.setOnClickListener { findNavController().navigate(R.id.action_progress_to_logs) }
        }
    }

    override fun onBatchCooldown(resumeAt: Long) {
        activity?.runOnUiThread {
            val b = _binding ?: return@runOnUiThread
            val label = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(resumeAt))
            b.tvStatus.text          = if (SmsSenderService.isRunning) "Batch Cooldown" else "Scheduled"
            b.cardCooldown.isVisible = true
            b.tvCooldownTimer.text   = "Resuming at $label"
            cooldownHandler.post(cooldownRunnable)
        }
    }

    override fun onComplete() {
        cooldownHandler.removeCallbacks(cooldownRunnable)
        activity?.runOnUiThread {
            val b = _binding ?: return@runOnUiThread
            b.tvStatus.text          = "Sending Complete"
            b.cardCooldown.isVisible = false
            b.cardWarning.isVisible  = false
            b.btnPause.isEnabled     = false
            b.btnStop.isEnabled      = false
            b.btnDone.isVisible      = true
            b.btnDone.setOnClickListener { findNavController().navigate(R.id.action_progress_to_logs) }
        }
    }

    override fun onStopped() {
        cooldownHandler.removeCallbacks(cooldownRunnable)
        activity?.runOnUiThread {
            val b = _binding ?: return@runOnUiThread
            b.tvStatus.text          = "Stopped"
            b.cardCooldown.isVisible = false
            b.btnPause.isEnabled     = false
            b.btnStop.isEnabled      = false
            b.btnDone.isVisible      = true
            b.btnDone.setOnClickListener { findNavController().navigateUp() }
        }
    }

    override fun onDestroyView() {
        cooldownHandler.removeCallbacks(cooldownRunnable)
        SmsSenderService.progressListener = null
        _binding = null
        super.onDestroyView()
    }
}
