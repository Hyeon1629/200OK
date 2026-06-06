package com.checkdang.app.ui.bodymap.analysis

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.model.BodyPart
import com.checkdang.app.data.model.PainRecord
import com.checkdang.app.data.remote.PainAnalysisApiClient
import com.checkdang.app.databinding.ActivityAiAnalysisBinding
import kotlinx.coroutines.launch

/**
 * 통증 AI 분석 화면.
 *
 * 통증 기록을 백엔드에 저장(→painRecordId)한 뒤 Gemini 분석(원인/조치)을 받아 표시한다
 * ([PainAnalysisApiClient]). 로그인 사용자 전용 — 게스트/비로그인은 안내만 표시한다.
 */
class AIAnalysisActivity : AppCompatActivity() {

    private val binding by lazy { ActivityAiAnalysisBinding.inflate(layoutInflater) }

    companion object {
        const val EXTRA_BODY_PART       = "extra_body_part"
        const val EXTRA_INTENSITY       = "extra_intensity"
        const val EXTRA_QUALITY_TAGS    = "extra_quality_tags"
        const val EXTRA_SITUATION_TAGS  = "extra_situation_tags"
    }

    private lateinit var record: PainRecord

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()

        val partName      = intent.getStringExtra(EXTRA_BODY_PART) ?: BodyPart.LOWER_BACK.name
        val intensity     = intent.getIntExtra(EXTRA_INTENSITY, 3)
        val qualityTags   = intent.getStringArrayExtra(EXTRA_QUALITY_TAGS)?.toList() ?: emptyList()
        val situationTags = intent.getStringArrayExtra(EXTRA_SITUATION_TAGS)?.toList() ?: emptyList()

        record = PainRecord(
            bodyPart      = BodyPart.valueOf(partName),
            intensity     = intensity,
            qualityTags   = qualityTags,
            situationTags = situationTags,
        )

        // 바디맵 기록 목록은 현재 로컬 소스(MockDataProvider)를 읽으므로 로컬에도 보관한다.
        // (백엔드에는 PainAnalysisApiClient.analyze 의 1단계에서 별도 저장됨)
        MockDataProvider.addPainRecord(record)

        bindPainInfo(record)
        binding.btnConfirm.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { analyze() }
        binding.btnErrorClose.setOnClickListener { finish() }

        // 게스트/비로그인은 AI 기능 미지원(다른 AI 화면과 동일 정책).
        if (SessionHolder.userId == null) {
            showError("AI 통증 분석은 로그인 후 이용할 수 있어요.", retryable = false)
            return
        }
        analyze()
    }

    private fun analyze() {
        showLoading()
        lifecycleScope.launch {
            runCatching { PainAnalysisApiClient.analyze(record) }
                .onSuccess { showResult(it.aiCause, it.aiFirstAid) }
                .onFailure {
                    showError(
                        it.message ?: "분석 중 문제가 발생했어요. 잠시 후 다시 시도해주세요.",
                        retryable = true,
                    )
                }
        }
    }

    private fun bindPainInfo(record: PainRecord) {
        binding.tvResultPart.text      = record.bodyPart.label
        binding.tvResultTypes.text     = record.tagSummary
        binding.tvResultIntensity.text = record.intensity.toString()
    }

    private fun showLoading() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutResult.visibility  = View.GONE
        binding.layoutError.visibility   = View.GONE
    }

    private fun showResult(aiCause: String, aiFirstAid: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutError.visibility   = View.GONE
        binding.layoutResult.visibility  = View.VISIBLE

        binding.tvAiCause.text    = aiCause
        binding.tvAiFirstAid.text = aiFirstAid
    }

    private fun showError(message: String, retryable: Boolean) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutResult.visibility  = View.GONE
        binding.layoutError.visibility   = View.VISIBLE

        binding.tvError.text = message
        binding.btnRetry.visibility = if (retryable) View.VISIBLE else View.GONE
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI 바디맵 분석"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
