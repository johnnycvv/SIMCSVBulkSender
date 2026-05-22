package com.simcsv.bulksender.ui.logs

import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.simcsv.bulksender.databinding.FragmentLogsBinding
import com.simcsv.bulksender.logger.LogExporter
import kotlinx.coroutines.launch

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LogsViewModel by viewModels()
    private lateinit var adapter: LogsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = LogsAdapter()
        binding.rvLogs.adapter = adapter

        viewModel.logs.observe(viewLifecycleOwner) { logs ->
            adapter.submitList(logs)
            binding.tvEmptyLogs.isVisible = logs.isEmpty()
            binding.tvLogCount.text = "${logs.size} entries"
        }

        viewModel.loadLogs(requireContext())

        binding.btnExportLogs.setOnClickListener {
            lifecycleScope.launch {
                val uri = LogExporter.exportToCsv(requireContext())
                if (uri != null) {
                    val shareIntent = LogExporter.buildShareIntent(uri)
                    startActivity(shareIntent)
                } else {
                    Snackbar.make(binding.root, "Export failed", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnClearLogs.setOnClickListener {
            viewModel.clearLogs(requireContext())
        }

        binding.chipAll.setOnClickListener { viewModel.filterLogs(null, requireContext()) }
        binding.chipSent.setOnClickListener { viewModel.filterLogs("SENT", requireContext()) }
        binding.chipFailed.setOnClickListener { viewModel.filterLogs("FAILED", requireContext()) }
        binding.chipDelivered.setOnClickListener { viewModel.filterLogs("DELIVERED", requireContext()) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
