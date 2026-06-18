package com.checkdang.app.ui.family

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.checkdang.app.R
import com.checkdang.app.data.model.FamilyMember
import com.checkdang.app.databinding.ItemFamilyMemberBinding
import com.checkdang.app.util.GlucoseEvaluator
import com.checkdang.app.util.GlucoseStatus

class FamilyMemberAdapter(
    private val onClick: (FamilyMember) -> Unit,
    private val onLongClick: (FamilyMember) -> Unit,
) : ListAdapter<FamilyMember, FamilyMemberAdapter.VH>(DIFF) {

    private val avatarColorIds = intArrayOf(
        R.color.avatar_0, R.color.avatar_1, R.color.avatar_2,
        R.color.avatar_3, R.color.avatar_4, R.color.avatar_5
    )

    inner class VH(private val b: ItemFamilyMemberBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(member: FamilyMember) {
            // 아바타
            val initial = member.name.firstOrNull()?.toString() ?: "?"
            b.tvAvatar.text = initial
            val avatarColor = ContextCompat.getColor(
                b.root.context,
                avatarColorIds[member.avatarColorIndex.coerceIn(0, avatarColorIds.size - 1)]
            )
            b.tvAvatar.backgroundTintList = ColorStateList.valueOf(avatarColor)

            // 이름 + 관계
            b.tvNameRelation.text = "${member.name} · ${member.relation}"
            b.tvTodayInfo.text    = "오늘 측정 ${member.todayCount}회 · ${member.latestMeasuredAt}"

            // 상태 뱃지
            val (statusLabel, statusColor, statusBg) = when (member.statusBadge) {
                GlucoseStatus.NORMAL  -> Triple("정상", R.color.status_normal,  R.color.status_normal_bg)
                GlucoseStatus.WARNING -> Triple("주의", R.color.status_warning, R.color.status_warning_bg)
                GlucoseStatus.DANGER  -> Triple("위험", R.color.status_danger,  R.color.status_danger_bg)
            }
            b.tvStatusBadge.text = statusLabel
            b.tvStatusBadge.setTextColor(ContextCompat.getColor(b.root.context, statusColor))
            b.tvStatusBadge.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(b.root.context, statusBg))

            // 혈당 수치
            val glucoseColor = GlucoseEvaluator.getColor(member.statusBadge, b.root.context)
            b.tvGlucoseValue.text      = member.latestGlucose.toString()
            b.tvGlucoseValue.setTextColor(glucoseColor)
            b.tvTiming.text            = member.latestTiming.label
            b.tvMeasuredAt.text        = member.latestMeasuredAt

            b.root.setOnClickListener   { onClick(member) }
            b.root.setOnLongClickListener { onLongClick(member); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemFamilyMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FamilyMember>() {
            override fun areItemsTheSame(old: FamilyMember, new: FamilyMember) = old.id == new.id
            override fun areContentsTheSame(old: FamilyMember, new: FamilyMember) = old == new
        }
    }
}
