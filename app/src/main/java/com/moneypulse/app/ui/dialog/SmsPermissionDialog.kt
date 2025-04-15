package com.moneypulse.app.ui.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moneypulse.app.R

// Define colors for consistency with the rest of the app
private val backgroundColor = Color(0xFFFAFAFA)
private val primaryPurple = Color(0xFF6200EE)
private val darkTextColor = Color(0xFF212121)
private val mediumTextColor = Color(0xFF616161)

/**
 * A professional SMS permission dialog matching the app's white theme
 */
@Composable
fun SmsPermissionDialog(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Dialog(onDismissRequest = { /* Not dismissible by clicking outside */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color.White,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon with professional styling
                Card(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    backgroundColor = Color.White,
                    elevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_sms),
                            contentDescription = null,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(28.dp))
                
                // Title with improved typography
                Text(
                    text = stringResource(R.string.sms_dialog_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = darkTextColor,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Main text with improved readability
                Text(
                    text = stringResource(R.string.sms_dialog_main_text),
                    fontSize = 16.sp,
                    color = mediumTextColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.sms_dialog_second_text),
                    fontSize = 16.sp,
                    color = mediumTextColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = backgroundColor,
                    shape = RoundedCornerShape(12.dp),
                    elevation = 0.dp
                ) {
                    Text(
                        text = stringResource(R.string.sms_dialog_privacy_text),
                        fontSize = 15.sp,
                        color = mediumTextColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 23.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(28.dp))
                
                // Buttons with professional styling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    // Skip button
                    OutlinedButton(
                        onClick = onSkip,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = mediumTextColor
                        ),
                        border = ButtonDefaults.outlinedBorder,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.sms_dialog_skip),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Continue button
                    Button(
                        onClick = onContinue,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = primaryPurple
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.sms_dialog_continue),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
} 