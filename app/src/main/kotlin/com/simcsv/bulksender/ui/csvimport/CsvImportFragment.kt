package com.simcsv.bulksender.ui.csvimport

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
import com.simcsv.bulksender.csv.CsvParser
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
        val uri = result.data?.data ?: return@registerForActivityResult

        val appContext = requireContext().applicationContext
        viewModel.setLoading()

        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }

                if (bytes == null || bytes.isEmpty()) {
                    viewModel.setError("Could not read file — stream was empty")
                    return@launch
                }

                val parseResult = withContext(Dispatchers.Default) {
                    CsvParser.parseBytes(bytes)
                }

                viewModel.setResult(parseResult)

            } catch (e: Exception) {
                viewModel.setError("Error: ${e.message ?: e.javaClass.simpleName}")
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
                Snackbar.make(binding.root, "Type a blast message above before proceeding.", Snackbar.LENGTH_LONG).show()
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
            binding.tvDebugStatus.visibility = View.VISIBLE
            binding.tvDebugStatus.text = "Parsed: ${result.totalRows} rows, ${result.validContacts.size} valid. ${result.headerInfo}"
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

        viewModel.error.observe(viewLifecycleOwner) { err ->
            err ?: return@observe
            binding.tvDebugStatus.visibility = View.VISIBLE
            binding.tvDebugStatus.text = "ERROR: $err"
            Snackbar.make(binding.root, err, Snackbar.LENGTH_LONG).show()
        }

        viewModel.debugStatus.observe(viewLifecycleOwner) { status ->
            binding.tvDebugStatus.visibility = View.VISIBLE
            binding.tvDebugStatus.text = status
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/csv", "text/comma-separated-values", "application/csv", "text/plain"
            ))
        }
        filePickerLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
