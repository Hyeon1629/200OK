package com.checkdang.app.data.repository

import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.model.PainRecord
import com.checkdang.app.data.remote.PainApiClient

class PainRepository {

    private val token get() = SessionHolder.accessToken
    private val hasRealToken get() = !token.isNullOrEmpty() && token != "mock_access_token"

    suspend fun savePainRecord(record: PainRecord): PainRecord {
        if (hasRealToken) {
            val saved = PainApiClient.savePainRecord(token!!, record)
            MockDataProvider.addPainRecord(saved)
            return saved
        }
        MockDataProvider.addPainRecord(record)
        return record
    }
}
