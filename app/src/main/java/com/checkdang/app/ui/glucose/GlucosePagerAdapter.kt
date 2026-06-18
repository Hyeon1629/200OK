package com.checkdang.app.ui.glucose

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.checkdang.app.ui.glucose.chart.GlucoseChartFragment
import com.checkdang.app.ui.glucose.list.GlucoseListFragment

class GlucosePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = 2
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> GlucoseChartFragment()
        else -> GlucoseListFragment()
    }
}
