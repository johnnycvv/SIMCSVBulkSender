package com.simcsv.bulksender.ui.csvimport

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.simcsv.bulksender.R
import com.simcsv.bulksender.databinding.FragmentCsvImportBinding
import com.simcsv.bulksender.permission.PermissionManager
import com.simcsv.bulksender.sms.SmsQueue

class CsvImportFragment : Fragment() {

    private var _binding: FragmentCsvImportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CsvImportViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.parseCsv(requireContext(), uri)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCsvImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnChooseFile.setOnClickListener {
            if (!PermissionManager.hasStoragePermissions(requireContext())) {
                PermissionManager.requestStoragePermissions(this)
                return@setOnClickListener
            }
            openFilePicker()
        }

        binding.btnProceedToPreview.setOnClickListener {
            viewModel.parseResult.value?.let { result ->
                SmsQueue.load(result.validContacts)
                findNavController().navigate(R.id.action_import_to_preview)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.isVisible = loading
            binding.btnChooseFile.isEnabled = !loading
        }

        viewModel.parseResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            binding.cardResults.isVisible = true
            binding.tvTotalRows.text = result.totalRows.toString()
            binding.tvValidRows.text = result.validContacts.size.toString()
            binding.tvInvalidRows.text = result.invalidContacts.size.toString()
            binding.tvHeaderInfo.text = result.headerInfo

            binding.btnProceedToPreview.isEnabled = result.validContacts.isNotEmpty()

            if (result.invalidContacts.isNotEmpty()) {
                binding.cardInvalidRows.isVisible = true
                val adapter = InvalidRowsAdapter(result.invalidContacts)
                binding.rvInvalidRows.adapter = adapter
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error ?: return@observe
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain"))
        }
        filePickerLauncher.launch(Intent.createChooser(intent, "Select CSV File"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
