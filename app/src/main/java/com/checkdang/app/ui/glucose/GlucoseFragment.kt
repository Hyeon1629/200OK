package com.checkdang.app.ui.glucose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.checkdang.app.R
import com.checkdang.app.databinding.FragmentGlucoseBinding
import com.checkdang.app.ui.glucose.input.GlucoseInputBottomSheet
import com.checkdang.app.util.GlucoseEvaluator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class GlucoseFragment : Fragment() {

    private var _binding: FragmentGlucoseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GlucoseViewModel by viewModels()

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
        binding.btnPdf.setOnClickListener {
            Toast.makeText(requireContext(), "PDF 생성을 시작합니다", Toast.LENGTH_SHORT).show()
        }

        binding.fabAdd.setOnClickListener {
            val sheet = GlucoseInputBottomSheet()
            sheet.onRecordSaved = { record ->
                val statusColor = GlucoseEvaluator.getColor(record.status, requireContext())
                Snackbar.make(binding.root, "기록이 저장되었어요", Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(statusColor)
                    .show()
            }
            sheet.show(childFragmentManager, GlucoseInputBottomSheet.TAG)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
