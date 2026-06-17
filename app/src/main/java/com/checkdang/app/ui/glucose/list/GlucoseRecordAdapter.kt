package com.checkdang.app.ui.glucose.list

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.checkdang.app.R
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.databinding.ItemGlucoseRecordBinding
import com.checkdang.app.util.GlucoseEvaluator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ListItem {
    data class DateHeader(val label: String) : ListItem()
    data class RecordItem(val record: GlucoseRecord) : ListItem()
}

class GlucoseRecordAdapter : ListAdapter<ListItem, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_RECORD = 1

        fun buildListItems(records: List<GlucoseRecord>): List<ListItem> {
            val sdf = SimpleDateFormat("yyyy.MM.dd (E)", Locale.KOREAN)
            return buildList {
                var lastDate: String? = null
                for (record in records) {
                    val date = sdf.format(Date(record.measuredAt))
                    if (date != lastDate) {
                        add(ListItem.DateHeader(date))
                        lastDate = date
                    }
                    add(ListItem.RecordItem(record))
                }
            }
        }
    }

    object Diff : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(old: ListItem, new: ListItem) = when {
            old is ListItem.DateHeader && new is ListItem.DateHeader -> old.label == new.label
            old is ListItem.RecordItem && new is ListItem.RecordItem -> old.record.id == new.record.id
            else -> false
        }
        override fun areContentsTheSame(old: ListItem, new: ListItem) = old == new
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is ListItem.DateHeader -> TYPE_HEADER
        is ListItem.RecordItem -> TYPE_RECORD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_glucose_date_header, parent, false)
            )
            else -> RecordViewHolder(
                ItemGlucoseRecordBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.DateHeader -> (holder as HeaderViewHolder).bind(item.label)
            is ListItem.RecordItem -> (holder as RecordViewHolder).bind(item.record)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(label: String) {
            (itemView as TextView).text = label
        }
    }

    class RecordViewHolder(private val b: ItemGlucoseRecordBinding) :
        RecyclerView.ViewHolder(b.root) {

        private val timeSdf = SimpleDateFormat("HH:mm", Locale.KOREAN)

        fun bind(record: GlucoseRecord) {
            val ctx = b.root.context
            val statusColor = GlucoseEvaluator.getColor(record.status, ctx)

            // 좌측 색상 바
            b.viewStatusBar.setBackgroundColor(statusColor)

            // 시간
            b.tvTime.text = timeSdf.format(Date(record.measuredAt))

            // 시점 라벨
            b.tvTimingLabel.text = record.timing.label

            // 메모
            if (record.memo.isNullOrBlank()) {
                b.tvMemo.visibility = View.GONE
            } else {
                b.tvMemo.visibility = View.VISIBLE
                b.tvMemo.text = record.memo
            }

            // 수치
            b.tvValue.text = record.value.toString()
            b.tvValue.setTextColor(statusColor)

            // 상태 칩
            b.chipStatus.setStatus(record.status)
        }
    }
}
