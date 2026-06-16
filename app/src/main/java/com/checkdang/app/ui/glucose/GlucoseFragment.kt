package com.checkdang.app.ui.glucose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.checkdang.app.R
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.databinding.FragmentGlucoseBinding
import com.checkdang.app.ui.glucose.export.GlucosePdfExporter
import com.checkdang.app.ui.glucose.input.GlucoseInputBottomSheet
import com.checkdang.app.ui.glucose.input.InsulinInputBottomSheet
import com.checkdang.app.util.GlucoseAlertNotifier
import com.checkdang.app.util.GlucoseEvaluator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlucoseFragment : Fragment() {

    private var _binding: FragmentGlucoseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GlucoseViewModel by viewModels()

    // API 26–28 의 "기기에 저장" 은 WRITE_EXTERNAL_STORAGE 가 필요. 허가되면 저장을 이어서 진행.
    private val storagePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) exportSave()
        else Toast.makeText(requireContext(), "저장 권한이 필요해요", Toast.LENGTH_SHORT).show()
    }

    // 고/저혈당 로컬 알림 권한(API 33+). 허용/거부 모두 조용히 — 알림은 부가 기능이라 본 흐름을 막지 않음.
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGlucoseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViewPager()
        setupClickListeners()
        observeStats()
        // Samsung 활성 시 매 진입마다 혈당 재조회. 비활성이면 자동으로 빈 리스트 → Mock 만 표시.
        viewModel.refresh()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = GlucosePagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 1

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "그래프"
                else -> "기록"
            }
        }.attach()
    }

    private fun setupClickListeners() {
        binding.btnPdf.setOnClickListener { showExportChooser() }

        binding.fabAdd.setOnClickListener { showAddChooser() }
    }

    /** FAB → 혈당 / 인슐린 입력 선택. */
    private fun showAddChooser() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("무엇을 기록할까요?")
            .setItems(arrayOf("혈당 입력", "인슐린 입력")) { _, which ->
                when (which) {
                    0 -> openGlucoseInput()
                    1 -> openInsulinInput()
                }
            }
            .show()
    }

    private fun openGlucoseInput() {
        // 위험 범위 입력 시 로컬 알림을 띄울 수 있도록, 입력 전에 알림 권한을 확보해둔다.
        ensureNotificationPermission()
        val sheet = GlucoseInputBottomSheet()
        sheet.onRecordSaved = { record ->
            viewModel.pushManualRecord(record)
            // 저/고혈당(DANGER) 이면 본인 기기 로컬 알림(수동 입력 1건 한정).
            GlucoseAlertNotifier.notifyIfNeeded(requireContext(), record)
            val statusColor = GlucoseEvaluator.getColor(record.status, requireContext())
            Snackbar.make(binding.root, "기록이 저장되었어요", Snackbar.LENGTH_SHORT)
                .setBackgroundTint(statusColor)
                .show()
        }
        sheet.show(childFragmentManager, GlucoseInputBottomSheet.TAG)
    }

    private fun openInsulinInput() {
        val sheet = InsulinInputBottomSheet()
        sheet.onRecordSaved = { record ->
            // 혈당 예측 bolus 피처용으로 백엔드에도 전송(로그인 사용자 한정, 게스트는 클라이언트가 스킵).
            viewModel.pushInsulinRecord(record)
            Snackbar.make(binding.root, "인슐린 ${record.unitsLabel}U 기록이 저장되었어요", Snackbar.LENGTH_SHORT)
                .show()
        }
        sheet.show(childFragmentManager, InsulinInputBottomSheet.TAG)
    }

    /** API 33+ 에서 알림 권한이 없으면 1회 요청. 그 이하 버전은 권한 불요. */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !GlucoseAlertNotifier.hasPermission(requireContext())
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.weeklyStats.collect { stats ->
                    binding.tvStatAverage.text = if (stats.average == 0) "--" else "${stats.average}"
                    binding.tvStatMax.text     = if (stats.max == 0) "--" else "${stats.max}"
                    binding.tvStatMin.text     = if (stats.min == 0) "--" else "${stats.min}"

                    // max/min에 GlucoseEvaluator 색상 적용 생략 (단순화)
                }
            }
        }
    }

    // ── 혈당 PDF 내보내기 ────────────────────────────────────────────────────
    private fun showExportChooser() {
        if (viewModel.records.value.isEmpty()) {
            Toast.makeText(requireContext(), "내보낼 혈당 기록이 없어요", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("혈당 리포트 PDF")
            .setItems(arrayOf("공유하기", "기기에 저장")) { _, which ->
                when (which) {
                    0 -> exportShare()
                    1 -> exportSaveWithPermission()
                }
            }
            .show()
    }

    private fun nickname(): String = SessionHolder.currentProfile?.nickname ?: "체크당 사용자"

    private fun exportShare() {
        val ctx = requireContext().applicationContext
        val records = viewModel.records.value
        val insulin = MockDataProvider.insulinRecordsFlow.value
        val nickname = nickname()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    GlucosePdfExporter.buildShareIntent(ctx, records, insulin, nickname)
                }
            }.onSuccess { startActivity(it) }
                .onFailure { Toast.makeText(requireContext(), "PDF 생성에 실패했어요", Toast.LENGTH_SHORT).show() }
        }
    }

    /** API 26–28 은 저장 권한을 먼저 확인/요청한다. API 29+ 는 권한 없이 바로 저장. */
    private fun exportSaveWithPermission() {
        val needsLegacyPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyPerm) storagePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else exportSave()
    }

    private fun exportSave() {
        val ctx = requireContext().applicationContext
        val records = viewModel.records.value
        val insulin = MockDataProvider.insulinRecordsFlow.value
        val nickname = nickname()
        viewLifecycleOwner.lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                GlucosePdfExporter.saveToDownloads(ctx, records, insulin, nickname)
            }
            if (path != null) Snackbar.make(binding.root, "저장됨 · $path", Snackbar.LENGTH_LONG).show()
            else Toast.makeText(requireContext(), "저장에 실패했어요", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
