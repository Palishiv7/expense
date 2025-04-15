package com.moneypulse.app.ui.privacy

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.moneypulse.app.ui.MoneyPulseTheme

/**
 * Activity to display the Privacy Policy in a WebView
 */
class PrivacyPolicyActivity : ComponentActivity() {
    
    companion object {
        // Privacy policy URL - replace with your actual privacy policy URL when ready
        const val PRIVACY_POLICY_URL = "https://www.moneypulse.com/privacy-policy"
        
        // Privacy policy asset path for local viewing - app/src/main/assets/privacy_policy.html
        const val PRIVACY_POLICY_LOCAL_FILE = "file:///android_asset/privacy_policy.html"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MoneyPulseTheme {
                PrivacyPolicyScreen(onBackPressed = { finish() })
            }
        }
    }
}

@Composable
fun PrivacyPolicyScreen(onBackPressed: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Use AndroidView to embed WebView in Compose
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = false // Disable JavaScript for security
                        
                        // Load local file if it exists, otherwise load from URL
                        // For development, use loadUrl with the URL
                        // For production with bundled privacy policy, use loadUrl with local file path
                        loadUrl(PrivacyPolicyActivity.PRIVACY_POLICY_LOCAL_FILE)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
} 