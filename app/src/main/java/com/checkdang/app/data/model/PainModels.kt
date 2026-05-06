package com.checkdang.app.data.model

import java.util.UUID

enum class BodyView { FRONT, BACK }

enum class MusclePart(val label: String) {
    // 머리/목
    TEMPORALIS("관자놀이근"),
    OCCIPITALIS("후두근"),
    STERNOCLEIDOMASTOID("흉쇄유돌근"),
    SPLENIUS("판상근"),
    SEMISPINALIS("두반극근"),
    // 어깨
    DELTOID_FRONT("삼각근(전면)"),
    DELTOID_REAR("삼각근(후면)"),
    TRAPEZIUS_UPPER("승모근(상부)"),
    TRAPEZIUS_MID("승모근(중부)"),
    ROTATOR_CUFF("회전근개"),
    RHOMBOID("능형근"),
    PECTORALIS_MAJOR("대흉근"),
    PECTORALIS_MINOR("소흉근"),
    SERRATUS("전거근"),
    // 팔
    BICEPS("이두근"),
    TRICEPS("삼두근"),
    BRACHIALIS("상완근"),
    // 복부/허리
    RECTUS_ABDOMINIS("복직근"),
    OBLIQUE("복사근"),
    QUADRATUS_LUMBORUM("요방형근"),
    ERECTOR_SPINAE("척추기립근"),
    MULTIFIDUS("다열근"),
    // 골반/엉덩이
    ILIOPSOAS("장요근"),
    GLUTEUS("둔근"),
    SARTORIUS("봉공근"),
    // 허벅지
    QUADRICEPS("대퇴사두근"),
    HAMSTRING("햄스트링"),
    TFL("대퇴근막장근"),
    // 무릎/정강이
    PATELLAR_LIGAMENT("슬개인대"),
    POPLITEUS("슬와근"),
    GASTROCNEMIUS("비복근"),
    TIBIALIS_ANTERIOR("전경골근"),
    SOLEUS("가자미근"),
}

fun BodyPart.muscles(): List<MusclePart> = when (this) {
    BodyPart.HEAD                  -> listOf(MusclePart.TEMPORALIS, MusclePart.OCCIPITALIS)
    BodyPart.NECK_FRONT            -> listOf(MusclePart.STERNOCLEIDOMASTOID, MusclePart.SPLENIUS)
    BodyPart.NECK_BACK             -> listOf(MusclePart.TRAPEZIUS_UPPER, MusclePart.SPLENIUS, MusclePart.SEMISPINALIS)
    BodyPart.LEFT_SHOULDER_FRONT,
    BodyPart.RIGHT_SHOULDER_FRONT  -> listOf(MusclePart.DELTOID_FRONT, MusclePart.PECTORALIS_MAJOR, MusclePart.PECTORALIS_MINOR)
    BodyPart.LEFT_SHOULDER_BACK,
    BodyPart.RIGHT_SHOULDER_BACK   -> listOf(MusclePart.DELTOID_REAR, MusclePart.TRAPEZIUS_UPPER, MusclePart.ROTATOR_CUFF, MusclePart.RHOMBOID)
    BodyPart.CHEST                 -> listOf(MusclePart.PECTORALIS_MAJOR, MusclePart.PECTORALIS_MINOR, MusclePart.SERRATUS)
    BodyPart.LEFT_ARM_FRONT,
    BodyPart.RIGHT_ARM_FRONT       -> listOf(MusclePart.BICEPS, MusclePart.TRICEPS, MusclePart.BRACHIALIS)
    BodyPart.ABDOMEN               -> listOf(MusclePart.RECTUS_ABDOMINIS, MusclePart.OBLIQUE)
    BodyPart.LEFT_HIP_FRONT,
    BodyPart.RIGHT_HIP_FRONT       -> listOf(MusclePart.ILIOPSOAS, MusclePart.GLUTEUS, MusclePart.SARTORIUS)
    BodyPart.LEFT_THIGH_FRONT,
    BodyPart.RIGHT_THIGH_FRONT     -> listOf(MusclePart.QUADRICEPS, MusclePart.HAMSTRING, MusclePart.TFL)
    BodyPart.LEFT_KNEE,
    BodyPart.RIGHT_KNEE            -> listOf(MusclePart.PATELLAR_LIGAMENT, MusclePart.POPLITEUS, MusclePart.GASTROCNEMIUS)
    BodyPart.LEFT_SHIN,
    BodyPart.RIGHT_SHIN            -> listOf(MusclePart.TIBIALIS_ANTERIOR, MusclePart.GASTROCNEMIUS, MusclePart.SOLEUS)
    BodyPart.UPPER_BACK            -> listOf(MusclePart.TRAPEZIUS_MID, MusclePart.RHOMBOID, MusclePart.ERECTOR_SPINAE)
    BodyPart.LOWER_BACK            -> listOf(MusclePart.QUADRATUS_LUMBORUM, MusclePart.ERECTOR_SPINAE, MusclePart.MULTIFIDUS)
}

enum class BodyPart(val label: String, val view: BodyView) {
    // Front
    HEAD("머리", BodyView.FRONT),
    NECK_FRONT("목 (앞)", BodyView.FRONT),
    LEFT_SHOULDER_FRONT("왼쪽 어깨", BodyView.FRONT),
    RIGHT_SHOULDER_FRONT("오른쪽 어깨", BodyView.FRONT),
    CHEST("가슴", BodyView.FRONT),
    LEFT_ARM_FRONT("왼팔", BodyView.FRONT),
    RIGHT_ARM_FRONT("오른팔", BodyView.FRONT),
    ABDOMEN("복부", BodyView.FRONT),
    LEFT_HIP_FRONT("왼쪽 골반", BodyView.FRONT),
    RIGHT_HIP_FRONT("오른쪽 골반", BodyView.FRONT),
    LEFT_THIGH_FRONT("왼쪽 허벅지", BodyView.FRONT),
    RIGHT_THIGH_FRONT("오른쪽 허벅지", BodyView.FRONT),
    LEFT_KNEE("왼쪽 무릎", BodyView.FRONT),
    RIGHT_KNEE("오른쪽 무릎", BodyView.FRONT),
    LEFT_SHIN("왼쪽 정강이", BodyView.FRONT),
    RIGHT_SHIN("오른쪽 정강이", BodyView.FRONT),
    // Back
    NECK_BACK("목 (뒤)", BodyView.BACK),
    UPPER_BACK("등 위", BodyView.BACK),
    LOWER_BACK("허리", BodyView.BACK),
    LEFT_SHOULDER_BACK("왼쪽 어깨 (뒤)", BodyView.BACK),
    RIGHT_SHOULDER_BACK("오른쪽 어깨 (뒤)", BodyView.BACK),
}

enum class PainType(val label: String) {
    SHARP("찌르는 통증"),
    DULL("둔한 통증"),
    BURNING("타는 느낌"),
    THROBBING("욱신거림"),
    STIFFNESS("뻣뻣함"),
    NUMBNESS("저림"),
}

enum class CorrelationLevel(val label: String) {
    HIGH("높음"),
    MEDIUM("중간"),
    LOW("낮음"),
}

data class Correlation(
    val factor: String,
    val level: CorrelationLevel,
    val description: String,
)

data class PainRecord(
    val id: String = UUID.randomUUID().toString(),
    val bodyPart: BodyPart,
    val musclePart: MusclePart? = null,
    val intensity: Int,          // 1–10
    val painTypes: List<PainType>,
    val recordedAt: Long = System.currentTimeMillis(),
)

data class AIAnalysisResult(
    val painRecord: PainRecord,
    val summary: String,
    val correlations: List<Correlation>,
    val recommendation: String,
)
