package com.checkdang.app.ui.lifestyle

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.checkdang.app.databinding.FragmentLifestyleBinding
import com.checkdang.app.ui.lifestyle.exercise.ExerciseDetailActivity
import com.checkdang.app.ui.lifestyle.meal.MealDetailActivity
import com.checkdang.app.ui.lifestyle.sleep.SleepDetailActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LifestyleFragment : Fragment() {

    private var _binding: FragmentLifestyleBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LifestyleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLifestyleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupHeader()
        setupExerciseCard()
        setupMealCard()
        setupSleepCard()
        setupClickListeners()
    }

    private fun setupHeader() {
        val sdf       = SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN)
        val dayOfWeek = SimpleDateFormat("E요일", Locale.KOREAN)
        val today     = Calendar.getInstance().time
        binding.tvDate.text = "오늘 ${sdf.format(today)} ${dayOfWeek.format(today)}"
    }

    private fun setupExerciseCard() {
        val s = viewModel.exercise
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

    private fun setupMealCard() {
        val s = viewModel.meal
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

    private fun setupSleepCard() {
        val s = viewModel.sleep
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
        binding.btnRefresh.setOnClickListener {
            Toast.makeText(requireContext(), "동기화되었어요", Toast.LENGTH_SHORT).show()
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
