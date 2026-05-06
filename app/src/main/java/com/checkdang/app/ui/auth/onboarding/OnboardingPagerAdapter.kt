package com.checkdang.app.ui.auth.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> OnboardingNicknameFragment()
        1 -> OnboardingBirthGenderFragment()
        2 -> OnboardingBodyFragment()
        3 -> OnboardingCompleteFragment()
        else -> throw IllegalArgumentException("Unknown page: $position")
    }
}
