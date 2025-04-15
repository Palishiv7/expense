package com.moneypulse.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.moneypulse.app.R
import com.moneypulse.app.ui.MainActivity
import com.moneypulse.app.util.PreferenceHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Onboarding activity that handles the onboarding flow and permission requests
 */
@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    // Request codes for permissions
    private val SMS_PERMISSION_REQUEST_CODE = 100
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    
    // UI elements
    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: Button
    private lateinit var btnGetStarted: Button
    private lateinit var indicators: Array<View>
    
    // Current page of the ViewPager
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // If onboarding has been completed before, go directly to MainActivity
        if (preferenceHelper.hasCompletedOnboarding()) {
            continueToMainActivity()
            return
        }
        
        // Set the onboarding layout
        setContentView(R.layout.activity_onboarding)
        
        // Initialize UI elements
        viewPager = findViewById(R.id.viewPager)
        btnNext = findViewById(R.id.btn_next)
        btnGetStarted = findViewById(R.id.btn_get_started)
        
        // Set up page indicators
        indicators = arrayOf(
            findViewById(R.id.indicator1),
            findViewById(R.id.indicator2),
            findViewById(R.id.indicator3),
            findViewById(R.id.indicator4)
        )
        
        // Set up ViewPager with adapter
        viewPager.adapter = OnboardingPagerAdapter(this)
        
        // Update indicators when page changes
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateIndicators()
                updateButtons()
            }
        })
        
        // Set up button click listeners
        btnNext.setOnClickListener {
            if (currentPage < OnboardingPagerAdapter.PAGE_COUNT - 1) {
                viewPager.currentItem = currentPage + 1
            }
        }
        
        btnGetStarted.setOnClickListener {
            completeOnboarding()
        }
        
        // Initialize UI state
        updateIndicators()
        updateButtons()
    }
    
    /**
     * Update the page indicator dots
     */
    private fun updateIndicators() {
        for (i in indicators.indices) {
            indicators[i].isSelected = i == currentPage
            
            // Update indicator appearance based on selection state
            if (i == currentPage) {
                indicators[i].setBackgroundResource(R.drawable.indicator_active)
            } else {
                indicators[i].setBackgroundResource(R.drawable.indicator_inactive)
            }
        }
    }
    
    /**
     * Update button visibility based on the current page
     */
    private fun updateButtons() {
        if (currentPage == OnboardingPagerAdapter.PAGE_COUNT - 1) {
            // On the last page, show the Get Started button
            btnNext.visibility = View.GONE
            btnGetStarted.visibility = View.VISIBLE
        } else {
            // On other pages, show the Next button
            btnNext.visibility = View.VISIBLE
            btnGetStarted.visibility = View.GONE
        }
    }
    
    private fun completeOnboarding() {
        // Mark onboarding as completed
        preferenceHelper.setOnboardingCompleted(true)
        
        // Request necessary permissions
        requestSmsPermission()
    }
    
    private fun requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted
            preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_GRANTED)
            checkAndRequestNotificationPermission()
        } else {
            // Request permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS),
                SMS_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            SMS_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // SMS permission granted
                    preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_GRANTED)
                    Toast.makeText(this, "SMS permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    // SMS permission denied
                    preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_DENIED)
                    preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
                    Toast.makeText(this, "SMS permission denied - app will work in manual mode", Toast.LENGTH_LONG).show()
                }
                checkAndRequestNotificationPermission()
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                // Regardless of notification permission result, continue to MainActivity
                continueToMainActivity()
            }
        }
    }
    
    private fun checkAndRequestNotificationPermission() {
        // Only needed for Android 13 and higher
        if (Build.VERSION.SDK_INT >= 33) { // Android 13 is API level 33
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                // Permission already granted
                continueToMainActivity()
            }
        } else {
            // Notification permission not needed
            continueToMainActivity()
        }
    }
    
    private fun continueToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
} 