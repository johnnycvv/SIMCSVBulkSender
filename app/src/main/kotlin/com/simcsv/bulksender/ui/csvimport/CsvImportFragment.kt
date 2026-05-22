package com.simcsv.bulksender.ui.csvimport

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.simcsv.bulksender.R
import com.simcsv.bulksender.databinding.FragmentCsvImportBinding
import com.simcsv.bulksender.permission.PermissionManager
import com.simcsv.bulksender.sms.SmsQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CsvImportFragment : Fragment() {

    private var _binding: FragmentCsvImportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CsvImportViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.parseCsv(uri)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCsvImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etBlastMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                val smsCount = if (len == 0) 1 else ((len - 1) / 160) + 1
                binding.tvSmsCount.text = when {
                    len == 0      -> "1 SMS per recipient"
                    smsCount == 1 -> "$len / 160 characters — 1 SMS per recipient"
                    else          -> "$len characters — $smsCount SMS parts per recipient"
                }
            }
        })

        binding.btnChooseFile.setOnClickListener {
            if (!PermissionManager.hasStoragePermissions(requireContext())) {
                PermissionManager.requestStoragePermissions(this)
                return@setOnClickListener
            }
            openFilePicker()
        }

        binding.btnProceedToPreview.setOnClickListener {
            val blastMessage = binding.etBlastMessage.text?.toString()?.trim() ?: ""
            val result = viewModel.parseResult.value ?: return@setOnClickListener

            if (blastMessage.isEmpty() && result.validContacts.any { it.message.isBlank() }) {
                Snackbar.make(
                    binding.root,
                    "Type a blast message above before proceeding.",
                    Snackbar.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            binding.btnProceedToPreview.isEnabled = false
            binding.progressBar.isVisible = true

            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    val contacts = if (blastMessage.isNotEmpty()) {
                        result.validContacts.map { it.copy(message = blastMessage) }
                    } else {
                        result.validContacts
                    }
                    SmsQueue.load(contacts)
                }
                if (_binding != null) {
                    findNavController().navigate(R.id.action_import_to_preview)
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.isVisible = loading
            binding.btnChooseFile.isEnabled = !loading
        }

        viewModel.parseResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            binding.cardResults.isVisible = true
            binding.tvTotalRows.text      = result.totalRows.toString()
            binding.tvValidRows.text      = result.validContacts.size.toString()
            binding.tvInvalidRows.text    = result.invalidContacts.size.toString()
            binding.tvHeaderInfo.text     = result.headerInfo
            binding.btnProceedToPreview.isEnabled = result.validContacts.isNotEmpty()

            if (result.invalidContacts.isNotEmpty()) {
                binding.cardInvalidRows.isVisible = true
                binding.rvInvalidRows.adapter = InvalidRowsAdapter(result.invalidContacts)
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
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/csv", "text/comma-separated-values", "application/csv", "text/plain"))
        }
        filePickerLauncher.launch(Intent.createChooser(intent, "Select CSV File"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
