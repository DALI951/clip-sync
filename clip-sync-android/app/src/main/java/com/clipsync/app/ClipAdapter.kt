package com.clipsync.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class ClipAdapter(
    private val items: List<ClipItem>,
    private val onCopy: (String) -> Unit
) : RecyclerView.Adapter<ClipAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sourceText: TextView = view.findViewById(R.id.itemSource)
        val timeText: TextView = view.findViewById(R.id.itemTime)
        val contentText: TextView = view.findViewById(R.id.itemContent)
        val copyBtn: Button = view.findViewById(R.id.itemCopyBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clip, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.sourceText.text = item.source
        holder.timeText.text = item.timestamp
        holder.contentText.text = item.text
        holder.copyBtn.setOnClickListener {
            onCopy(item.text)
        }
    }

    override fun getItemCount() = items.size
}
