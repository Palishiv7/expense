package com.moneypulse.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Screen that explains privacy aspects of the app during onboarding
 */
@Composable
fun PrivacyExplanationScreen() {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Your Privacy Matters",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Description
        Text(
            text = "MoneyPulse is designed with privacy at its core. Here's how we protect your data:",
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // Privacy points
        PrivacyPoint(
            icon = Icons.Default.Lock,
            title = "Data Stays on Your Device",
            description = "All your financial data is stored only on your device. We don't upload or share your data with any servers."
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PrivacyPoint(
            icon = Icons.Default.Lock,
            title = "End-to-End Encryption",
            description = "All data stored in the app is encrypted using industry-standard AES-256 encryption, keeping your information secure."
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PrivacyPoint(
            icon = Icons.Default.Lock,
            title = "Only Reads Financial SMS",
            description = "MoneyPulse only processes SMS messages from financial institutions that contain transaction information - personal messages are never read or analyzed."
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Extra clarification
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 0.dp,
            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Additional Assurance",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• We don't track your behavior\n" +
                           "• We don't use any analytics services\n" +
                           "• We don't have any ads\n" +
                           "• We don't sell your data",
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}

@Composable
fun PrivacyPoint(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Icon card
        Card(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
            elevation = 0.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colors.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Text content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.body2
            )
        }
    }
} 