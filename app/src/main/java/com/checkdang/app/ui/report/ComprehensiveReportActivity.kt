package com.checkdang.app.ui.report

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.databinding.ActivityComprehensiveReportBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.launch

/**
 * AI 생활습관 리포트 화면. Gemini 가 생성한 식단·수면·운동 종합 리포트(마크다운)를 렌더한다.
 * (혈당·통증은 현재 백엔드 리포트 로직 미포함 — 후속 작업 예정.)
 *
 * 진입: Home 대시보드 'AI 생활습관 리포트' 카드.
 * 로그인 사용자 전용 — 게스트는 진입 시 안내만 표시하고 호출하지 않는다.
 */
class ComprehensiveReportActivity : AppCompatActivity() {

    private val binding by lazy { ActivityComprehensiveReportBinding.inflate(layoutInflater) }
    private val viewModel: ComprehensiveReportViewModel by viewModels()

    private val markwon by lazy {
        Markwon.builder(this)
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()
        binding.btnRetry.setOnClickListener { viewModel.loadReport() }

        // 종합 리포트는 로그인 사용자 전용(백엔드 정책). 게스트는 사전 차단.
        if (SessionHolder.isGuest) {
            showLoginRequired()
            return
        }

        observeState()
        viewModel.loadReport()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: ReportUiState) = when (state) {
        is ReportUiState.Idle,
        is ReportUiState.Loading -> showLoading()
        is ReportUiState.Loaded  -> showReport(state.markdown)
        is ReportUiState.Error   -> showError(state.message)
    }

    private fun showLoading() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutError.visibility   = View.GONE
        binding.scrollContent.visibility = View.GONE
    }

    private fun showReport(markdown: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutError.visibility   = View.GONE
        binding.scrollContent.visibility = View.VISIBLE
        markwon.setMarkdown(binding.tvReport, markdown)
    }

    private fun showError(message: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.scrollContent.visibility = View.GONE
        binding.layoutError.visibility   = View.VISIBLE
        binding.tvError.text = message
        binding.btnRetry.visibility = View.VISIBLE
    }

    /** 게스트: 호출 없이 로그인 유도 안내만 표시(재시도 버튼 숨김). */
    private fun showLoginRequired() {
        binding.layoutLoading.visibility = View.GONE
        binding.scrollContent.visibility = View.GONE
        binding.layoutError.visibility   = View.VISIBLE
        binding.tvError.text = "AI 생활습관 리포트는 로그인 후 이용할 수 있어요."
        binding.btnRetry.visibility = View.GONE
    }
}
