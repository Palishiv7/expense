package com.moneypulse.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen that shows examples of recognized bank SMS messages
 */
@Composable
fun BankExamplesScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Supported Message Formats",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Description
        Text(
            text = "MoneyPulse can detect transactions from various banks. Here are some examples:",
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Examples list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(bankExamples) { example ->
                BankMessageExample(example)
            }
        }
    }
}

@Composable
fun BankMessageExample(
    example: BankExample
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            )
            .background(MaterialTheme.colors.surface)
            .padding(16.dp)
    ) {
        // Bank name
        Text(
            text = example.bankName,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Message example
        Text(
            text = example.messageText,
            style = MaterialTheme.typography.body2,
            lineHeight = 20.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // What MoneyPulse detects
        Text(
            text = "MoneyPulse detects:",
            style = MaterialTheme.typography.caption,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Detection details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.caption
                )
                Text(
                    text = example.amount,
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Column {
                Text(
                    text = "Merchant",
                    style = MaterialTheme.typography.caption
                )
                Text(
                    text = example.merchant,
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class BankExample(
    val bankName: String,
    val messageText: String,
    val amount: String,
    val merchant: String
)

// Sample bank message examples
val bankExamples = listOf(
    BankExample(
        bankName = "HDFC Bank",
        messageText = "Rs.2,599.00 has been debited from a/c no. XX7290 on 12-04-23 for POS purchase at AMAZON RETAIL IN. Avl bal: Rs.45,321.56",
        amount = "Rs.2,599.00",
        merchant = "AMAZON RETAIL"
    ),
    BankExample(
        bankName = "ICICI Bank",
        messageText = "ALERT: Your ICICI Bank Debit Card XX1234 has been used for Rs.850.00 at SWIGGY on 11-04-23 at 19:45:32. If not done by you, call 1800226999.",
        amount = "Rs.850.00",
        merchant = "SWIGGY"
    ),
    BankExample(
        bankName = "SBI",
        messageText = "Your a/c no. XX8732 has been debited with INR 1,299.00 on 10-04-23 and credited to FLIPKART. Txn ID: SBIH123456789. Call 18001234 to report fraud.",
        amount = "INR 1,299.00",
        merchant = "FLIPKART"
    ),
    BankExample(
        bankName = "Axis Bank",
        messageText = "INR 450.00 debited from your A/C XXXX1234 on 09-APR-23 for UPI payment to ZOMATO. UPI Ref: 123456789012. Balance: INR 32,450.75",
        amount = "INR 450.00",
        merchant = "ZOMATO"
    )
) 