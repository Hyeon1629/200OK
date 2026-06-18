package com.checkdang.app.ui.glucose.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.checkdang.app.R
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.InsulinRecord
import com.checkdang.app.databinding.ItemGlucoseRecordBinding
import com.checkdang.app.databinding.ItemInsulinRecordBinding
import com.checkdang.app.ui.glucose.TimelineEntry
import com.checkdang.app.util.GlucoseEvaluator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ListItem {
    data class DateHeader(val label: String) : ListItem()
    data class GlucoseItem(val record: GlucoseRecord) : ListItem()
    data class InsulinItem(val record: InsulinRecord) : ListItem()
}

class GlucoseRecordAdapter : ListAdapter<ListItem, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private const val TYPE_HEADER  = 0
        private const val TYPE_GLUCOSE = 1
        private const val TYPE_INSULIN = 2

        /** 혈당 + 인슐린 타임라인을 날짜 헤더가 붙은 리스트 아이템으로 변환. */
        fun buildListItems(entries: List<TimelineEntry>): List<ListItem> {
            val sdf = SimpleDateFormat("yyyy.MM.dd (E)", Locale.KOREAN)
            return buildList {
                var lastDate: String? = null
                for (entry in entries) {
                    val date = sdf.format(Date(entry.time))
                    if (date != lastDate) {
                        add(ListItem.DateHeader(date))
                        lastDate = date
                    }
                    when (entry) {
                        is TimelineEntry.Glucose -> add(ListItem.GlucoseItem(entry.record))
                        is TimelineEntry.Insulin -> add(ListItem.InsulinItem(entry.record))
                    }
                }
            }
        }
    }

    object Diff : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(old: ListItem, new: ListItem) = when {
            old is ListItem.DateHeader && new is ListItem.DateHeader -> old.label == new.label
            old is ListItem.GlucoseItem && new is ListItem.GlucoseItem -> old.record.id == new.record.id
            old is ListItem.InsulinItem && new is ListItem.InsulinItem -> old.record.id == new.record.id
            else -> false
        }
        override fun areContentsTheSame(old: ListItem, new: ListItem) = old == new
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is ListItem.DateHeader  -> TYPE_HEADER
        is ListItem.GlucoseItem -> TYPE_GLUCOSE
        is ListItem.InsulinItem -> TYPE_INSULIN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_glucose_date_header, parent, false)
            )
            TYPE_INSULIN -> InsulinViewHolder(
                ItemInsulinRecordBinding.inflate(inflater, parent, false)
            )
            else -> GlucoseViewHolder(
                ItemGlucoseRecordBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.DateHeader  -> (holder as HeaderViewHolder).bind(item.label)
            is ListItem.GlucoseItem -> (holder as GlucoseViewHolder).bind(item.record)
            is ListItem.InsulinItem -> (holder as InsulinViewHolder).bind(item.record)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(label: String) {
            (itemView as TextView).text = label
        }
    }

    class GlucoseViewHolder(private val b: ItemGlucoseRecordBinding) :
        RecyclerView.ViewHolder(b.root) {

        private val timeSdf = SimpleDateFormat("HH:mm", Locale.KOREAN)

        fun bind(record: GlucoseRecord) {
            val ctx = b.root.context
            val statusColor = GlucoseEvaluator.getColor(record.status, ctx)

            b.viewStatusBar.setBackgroundColor(statusColor)
            b.tvTime.text = timeSdf.format(Date(record.measuredAt))
            b.tvTimingLabel.text = record.timing.label

            if (record.memo.isNullOrBlank()) {
                b.tvMemo.visibility = View.GONE
            } else {
                b.tvMemo.visibility = View.VISIBLE
                b.tvMemo.text = record.memo
            }

            b.tvValue.text = record.value.toString()
            b.tvValue.setTextColor(statusColor)
            b.chipStatus.setStatus(record.status)
        }
    }

    class InsulinViewHolder(private val b: ItemInsulinRecordBinding) :
        RecyclerView.ViewHolder(b.root) {

        private val timeSdf = SimpleDateFormat("HH:mm", Locale.KOREAN)

        fun bind(record: InsulinRecord) {
            b.tvTime.text = timeSdf.format(Date(record.injectedAt))
            b.tvTypeLabel.text = "💉 인슐린 · ${record.type.label}"

            if (record.memo.isNullOrBlank()) {
                b.tvMemo.visibility = View.GONE
            } else {
                b.tvMemo.visibility = View.VISIBLE
                b.tvMemo.text = record.memo
            }

            b.tvUnits.text = record.unitsLabel
        }
    }
}
