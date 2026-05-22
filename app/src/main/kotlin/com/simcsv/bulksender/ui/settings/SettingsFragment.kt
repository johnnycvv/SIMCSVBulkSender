package com.simcsv.bulksender.ui.settings

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.simcsv.bulksender.BulkSenderApp
import com.simcsv.bulksender.databinding.FragmentSettingsBinding
import com.simcsv.bulksender.data.AppSettings
import com.simcsv.bulksender.sms.SimSelector

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as BulkSenderApp
        val settings = app.loadSettings()

        binding.etDelay.setText(settings.delaySeconds.toString())
        binding.switchRandomDelay.isChecked = settings.randomizeDelay
        binding.etRandomMin.setText(settings.randomDelayMinSeconds.toString())
        binding.etRandomMax.setText(settings.randomDelayMaxSeconds.toString())
        binding.etMaxRetry.setText(settings.maxRetryCount.toString())
        binding.etDailyCap.setText(settings.dailySendCap.toString())
        binding.switchNotify.isChecked = settings.notifyOnComplete

        updateRandomDelayVisibility(settings.randomizeDelay)

        binding.switchRandomDelay.setOnCheckedChangeListener { _, checked ->
            updateRandomDelayVisibility(checked)
        }

        val sims = SimSelector.getAvailableSims(requireContext())
        val simNames = sims.map { "${it.displayName} (${it.carrierName})" }.toTypedArray()
        if (simNames.isEmpty()) {
            binding.tvSimInfo.text = "No SIM cards detected"
        } else {
            binding.tvSimInfo.text = simNames.joinToString("\n") { "• $it" }
        }

        val simSlotLabels = sims.mapIndexed { i, s -> "SIM ${i + 1}: ${s.displayName}" }.toTypedArray()
        if (simSlotLabels.size > 1) {
            binding.layoutSimSlot.visibility = View.VISIBLE
            binding.radioSim1.text = simSlotLabels.getOrElse(0) { "SIM 1" }
            binding.radioSim2.text = simSlotLabels.getOrElse(1) { "SIM 2" }
            if (settings.selectedSimSlot == 1) binding.radioSim2.isChecked = true
            else binding.radioSim1.isChecked = true
        } else {
            binding.layoutSimSlot.visibility = View.GONE
        }

        binding.btnSaveSettings.setOnClickListener {
            saveSettings(app, sims.size)
        }
    }

    private fun updateRandomDelayVisibility(show: Boolean) {
        binding.layoutRandomDelay.visibility = if (show) View.VISIBLE else View.GONE
        binding.layoutFixedDelay.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun saveSettings(app: BulkSenderApp, simCount: Int) {
        val delay = binding.etDelay.text.toString().toIntOrNull()?.coerceIn(5, 600) ?: 10
        val randomMin = binding.etRandomMin.text.toString().toIntOrNull()?.coerceAtLeast(5) ?: 5
        val randomMax = binding.etRandomMax.text.toString().toIntOrNull()?.coerceAtLeast(randomMin + 1) ?: 30
        val maxRetry = binding.etMaxRetry.text.toString().toIntOrNull()?.coerceIn(0, 10) ?: 3
        val dailyCap = binding.etDailyCap.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 500
        val simSlot = if (simCount > 1 && binding.radioSim2.isChecked) 1 else 0

        val settings = AppSettings(
            delaySeconds = delay,
            randomizeDelay = binding.switchRandomDelay.isChecked,
            randomDelayMinSeconds = randomMin,
            randomDelayMaxSeconds = randomMax,
            maxRetryCount = maxRetry,
            dailySendCap = dailyCap,
            selectedSimSlot = simSlot,
            notifyOnComplete = binding.switchNotify.isChecked
        )
        app.saveSettings(settings)
        Snackbar.make(binding.root, "Settings saved", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
