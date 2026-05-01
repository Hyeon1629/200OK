package com.checkdang.app.ui.home

import androidx.lifecycle.ViewModel
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.GlucoseSummary
import com.checkdang.app.data.model.LifestyleSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {

    private val _glucoseSummary = MutableStateFlow<GlucoseSummary?>(MockDataProvider.getGlucoseSummary())
    val glucoseSummary: StateFlow<GlucoseSummary?> = _glucoseSummary.asStateFlow()

    private val _lifestyleSummary = MutableStateFlow<LifestyleSummary?>(MockDataProvider.getLifestyleSummary())
    val lifestyleSummary: StateFlow<LifestyleSummary?> = _lifestyleSummary.asStateFlow()

    private val _weeklyGlucose = MutableStateFlow(MockDataProvider.getWeeklyGlucose())
    val weeklyGlucose: StateFlow<List<Float>> = _weeklyGlucose.asStateFlow()
}
