package com.simcsv.bulksender.ui.preview

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.simcsv.bulksender.R
import com.simcsv.bulksender.databinding.FragmentPreviewBinding
import com.simcsv.bulksender.permission.PermissionManager
import com.simcsv.bulksender.sms.SmsQueue

class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val jobs = SmsQueue.getAllJobs()
        binding.tvQueueCount.text = "Queue: ${jobs.size} messages"

        val adapter = PreviewAdapter(jobs)
        binding.rvPreview.adapter = adapter

        binding.btnStartSending.setOnClickListener {
            if (!PermissionManager.hasSmsPermissions(requireContext())) {
                Snackbar.make(
                    binding.root,
                    "SMS permission required. Grant it then try again.",
                    Snackbar.LENGTH_LONG
                ).setAction("Grant") {
                    PermissionManager.requestSmsPermissions(this)
                }.show()
                return@setOnClickListener
            }

            if (SmsQueue.totalCount == 0) {
                Snackbar.make(binding.root, "No contacts loaded. Import a CSV first.", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (SmsQueue.getAllJobs().all { it.contact.message.isBlank() }) {
                Snackbar.make(
                    binding.root,
                    "No message set. Go back and type a blast message.",
                    Snackbar.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            findNavController().navigate(R.id.action_preview_to_progress)
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionManager.REQUEST_CODE_SMS) {
            if (PermissionManager.hasSmsPermissions(requireContext())) {
                Snackbar.make(binding.root, "Permissions granted. Tap Start Sending.", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, "SMS permission denied — cannot send messages.", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
