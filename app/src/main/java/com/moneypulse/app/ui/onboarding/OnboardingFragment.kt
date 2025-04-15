package com.moneypulse.app.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.moneypulse.app.R

/**
 * A simple fragment for displaying onboarding content
 */
class OnboardingFragment : Fragment() {

    companion object {
        private const val ARG_PAGE = "ARG_PAGE"
        
        fun newInstance(page: Int): OnboardingFragment {
            val fragment = OnboardingFragment()
            val args = Bundle()
            args.putInt(ARG_PAGE, page)
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_page, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val page = arguments?.getInt(ARG_PAGE) ?: 0
        
        val imageView = view.findViewById<ImageView>(R.id.imageView)
        val titleTextView = view.findViewById<TextView>(R.id.titleTextView)
        val descriptionTextView = view.findViewById<TextView>(R.id.descriptionTextView)
        
        when (page) {
            0 -> {
                // Welcome page
                imageView.setImageResource(R.drawable.onboarding_welcome)
                titleTextView.text = "Welcome to MoneyPulse"
                descriptionTextView.text = 
                    "MoneyPulse helps you track your expenses automatically by analyzing your transaction SMS messages."
            }
            1 -> {
                // SMS Reading
                imageView.setImageResource(R.drawable.onboarding_sms_permission)
                titleTextView.text = "SMS Reading"
                descriptionTextView.text = 
                    "MoneyPulse needs SMS permission to detect your transactions. All data stays on your device and is never shared with anyone."
            }
            2 -> {
                // Supported Banks
                imageView.setImageResource(R.drawable.onboarding_supported_banks)
                titleTextView.text = "Supported Banks"
                descriptionTextView.text = 
                    "MoneyPulse works with most major banks and recognizes various transaction formats. HDFC, ICICI, SBI, Axis, and many more are supported."
            }
            3 -> {
                // Ready to go
                imageView.setImageResource(R.drawable.onboarding_all_set)
                titleTextView.text = "You're All Set!"
                descriptionTextView.text = 
                    "Let's start tracking your finances automatically with MoneyPulse. You can always manage permissions in app settings."
            }
        }
    }
} 