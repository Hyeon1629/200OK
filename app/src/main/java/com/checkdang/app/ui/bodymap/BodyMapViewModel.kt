package com.checkdang.app.ui.bodymap

import androidx.lifecycle.ViewModel
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.PainRecord
import kotlinx.coroutines.flow.StateFlow

class BodyMapViewModel : ViewModel() {
    val painRecords: StateFlow<List<PainRecord>> = MockDataProvider.painRecordsFlow

    fun addPainRecord(record: PainRecord) {
        MockDataProvider.addPainRecord(record)
    }
}
