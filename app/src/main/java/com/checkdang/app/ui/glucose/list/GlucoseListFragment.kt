package com.checkdang.app.ui.glucose.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.checkdang.app.databinding.FragmentGlucoseListBinding
import com.checkdang.app.ui.glucose.GlucoseViewModel
import kotlinx.coroutines.launch

class GlucoseListFragment : Fragment() {

    private var _binding: FragmentGlucoseListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GlucoseViewModel by viewModels(ownerProducer = { requireParentFragment() })
    private val adapter = GlucoseRecordAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGlucoseListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecords.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.records.collect { records ->
                    val items = GlucoseRecordAdapter.buildListItems(records)
                    adapter.submitList(items)

                    binding.layoutEmpty.visibility =
                        if (records.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerRecords.visibility =
                        if (records.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
