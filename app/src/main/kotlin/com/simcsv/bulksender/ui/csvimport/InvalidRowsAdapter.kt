package com.simcsv.bulksender.ui.csvimport

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simcsv.bulksender.data.Contact
import com.simcsv.bulksender.databinding.ItemInvalidRowBinding

class InvalidRowsAdapter(private val contacts: List<Contact>) :
    RecyclerView.Adapter<InvalidRowsAdapter.VH>() {

    inner class VH(val binding: ItemInvalidRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemInvalidRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val contact = contacts[position]
        holder.binding.tvRow.text = "Row ${contact.rowIndex}"
        holder.binding.tvPhone.text = contact.phoneNumber.ifBlank { "(empty)" }
        holder.binding.tvError.text = contact.validationError
    }

    override fun getItemCount() = contacts.size
}
