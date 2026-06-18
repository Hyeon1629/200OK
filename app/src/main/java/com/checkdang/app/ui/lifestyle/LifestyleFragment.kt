package com.checkdang.app.ui.lifestyle

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary
import com.checkdang.app.databinding.FragmentLifestyleBinding
import com.checkdang.app.ui.lifestyle.exercise.ExerciseDetailActivity
import com.checkdang.app.ui.lifestyle.meal.MealDetailActivity
import com.checkdang.app.ui.lifestyle.sleep.SleepDetailActivity
import com.checkdang.app.ui.menu.subscription.SubscriptionActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LifestyleFragment : Fragment() {

    private var _binding: FragmentLifestyleBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LifestyleViewModel by viewModels()

    private val HEALTH_PERMISSIONS = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        // 아래 두 줄이 추가되었습니다!
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    // Health Connect 권한 요청 런처 — Fragment 생성 시점에 등록해야 함
    private val requestPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.isEmpty()) {
            Toast.makeText(requireContext(), "권한이 거부되어 삼성 헬스에 연동할 수 없어요.", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        viewModel.connectAndSync()
        val msg = if (granted.containsAll(HEALTH_PERMISSIONS)) "삼성 헬스 연동 완료!"
                  else "일부 권한만 허가됐어요. 가능한 데이터를 가져올게요."
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLifestyleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupHeader()
        setupClickListeners()
        observeData()
        applyTierGate()
    }

    /**
     * 화면이 보일 때마다(최초 진입 + 탭 재방문, 예: 구독 화면에서 결제 후 복귀) 구독 상태를
     * 재확인한다. PAID 가 아니면 헬스 연동 UI 전체를 숨기고 안내 텍스트만 노출, PAID 면
     * 헬스 연동 UI를 보여주고 Samsung silent 연동을 시도한다.
     */
    override fun onResume() {
        super.onResume()
        applyTierGate()
        trySamsungSilent()
    }

    private fun applyTierGate() {
        val unlocked = SessionHolder.tier == UserTier.PAID
        binding.groupHealthContent.visibility = if (unlocked) View.VISIBLE else View.GONE
        binding.tvSubscribeRequired.visibility = if (unlocked) View.GONE else View.VISIBLE
    }

    /**
     * 진입/재방문 시 Samsung Health Data SDK 우선 시도(권한 다이얼로그는 띄우지 않음).
     * 이미 허가된 경우에만 Samsung 소스로 전환된다. 실패해도 기존 Health Connect 흐름은
     * 사용자가 새로고침 버튼으로 트리거 가능.
     *
     * 프리미엄(PAID) 사용자만 시도 — FREE/GUEST 는 헬스 연동 대상이 아니므로 Mock 데이터 유지.
     */
    private fun trySamsungSilent() {
        if (SessionHolder.tier != UserTier.PAID) return
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.trySamsungHealthFirst(requireActivity(), requestPermissionIfNeeded = false)
        }
    }

    private fun setupHeader() {
        val sdf       = SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN)
        val dayOfWeek = SimpleDateFormat("E요일", Locale.KOREAN)
        val today     = Calendar.getInstance().time
        binding.tvDate.text = "오늘 ${sdf.format(today)} ${dayOfWeek.format(today)}"
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.exercise.collect  { setupExerciseCard(it) } }
                launch { viewModel.meal.collect      { setupMealCard(it) } }
                launch { viewModel.sleep.collect     { setupSleepCard(it) } }
                launch { viewModel.isSyncing.collect { binding.btnRefresh.isEnabled = !it } }
            }
        }
    }

    private fun setupExerciseCard(s: ExerciseSummary?) {
        if (s == null) {
            binding.donutExercise.progress  = 0f
            binding.tvExercisePercent.text  = "-"
            binding.tvExerciseSub.text      = "기록 없음"
            binding.tvExerciseCalories.text = "-"
            binding.tvExerciseSessions.text = "-"
            return
        }
        val progress = s.totalMinutes.toFloat() / s.goalMinutes
        binding.donutExercise.progress  = progress
        binding.tvExercisePercent.text  = "${(progress * 100).toInt()}%"
        binding.tvExerciseSub.text      = "${s.totalMinutes} / ${s.goalMinutes}분"
        binding.tvExerciseCalories.text = "${s.totalCalories} kcal 소모"
        binding.tvExerciseSessions.text = "운동 ${s.sessions.size}회"
    }

    private fun setupMealCard(s: MealSummary?) {
        if (s == null) {
            binding.tvMealKcal.text     = "-"
            binding.tvMealGoal.text     = "기록 없음"
            binding.tvCarbsLabel.text   = "탄수화물 -"
            binding.tvProteinLabel.text = "단백질 -"
            binding.tvFatLabel.text     = "지방 -"
            setWeight(binding.viewCarbs,   1f / 3)
            setWeight(binding.viewProtein, 1f / 3)
            setWeight(binding.viewFat,     1f / 3)
            return
        }
        val pct = s.totalKcal * 100 / s.goalKcal
        binding.tvMealKcal.text     = "${String.format(Locale.getDefault(), "%,d", s.totalKcal)} kcal"
        binding.tvMealGoal.text     = "/ ${String.format(Locale.getDefault(), "%,d", s.goalKcal)} kcal (${pct}%)"
        binding.tvCarbsLabel.text   = "탄수화물 ${s.carbsG}g"
        binding.tvProteinLabel.text = "단백질 ${s.proteinG}g"
        binding.tvFatLabel.text     = "지방 ${s.fatG}g"
        val total = (s.carbsG + s.proteinG + s.fatG).toFloat()
        setWeight(binding.viewCarbs,   s.carbsG   / total)
        setWeight(binding.viewProtein, s.proteinG / total)
        setWeight(binding.viewFat,     s.fatG     / total)
    }

    private fun setupSleepCard(s: SleepSummary?) {
        if (s == null) {
            binding.tvSleepTotal.text      = "-"
            binding.tvSleepEfficiency.text = "-"
            binding.tvSleepBedtime.text    = "기록 없음"
            setWeight(binding.viewSleepDeep,  1f / 3)
            setWeight(binding.viewSleepLight, 1f / 3)
            setWeight(binding.viewSleepRem,   1f / 3)
            return
        }
        val hours   = s.totalHours.toInt()
        val minutes = ((s.totalHours - hours) * 60).toInt()
        binding.tvSleepTotal.text      = "${hours}시간 ${minutes}분"
        binding.tvSleepEfficiency.text = "효율 ${s.efficiency}%"
        binding.tvSleepBedtime.text    = "취침 ${s.bedtime} · 기상 ${s.wakeTime}"
        val total = s.deepHours + s.lightHours + s.remHours
        setWeight(binding.viewSleepDeep,  s.deepHours  / total)
        setWeight(binding.viewSleepLight, s.lightHours / total)
        setWeight(binding.viewSleepRem,   s.remHours   / total)
    }

    private fun setWeight(view: View, weight: Float) {
        (view.layoutParams as android.widget.LinearLayout.LayoutParams).weight = weight
        view.requestLayout()
    }

    private fun setupClickListeners() {
        binding.btnRefresh.setOnClickListener { onRefreshClicked() }
        binding.cardExercise.setOnClickListener {
            startActivity(Intent(requireContext(), ExerciseDetailActivity::class.java))
        }
        binding.cardMeal.setOnClickListener {
            startActivity(Intent(requireContext(), MealDetailActivity::class.java))
        }
        binding.cardSleep.setOnClickListener {
            startActivity(Intent(requireContext(), SleepDetailActivity::class.java))
        }
    }

    /**
     * 새로고침 버튼 플로우 — Samsung 우선, Health Connect 차선.
     *
     * 0. 프리미엄(PAID) 구독자가 아니면 연동 자체를 차단하고 구독 화면으로 안내
     * 1. Samsung Health Data SDK 시도 (필요 시 권한 다이얼로그 노출)
     *    → 성공하면 그대로 종료
     * 2. 실패/미설치/미지원 시 기존 Health Connect 흐름으로 fallback
     */
    private fun onRefreshClicked() {
        if (SessionHolder.tier != UserTier.PAID) {
            Toast.makeText(requireContext(), "삼성 헬스 연동은 프리미엄 구독자만 이용할 수 있어요.", Toast.LENGTH_LONG).show()
            startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val activated = viewModel.trySamsungHealthFirst(
                activity = requireActivity(),
                requestPermissionIfNeeded = true
            )
            if (activated) {
                Toast.makeText(requireContext(), "삼성 헬스(직접 연동)에서 데이터를 가져왔어요.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            startHealthConnectSync()
        }
    }

    /**
     * Health Connect fallback 플로우 (기존 로직, 내용 무변경):
     * 1. Health Connect 설치 여부 확인
     * 2. 권한 보유 여부 확인
     * 3. 권한 없으면 요청 → 허가 시 connectAndSync() 자동 호출
     * 4. 권한 있으면 즉시 connectAndSync()
     */
    private fun startHealthConnectSync() {
        // 뒤에 "com.google.android.apps.healthdata" 를 추가합니다.
        val status = HealthConnectClient.getSdkStatus(requireContext(), "com.google.android.apps.healthdata")
        when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                Toast.makeText(
                    requireContext(),
                    "Samsung Health 앱이 설치되어 있지 않아요",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                Toast.makeText(
                    requireContext(),
                    "Samsung Health를 최신 버전으로 업데이트해 주세요",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        val client = HealthConnectClient.getOrCreate(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(HEALTH_PERMISSIONS)) {
                viewModel.connectAndSync()
                Toast.makeText(requireContext(), "삼성 헬스에서 데이터를 가져오는 중...", Toast.LENGTH_SHORT).show()
            } else {
                requestPermissions.launch(HEALTH_PERMISSIONS)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
