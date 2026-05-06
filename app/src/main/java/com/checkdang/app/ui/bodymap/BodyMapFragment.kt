package com.checkdang.app.ui.bodymap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.checkdang.app.data.model.BodyView
import com.checkdang.app.databinding.FragmentBodymapBinding
import com.checkdang.app.ui.bodymap.input.PainInputBottomSheet
import kotlinx.coroutines.launch

class BodyMapFragment : Fragment() {

    private var _binding: FragmentBodymapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BodyMapViewModel by viewModels()
    private val adapter = PainRecordAdapter { record ->
        // Tapping existing record → show input for same part
        PainInputBottomSheet.newInstance(record.bodyPart)
            .show(childFragmentManager, "pain_input")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBodymapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupBodyMapView()
        setupToggleGroup()
        observeRecords()
    }

    private fun setupRecyclerView() {
        binding.rvPainRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPainRecords.adapter = adapter
        binding.rvPainRecords.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
    }

    private fun setupBodyMapView() {
        binding.bodyMapView.onPartSelected = { part ->
            binding.tvHint.text = "${part.label} 선택됨"
            binding.btnInputPain.isEnabled = true
            PainInputBottomSheet.newInstance(part)
                .show(childFragmentManager, "pain_input")
        }

        binding.btnInputPain.setOnClickListener {
            val selected = binding.bodyMapView.selectedPart ?: return@setOnClickListener
            PainInputBottomSheet.newInstance(selected)
                .show(childFragmentManager, "pain_input")
        }
    }

    private fun setupToggleGroup() {
        // Default: front selected
        binding.toggleView.check(binding.btnFront.id)
        binding.toggleView.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val bodyView = if (checkedId == binding.btnFront.id) BodyView.FRONT else BodyView.BACK
            binding.bodyMapView.setBodyView(bodyView)
            binding.tvHint.text = "통증 부위를 터치하세요"
            binding.btnInputPain.isEnabled = false
        }
    }

    private fun observeRecords() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.painRecords.collect { records ->
                    adapter.submitList(records)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
