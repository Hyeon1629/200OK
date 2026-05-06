package com.checkdang.app.ui.family

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.FamilyMember
import com.checkdang.app.databinding.DialogAddFamilyBinding
import com.checkdang.app.util.GlucoseStatus
import com.checkdang.app.util.MealTiming
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import java.util.UUID

class AddFamilyDialog : BottomSheetDialogFragment() {

    private var _binding: DialogAddFamilyBinding? = null
    private val binding get() = _binding!!

    var onFamilyAdded: (() -> Unit)? = null

    companion object {
        private const val MY_INVITE_CODE = "ABCD12"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddFamilyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyTopRoundedCorners()

        binding.tvMyCode.text = "내 초대 코드: $MY_INVITE_CODE"
        binding.btnCopyCode.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("invite_code", MY_INVITE_CODE))
            Toast.makeText(requireContext(), "초대 코드가 복사되었어요", Toast.LENGTH_SHORT).show()
        }

        binding.btnInvite.setOnClickListener {
            val code = binding.etInviteCode.text?.toString()?.trim() ?: ""
            if (code.length != 6) {
                binding.etInviteCode.error = "6자리 코드를 입력해 주세요"
                return@setOnClickListener
            }
            addDummyFamilyMember()
            onFamilyAdded?.invoke()
            dismiss()
        }
    }

    private fun addDummyFamilyMember() {
        val dummies = listOf(
            FamilyMember("f_new_1", "박지수",  "딸",   3, 88,  MealTiming.FASTING,      "방금 전", 1, GlucoseStatus.NORMAL),
            FamilyMember("f_new_2", "최철수",  "형",   4, 178, MealTiming.POST_MEAL_2H, "1시간 전", 2, GlucoseStatus.DANGER),
            FamilyMember("f_new_3", "김소연",  "여동생", 1, 115, MealTiming.POST_MEAL_1H, "2시간 전", 1, GlucoseStatus.WARNING),
        )
        val candidate = dummies.firstOrNull { m ->
            MockDataProvider.getFamilyMembers().none { it.name == m.name }
        } ?: FamilyMember(
            id               = UUID.randomUUID().toString(),
            name             = "새 가족",
            relation         = "지인",
            avatarColorIndex = 5,
            latestGlucose    = 100,
            latestTiming     = MealTiming.FASTING,
            latestMeasuredAt = "방금 전",
            todayCount       = 1,
            statusBadge      = GlucoseStatus.NORMAL
        )
        MockDataProvider.addFamilyMember(candidate)
    }

    private fun applyTopRoundedCorners() {
        val cornerRadius = resources.getDimension(com.checkdang.app.R.dimen.bottom_sheet_corner_radius)
        val white = androidx.core.content.ContextCompat.getColor(
            requireContext(), com.checkdang.app.R.color.white
        )
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog ?: return
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val shape = ShapeAppearanceModel.builder()
            .setTopLeftCornerSize(cornerRadius)
            .setTopRightCornerSize(cornerRadius)
            .build()
        sheet.background = MaterialShapeDrawable(shape).apply { setTint(white) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
