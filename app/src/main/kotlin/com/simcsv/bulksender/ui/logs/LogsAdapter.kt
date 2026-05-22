package com.simcsv.bulksender.ui.logs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.simcsv.bulksender.data.SmsLog
import com.simcsv.bulksender.databinding.ItemLogBinding
import java.text.SimpleDateFormat
import java.util.*

class LogsAdapter : ListAdapter<SmsLog, LogsAdapter.VH>(DIFF) {

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SmsLog>() {
            override fun areItemsTheSame(a: SmsLog, b: SmsLog) = a.id == b.id
            override fun areContentsTheSame(a: SmsLog, b: SmsLog) = a == b
        }
    }

    inner class VH(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val log = getItem(position)
        holder.binding.tvPhone.text = log.phoneNumber
        holder.binding.tvName.text = log.name.ifBlank { "—" }
        holder.binding.tvStatus.text = log.status
        holder.binding.tvTimestamp.text = dateFormat.format(Date(log.timestamp))
        holder.binding.tvMessage.text = log.message
        if (log.errorMessage.isNotBlank()) {
            holder.binding.tvError.text = log.errorMessage
            holder.binding.tvError.visibility = android.view.View.VISIBLE
        } else {
            holder.binding.tvError.visibility = android.view.View.GONE
        }

        val color = when (log.status) {
            "SENT" -> 0xFF388E3C.toInt()
            "DELIVERED" -> 0xFF1976D2.toInt()
            "FAILED" -> 0xFFD32F2F.toInt()
            else -> 0xFF757575.toInt()
        }
        holder.binding.tvStatus.setTextColor(color)
    }
}
