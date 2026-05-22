package com.simcsv.bulksender.ui.preview

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.simcsv.bulksender.R
import com.simcsv.bulksender.databinding.FragmentPreviewBinding
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
            findNavController().navigate(R.id.action_preview_to_progress)
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
