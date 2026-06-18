package com.checkdang.app.ui.report

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.UserTier
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
 * 로그인 + 프리미엄(PAID) 구독자 전용 — 게스트/비구독자는 진입 시 안내만 표시하고 호출하지 않는다.
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

    private var reportRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()
        binding.btnRetry.setOnClickListener { viewModel.loadReport() }
        observeState()
    }

    /** 탭/액티비티 재방문 시(예: 구독 후 복귀) 접근 권한을 다시 평가한다. */
    override fun onResume() {
        super.onResume()
        applyAccessGate()
    }

    /**
     * 게스트는 로그인 유도, 비구독(FREE)자는 구독 유도 안내만 표시하고 리포트 호출 자체를 막는다.
     * PAID 구독자에게는 최초 1회만 리포트를 로드(중복 호출 방지).
     */
    private fun applyAccessGate() {
        if (SessionHolder.isGuest) {
            showLoginRequired()
            return
        }
        if (SessionHolder.tier != UserTier.PAID) {
            showSubscriptionRequired()
            return
        }
        if (!reportRequested) {
            reportRequested = true
            viewModel.loadReport()
        }
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

    /** 비구독(FREE/GUEST tier): 호출 없이 구독 유도 안내만 표시(재시도 버튼 숨김). */
    private fun showSubscriptionRequired() {
        binding.layoutLoading.visibility = View.GONE
        binding.scrollContent.visibility = View.GONE
        binding.layoutError.visibility   = View.VISIBLE
        binding.tvError.text = "구독으로 삼성헬스 연동을 통해 삼성헬스에서 데이터 입력후 생성됩니다."
        binding.btnRetry.visibility = View.GONE
    }
}
