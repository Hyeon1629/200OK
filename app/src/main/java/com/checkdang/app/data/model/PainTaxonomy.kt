package com.checkdang.app.data.model

/**
 * 통증 분류 taxonomy — 부위 선택 후 통증의 성질/상황을 태그로 기록한다.
 * (확정 자산 사양 기준. 그룹/태그/색상 변경 시 디자인 재승인 필요)
 */
object PainTaxonomy {

    /** 통증 분류 그룹 — 라벨 + 그룹 색상(#RRGGBB) + 하위 태그들 */
    data class Group(val label: String, val colorHex: String, val tags: List<String>)

    /** ① 통증 성질 (어떤 느낌인지) — 그룹별 색상 */
    val QUALITY: List<Group> = listOf(
        Group("둔한 통증",   "#8E7CC3", listOf("뻐근함", "묵직함", "압박감", "조이는 느낌", "결림")),
        Group("날카로운 통증", "#E0603A", listOf("찌르는 듯함", "칼로 베는 듯함", "콕콕 쑤심", "욱신거림")),
        Group("신경성 통증", "#D9A227", listOf("찌릿함", "저림", "타는 듯함", "전기 오는 느낌", "방사통")),
        Group("근육성 통증", "#3E8E7E", listOf("당김", "뭉침", "경련", "떨림", "힘 빠짐")),
        Group("관절성 통증", "#3A78C2", listOf("시큰거림", "뻣뻣함", "삐걱거리는 느낌", "붓는 느낌", "불안정한 느낌")),
    )

    /** ② 통증 상황 (언제 아픈지) — 선택 시 green */
    val SITUATION: List<Group> = listOf(
        Group("동작별",     "#43A047", listOf("움직일 때", "가만히 있을 때", "누르면 아픔", "특정 자세에서 아픔")),
        Group("일상동작별", "#43A047", listOf("걸을 때", "계단 오를 때", "앉을 때", "누울 때", "굽힐 때", "들어올릴 때", "숨쉴 때")),
        Group("시간대별",   "#43A047", listOf("아침에 심함", "활동 후 심함", "밤에 심함", "날씨 변화 시 심함")),
    )

    /** 신경 관련 성질 태그 — Mock 분석의 신경 민감도 상관관계 판정에 사용 */
    val NEURAL_TAGS: Set<String> = setOf("찌릿함", "저림", "타는 듯함", "전기 오는 느낌", "방사통")
}
