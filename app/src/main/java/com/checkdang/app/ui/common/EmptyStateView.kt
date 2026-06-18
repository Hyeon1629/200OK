package com.checkdang.app.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.checkdang.app.databinding.ViewEmptyStateBinding

/**
 * 재사용 가능한 빈 상태 뷰.
 * setIcon, setTitle, setDescription, setAction으로 내용을 구성한다.
 */
class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewEmptyStateBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        orientation = VERTICAL
    }

    fun setIcon(drawableRes: Int) {
        binding.ivEmptyIcon.setImageResource(drawableRes)
        binding.ivEmptyIcon.visibility = View.VISIBLE
    }

    fun setTitle(title: String) {
        binding.tvEmptyTitle.text = title
    }

    fun setDescription(desc: String) {
        binding.tvEmptyDescription.text = desc
        binding.tvEmptyDescription.visibility = View.VISIBLE
    }

    fun setAction(label: String, onClick: () -> Unit) {
        binding.btnEmptyAction.text = label
        binding.btnEmptyAction.visibility = View.VISIBLE
        binding.btnEmptyAction.setOnClickListener { onClick() }
    }
}
