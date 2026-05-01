package com.checkdang.app.ui.family

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.checkdang.app.R
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.data.model.FamilyMember
import com.checkdang.app.databinding.ActivityFamilyBinding
import com.checkdang.app.util.GlucoseStatus
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class FamilyActivity : AppCompatActivity() {

    private val binding by lazy { ActivityFamilyBinding.inflate(layoutInflater) }
    private lateinit var adapter: FamilyMemberAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()

        if (SessionHolder.tier != UserTier.PAID) {
            binding.layoutNotPaid.visibility = View.VISIBLE
            binding.layoutPaid.visibility    = View.GONE
            return
        }

        showPaidLayout()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "가족 공유"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun showPaidLayout() {
        binding.layoutNotPaid.visibility = View.GONE
        binding.layoutPaid.visibility    = View.VISIBLE

        adapter = FamilyMemberAdapter(
            onClick = { member ->
                Toast.makeText(
                    this, "${member.name}님의 상세 보기 (Phase 7+)", Toast.LENGTH_SHORT
                ).show()
            },
            onLongClick = { member -> showRemoveDialog(member) }
        )
        binding.rvFamilyMembers.layoutManager = LinearLayoutManager(this)
        binding.rvFamilyMembers.adapter = adapter

        binding.btnAddFamily.setOnClickListener {
            val dialog = AddFamilyDialog()
            dialog.onFamilyAdded = {
                Snackbar.make(binding.root, "가족이 추가되었어요!", Snackbar.LENGTH_SHORT).show()
            }
            dialog.show(supportFragmentManager, "add_family")
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MockDataProvider.familyFlow.collect { members ->
                    adapter.submitList(members)
                    binding.tvFamilyCount.text = "함께 관리 중인 가족 ${members.size}명"
                    updateDangerBanner(members)
                }
            }
        }
    }

    private fun updateDangerBanner(members: List<FamilyMember>) {
        val dangerMember = members.firstOrNull { it.statusBadge == GlucoseStatus.DANGER }
        if (dangerMember != null) {
            val wasGone = binding.bannerDanger.visibility == View.GONE
            binding.bannerDanger.visibility = View.VISIBLE
            binding.tvBannerMessage.text =
                "⚠ ${dangerMember.name}님의 혈당이 위험 수준이에요. 안부 전화를 권장해요"
            binding.btnCall.setOnClickListener {
                Toast.makeText(this, "전화 앱이 열립니다 (모의)", Toast.LENGTH_SHORT).show()
            }
            if (wasGone) {
                val anim = AnimationUtils.loadAnimation(this, R.anim.slide_down)
                binding.bannerDanger.startAnimation(anim)
            }
        } else {
            binding.bannerDanger.visibility = View.GONE
        }
    }

    private fun showRemoveDialog(member: FamilyMember) {
        MaterialAlertDialogBuilder(this)
            .setTitle("가족 제거")
            .setMessage("${member.name}님을 가족 목록에서 제거할까요?")
            .setPositiveButton("제거") { _, _ ->
                MockDataProvider.removeFamilyMember(member.id)
                Snackbar.make(binding.root, "${member.name}님이 제거되었어요", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
