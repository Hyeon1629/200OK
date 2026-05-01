package com.checkdang.app.data.model

data class PatientProfile(
    val nickname: String = "",
    val birthDate: String = "",      // "YYYY-MM-DD"
    val gender: Gender = Gender.NONE,
    val heightCm: Float = 0f,
    val weightKg: Float = 0f
)

enum class Gender { MALE, FEMALE, NONE }
