package com.checkdang.app.ui.lifestyle

import androidx.lifecycle.ViewModel
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary

class LifestyleViewModel : ViewModel() {
    val exercise: ExerciseSummary? = MockDataProvider.getExerciseSummary()
    val meal: MealSummary?         = MockDataProvider.getMealSummary()
    val sleep: SleepSummary?       = MockDataProvider.getSleepSummary()
}
