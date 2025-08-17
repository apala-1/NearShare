package com.example.nearshare.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nearshare.R

data class DeviceItem(
    val endpointId: String,
    val name: String
)

class DeviceAdapter(
    private val onClick: (DeviceItem) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    private val items = mutableListOf<DeviceItem>()

    fun upsert(item: DeviceItem) {
        val i = items.indexOfFirst { it.endpointId == item.endpointId }
        if (i >= 0) {
            items[i] = item
            notifyItemChanged(i)
        } else {
            items.add(item)
            notifyItemInserted(items.size - 1)
        }
    }

    fun remove(endpointId: String) {
        val i = items.indexOfFirst { it.endpointId == endpointId }
        if (i >= 0) {
            items.removeAt(i)
            notifyItemRemoved(i)
        }
    }

    fun get(endpointId: String): DeviceItem? = items.firstOrNull { it.endpointId == endpointId }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name.ifBlank { "Unknown device" }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.name)
    }
}
