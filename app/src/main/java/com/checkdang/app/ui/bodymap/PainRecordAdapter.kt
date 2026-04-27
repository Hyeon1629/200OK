package com.checkdang.app.ui.bodymap

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.checkdang.app.data.model.PainRecord
import com.checkdang.app.databinding.ItemPainRecordBinding
import java.util.concurrent.TimeUnit

class PainRecordAdapter(
    private val onClick: (PainRecord) -> Unit
) : ListAdapter<PainRecord, PainRecordAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemPainRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: PainRecord) {
            binding.tvIntensity.text  = record.intensity.toString()
            binding.tvPart.text       = record.bodyPart.label
            binding.tvPainTypes.text  = record.painTypes.joinToString(" · ") { it.label }
            binding.tvDate.text       = formatRelativeTime(record.recordedAt)
            binding.root.setOnClickListener { onClick(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPainRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    private fun formatRelativeTime(timeMs: Long): String {
        val diff = System.currentTimeMillis() - timeMs
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            days == 0L -> "오늘"
            days == 1L -> "어제"
            else       -> "${days}일 전"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PainRecord>() {
            override fun areItemsTheSame(old: PainRecord, new: PainRecord) = old.id == new.id
            override fun areContentsTheSame(old: PainRecord, new: PainRecord) = old == new
        }
    }
}
