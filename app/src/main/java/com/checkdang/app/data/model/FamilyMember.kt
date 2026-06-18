package com.checkdang.app.data.model

import com.checkdang.app.util.GlucoseStatus
import com.checkdang.app.util.MealTiming

data class FamilyMember(
    val id: String,
    val name: String,
    val relation: String,
    val avatarColorIndex: Int,          // 0–5
    val latestGlucose: Int,
    val latestTiming: MealTiming,
    val latestMeasuredAt: String,       // "30분 전"
    val todayCount: Int,
    val statusBadge: GlucoseStatus,
)
