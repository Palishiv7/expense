package com.moneypulse.app.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Adapter for the onboarding ViewPager
 */
class OnboardingPagerAdapter(
    activity: FragmentActivity
) : FragmentStateAdapter(activity) {

    companion object {
        const val PAGE_COUNT = 4
    }

    override fun getItemCount(): Int = PAGE_COUNT

    override fun createFragment(position: Int): Fragment {
        return OnboardingFragment.newInstance(position)
    }
} 