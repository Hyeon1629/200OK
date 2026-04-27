package com.checkdang.app.ui.bodymap.analysis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.checkdang.app.R
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.BodyPart
import com.checkdang.app.data.model.Correlation
import com.checkdang.app.data.model.CorrelationLevel
import com.checkdang.app.data.model.PainRecord
import com.checkdang.app.data.model.PainType
import com.checkdang.app.databinding.ActivityAiAnalysisBinding
import com.checkdang.app.databinding.ItemCorrelationBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AIAnalysisActivity : AppCompatActivity() {

    private val binding by lazy { ActivityAiAnalysisBinding.inflate(layoutInflater) }

    companion object {
        const val EXTRA_BODY_PART   = "extra_body_part"
        const val EXTRA_INTENSITY   = "extra_intensity"
        const val EXTRA_PAIN_TYPES  = "extra_pain_types"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()

        val partName   = intent.getStringExtra(EXTRA_BODY_PART) ?: BodyPart.LOWER_BACK.name
        val intensity  = intent.getIntExtra(EXTRA_INTENSITY, 3)
        val typeNames  = intent.getStringArrayExtra(EXTRA_PAIN_TYPES) ?: emptyArray()
        val painTypes  = typeNames.mapNotNull { runCatching { PainType.valueOf(it) }.getOrNull() }
            .ifEmpty { listOf(PainType.DULL) }

        val record = PainRecord(
            bodyPart   = BodyPart.valueOf(partName),
            intensity  = intensity,
            painTypes  = painTypes,
        )

        // Save to mock storage
        MockDataProvider.addPainRecord(record)

        // Show loading then reveal result after 1.5s
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutResult.visibility  = View.GONE

        lifecycleScope.launch {
            delay(1500)
            val result = MockDataProvider.analyzePainMock(record)
            showResult(result.painRecord, result.summary, result.correlations, result.recommendation)
        }
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

    private fun showResult(
        record: PainRecord,
        summary: String,
        correlations: List<Correlation>,
        recommendation: String
    ) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutResult.visibility  = View.VISIBLE

        binding.tvResultPart.text      = record.bodyPart.label
        binding.tvResultTypes.text     = record.painTypes.joinToString(" · ") { it.label }
        binding.tvResultIntensity.text = record.intensity.toString()
        binding.tvSummary.text         = summary
        binding.tvRecommendation.text  = recommendation

        val adapter = CorrelationAdapter()
        binding.rvCorrelations.layoutManager = LinearLayoutManager(this)
        binding.rvCorrelations.adapter = adapter
        adapter.submitList(correlations)

        binding.btnPdf.setOnClickListener {
            Toast.makeText(this, "PDF 저장 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }
        binding.btnConfirm.setOnClickListener { finish() }
    }

    // ── Inline CorrelationAdapter ────────────────────────────────────────────

    inner class CorrelationAdapter : ListAdapter<Correlation, CorrelationAdapter.VH>(
        object : DiffUtil.ItemCallback<Correlation>() {
            override fun areItemsTheSame(old: Correlation, new: Correlation) = old.factor == new.factor
            override fun areContentsTheSame(old: Correlation, new: Correlation) = old == new
        }
    ) {

        inner class VH(private val b: ItemCorrelationBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: Correlation) {
                b.tvFactor.text      = item.factor
                b.tvLevel.text       = item.level.label
                b.tvDescription.text = item.description

                val (textColor, bgColor) = when (item.level) {
                    CorrelationLevel.HIGH   -> R.color.status_danger  to R.color.status_danger_bg
                    CorrelationLevel.MEDIUM -> R.color.status_warning to R.color.status_warning_bg
                    CorrelationLevel.LOW    -> R.color.status_normal  to R.color.status_normal_bg
                }
                b.tvLevel.setTextColor(ContextCompat.getColor(this@AIAnalysisActivity, textColor))
                b.tvLevel.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this@AIAnalysisActivity, bgColor)
                    )
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemCorrelationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
    }
}
