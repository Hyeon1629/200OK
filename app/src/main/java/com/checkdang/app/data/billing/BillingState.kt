package com.checkdang.app.data.billing

import com.android.billingclient.api.ProductDetails

sealed class BillingState {
    data object Idle : BillingState()
    data object Loading : BillingState()
    data class Ready(val products: List<ProductDetails>) : BillingState()
    data object Purchasing : BillingState()
    data class Success(val productId: String) : BillingState()
    data class Error(val message: String, val code: Int? = null) : BillingState()
}
