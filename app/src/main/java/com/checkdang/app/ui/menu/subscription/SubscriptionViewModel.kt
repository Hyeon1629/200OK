package com.checkdang.app.ui.menu.subscription

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.checkdang.app.CheckDangApplication
import com.checkdang.app.data.billing.BillingRepository
import com.checkdang.app.data.billing.BillingState
import kotlinx.coroutines.flow.StateFlow

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BillingRepository =
        (application as CheckDangApplication).billingRepository

    val state: StateFlow<BillingState> = repository.state

    fun startPurchase(activity: Activity, productId: String) {
        repository.launchBillingFlow(activity, productId)
    }

    fun retry() {
        repository.startConnection()
    }

    fun consumeTransientState() {
        repository.resetTransientState()
    }
}
