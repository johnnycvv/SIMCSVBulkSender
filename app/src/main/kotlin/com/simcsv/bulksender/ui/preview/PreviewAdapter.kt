package com.simcsv.bulksender.ui.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simcsv.bulksender.data.SendJob
import com.simcsv.bulksender.databinding.ItemPreviewBinding

class PreviewAdapter(private val jobs: List<SendJob>) :
    RecyclerView.Adapter<PreviewAdapter.VH>() {

    inner class VH(val binding: ItemPreviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val job = jobs[position]
        holder.binding.tvIndex.text = "#${position + 1}"
        holder.binding.tvPhone.text = job.contact.phoneNumber
        holder.binding.tvName.text = job.contact.name.ifBlank { "—" }
        holder.binding.tvMessage.text = job.contact.message
        holder.binding.tvStatus.text = job.status.name
    }

    override fun getItemCount() = jobs.size
}
