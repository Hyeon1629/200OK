package com.checkdang.app.ui.menu

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.checkdang.app.R
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.SocialProvider
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.databinding.FragmentMenuBinding
import com.checkdang.app.databinding.ItemMenuRowBinding
import com.checkdang.app.data.remote.AuthApiClient
import com.checkdang.app.ui.auth.login.LoginActivity
import com.checkdang.app.ui.family.FamilyActivity
import com.checkdang.app.ui.menu.subscription.SubscriptionActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MenuFragment : Fragment() {

    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenuRows()
        observeTier()

        binding.cardTier.setOnLongClickListener {
            SessionHolder.toggleTierForDemo()
            true
        }

        binding.btnLoginFromMenu.setOnClickListener {
            SessionHolder.reset()
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }

    private fun observeTier() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionHolder.tierFlow.collect { tier ->
                    refreshProfileHeader()
                    refreshTierCard(tier)
                    refreshLockedItems(tier)
                }
            }
        }
    }

    private fun refreshProfileHeader() {
        when {
            SessionHolder.isGuest -> {
                binding.tvAvatarInitial.text = "?"
                binding.tvAvatarInitial.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.text_secondary)
                )
                binding.tvProfileName.text        = "비회원으로 이용 중"
                binding.tvProfileEmail.visibility = View.GONE
                binding.ivProfileArrow.visibility = View.GONE
                binding.btnLoginFromMenu.visibility = View.VISIBLE
            }
            SessionHolder.isLoggedIn -> {
                val profile  = SessionHolder.currentProfile
                val initial  = profile?.nickname?.firstOrNull()?.toString() ?: "?"
                binding.tvAvatarInitial.text = initial
                binding.tvAvatarInitial.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.brand_green)
                )
                binding.tvProfileName.text = profile?.nickname ?: "이름 없음"
                val providerLabel = when (SessionHolder.authProvider) {
                    SocialProvider.GOOGLE -> "Google 계정 연결됨"
                    SocialProvider.KAKAO  -> "카카오 계정 연결됨"
                    SocialProvider.NONE   -> ""
                }
                binding.tvProfileEmail.text       = providerLabel
                binding.tvProfileEmail.visibility = if (providerLabel.isNotEmpty()) View.VISIBLE else View.GONE
                binding.ivProfileArrow.visibility = View.VISIBLE
                binding.btnLoginFromMenu.visibility = View.GONE
            }
            else -> {
                binding.tvAvatarInitial.text = "?"
                binding.tvAvatarInitial.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.text_secondary)
                )
                binding.tvProfileName.text        = "비회원으로 이용 중"
                binding.tvProfileEmail.visibility = View.GONE
                binding.ivProfileArrow.visibility = View.GONE
                binding.btnLoginFromMenu.visibility = View.VISIBLE
            }
        }

        // 연결된 계정 행 라벨 동적 갱신
        val connectedLabel = when (SessionHolder.authProvider) {
            SocialProvider.GOOGLE -> "Google"
            SocialProvider.KAKAO  -> "카카오"
            SocialProvider.NONE   -> "없음"
        }
        binding.menuConnectedAccount.tvMenuSub.visibility = View.VISIBLE
        binding.menuConnectedAccount.tvMenuSub.text       = connectedLabel
    }

    private fun refreshTierCard(tier: UserTier) {
        val (label, textColor, bgColor) = when (tier) {
            UserTier.PAID  -> Triple("PAID ✓",  R.color.white,          R.color.brand_green)
            UserTier.FREE  -> Triple("FREE",     R.color.text_secondary, R.color.background_secondary)
            UserTier.GUEST -> Triple("GUEST",    R.color.text_secondary, R.color.background_secondary)
        }
        binding.tvTierBadge.text = label
        binding.tvTierBadge.setTextColor(ContextCompat.getColor(requireContext(), textColor))
        binding.tvTierBadge.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), bgColor))
        binding.btnSubscriptionCta.text = if (tier == UserTier.PAID) "구독 관리" else "프리미엄 시작하기"
        binding.btnSubscriptionCta.setOnClickListener {
            startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
        }
    }

    private fun refreshLockedItems(tier: UserTier) {
        val isPaid = tier == UserTier.PAID
        applyLock(binding.menuBackup, locked = !isPaid)
        applyLock(binding.menuFamily, locked = !isPaid)
    }

    private fun applyLock(row: ItemMenuRowBinding, locked: Boolean) {
        row.ivLock.visibility = if (locked) View.VISIBLE else View.GONE
        row.ivArrow.alpha     = if (locked) 0.2f else 0.3f
        row.tvMenuTitle.alpha = if (locked) 0.5f else 1.0f
        row.ivMenuIcon.alpha  = if (locked) 0.4f else 1.0f
    }

    private fun setupMenuRows() {
        // 섹션 1: 내 정보
        configRow(binding.menuProfile,      R.drawable.ic_person, "환자 프로필 관리")
        configRow(binding.menuNotification, R.drawable.ic_bell,   "알림 설정")
        configRow(binding.menuUnit,         R.drawable.ic_unit,   "단위 설정 (mg/dL)")

        // 섹션 2: 데이터
        configRow(binding.menuExport, R.drawable.ic_export, "데이터 내보내기 (PDF/CSV)")
        configRow(binding.menuBackup, R.drawable.ic_backup, "데이터 백업")
        configRow(binding.menuFamily, R.drawable.ic_group,  "가족 공유")

        // 섹션 3: 고객센터
        configRow(binding.menuFaq,     R.drawable.ic_help,     "자주 묻는 질문")
        configRow(binding.menuSupport, R.drawable.ic_chat,     "1:1 문의")
        configRow(binding.menuTerms,   R.drawable.ic_document, "이용약관")
        configRow(binding.menuPrivacy, R.drawable.ic_shield,   "개인정보처리방침")
        configRow(binding.menuVersion, R.drawable.ic_info,     "앱 버전",
            subText = "1.0.0", showArrow = false)

        // 섹션 4: 계정
        configRow(binding.menuConnectedAccount, R.drawable.ic_person, "연결된 계정",
            showArrow = false)
        configRow(binding.menuLogout,   R.drawable.ic_logout, "로그아웃")
        configRow(binding.menuWithdraw, R.drawable.ic_logout, "회원 탈퇴",
            titleColor = R.color.text_secondary, titleSize = 13f)

        // 클릭 리스너
        binding.menuFamily.root.setOnClickListener {
            if (SessionHolder.tier == UserTier.PAID) {
                startActivity(Intent(requireContext(), FamilyActivity::class.java))
            } else {
                showPremiumDialog()
            }
        }
        binding.menuBackup.root.setOnClickListener {
            if (SessionHolder.tier != UserTier.PAID) showPremiumDialog()
        }
        binding.menuLogout.root.setOnClickListener   { showLogoutDialog() }
        binding.menuWithdraw.root.setOnClickListener { showWithdrawDialog() }

        binding.menuProfile.root.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_profile_edit)
        }

        listOf(
            binding.menuNotification, binding.menuUnit,
            binding.menuExport, binding.menuFaq, binding.menuSupport,
            binding.menuTerms, binding.menuPrivacy
        ).forEach { row ->
            row.root.setOnClickListener {
                Toast.makeText(requireContext(), "${row.tvMenuTitle.text} (준비 중)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configRow(
        row: ItemMenuRowBinding,
        icon: Int,
        title: String,
        subText: String? = null,
        showArrow: Boolean = true,
        titleColor: Int = R.color.text_primary,
        titleSize: Float? = null
    ) {
        row.ivMenuIcon.setImageResource(icon)
        row.tvMenuTitle.text = title
        row.tvMenuTitle.setTextColor(ContextCompat.getColor(requireContext(), titleColor))
        titleSize?.let { row.tvMenuTitle.textSize = it }
        if (subText != null) {
            row.tvMenuSub.visibility = View.VISIBLE
            row.tvMenuSub.text = subText
        }
        row.ivArrow.visibility = if (showArrow) View.VISIBLE else View.GONE
    }

    private fun showPremiumDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("프리미엄 전용 기능이에요")
            .setMessage("이 기능은 프리미엄 구독 회원만 사용할 수 있어요.\n구독하고 가족의 건강을 함께 관리해 보세요!")
            .setPositiveButton("구독하러 가기") { _, _ ->
                startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("로그아웃")
            .setMessage("정말 로그아웃 하시겠어요?")
            .setPositiveButton("로그아웃") { _, _ ->
                performLogout()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performLogout() {
        val token = SessionHolder.refreshToken

        viewLifecycleOwner.lifecycleScope.launch {
            // 1. refreshToken이 있으면 백엔드 로그아웃 호출
            if (!token.isNullOrEmpty() && token != "mock_refresh_token") {
                runCatching { AuthApiClient.logout(token) }
                // 성공/실패 무관하게 로컬 로그아웃 진행
            }

            // 2. 로컬 세션 삭제
            SessionHolder.reset()

            // 3. 로그인 화면으로 이동
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }

    private fun showWithdrawDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("회원 탈퇴")
            .setMessage("탈퇴하면 모든 데이터가 삭제돼요.\n정말 탈퇴하시겠어요?")
            .setPositiveButton("탈퇴") { _, _ ->
                SessionHolder.reset()
                startActivity(
                    Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
