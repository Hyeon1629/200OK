package com.checkdang.app.ui.menu.subscription

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.checkdang.app.R

class MockKakaoPayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mock_kakao_pay)

        val amount = intent.getStringExtra(EXTRA_AMOUNT) ?: "₩5,900"
        findViewById<TextView>(R.id.tv_amount).text = amount

        findViewById<TextView>(R.id.btn_close).setOnClickListener { cancel() }
        findViewById<TextView>(R.id.btn_cancel).setOnClickListener { cancel() }

        findViewById<Button>(R.id.btn_pay).setOnClickListener {
            it.isEnabled = false
            (it as Button).text = "처리 중..."
            // 결제 처리 시뮬레이션 딜레이
            it.postDelayed({
                setResult(Activity.RESULT_OK)
                finish()
            }, 1500)
        }
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_AMOUNT = "extra_amount"
    }
}
