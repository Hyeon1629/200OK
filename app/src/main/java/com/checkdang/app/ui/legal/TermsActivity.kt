package com.checkdang.app.ui.legal

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.checkdang.app.R
import com.checkdang.app.databinding.ActivityTermsBinding
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * 이용약관 조회 화면.
 *
 * 모드 분기:
 *  - [MODE_VIEW]      : 단순 조회 (기본값). 메뉴/로그인 화면 캡션에서 진입.
 *  - [MODE_AGREEMENT] : 회원가입 흐름에서 동의 액션 포함. 동의 시 RESULT_OK 반환.
 *
 * 약관 원문은 [R.raw.terms_of_service] (마크다운) 에서 로드하며 Markwon 으로 렌더링.
 * LinkifyPlugin 이 URL/이메일을 자동으로 클릭 가능한 링크로 변환한다.
 */
class TermsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE     = "extra_mode"
        const val MODE_VIEW      = "view"
        const val MODE_AGREEMENT = "agreement"
    }

    private val binding by lazy { ActivityTermsBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()
        loadTerms()
        setupFooter()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadTerms() {
        val markdown = resources.openRawResource(R.raw.terms_of_service)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

        val markwon = Markwon.builder(this)
            .usePlugin(LinkifyPlugin.create())
            .build()
        markwon.setMarkdown(binding.tvTerms, markdown)
    }

    private fun setupFooter() {
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_VIEW
        if (mode == MODE_AGREEMENT) {
            binding.footerAgreement.visibility = View.VISIBLE
            binding.btnAgree.setOnClickListener {
                setResult(RESULT_OK)
                finish()
            }
        } else {
            binding.footerAgreement.visibility = View.GONE
        }
    }
}
