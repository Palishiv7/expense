package com.moneypulse.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
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
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.PagerState
import com.google.accompanist.pager.rememberPagerState
import com.moneypulse.app.R
import kotlinx.coroutines.launch

// Updated color definitions for a professional white-based theme
private val backgroundColor = Color(0xFFFAFAFA) // Light gray-white for all screens
private val primaryColor = Color(0xFF6200EE)
private val primaryLightColor = Color(0xFF9E76F1)
private val moneyGreen = Color(0xFF4CAF50)
private val smsBlue = Color(0xFF2196F3)
private val bankPurple = Color(0xFF9C27B0)

@OptIn(ExperimentalPagerApi::class)
@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    val pagerState = rememberPagerState()
    val coroutineScope = rememberCoroutineScope()
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Pager for onboarding screens
        HorizontalPager(
            count = 4,
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> WelcomeScreen()
                1 -> SmsPermissionScreen()
                2 -> SupportedBanksScreen()
                3 -> CompletionScreen()
            }
        }
        
        // Page indicator
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(4) { iteration ->
                val color = if (pagerState.currentPage == iteration) {
                    primaryColor
                } else {
                    Color.LightGray
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(if (pagerState.currentPage == iteration) 14.dp else 10.dp)
                )
            }
        }
        
        // Next/Get Started button
        Button(
            onClick = {
                if (pagerState.currentPage < 3) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                } else {
                    onOnboardingComplete()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .fillMaxWidth(0.9f)
                .height(58.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (pagerState.currentPage == 3) moneyGreen else primaryColor
            ),
            elevation = ButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 8.dp
            )
        ) {
            Text(
                text = if (pagerState.currentPage < 3) stringResource(R.string.onboarding_next) else stringResource(R.string.onboarding_get_started),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WelcomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Money icon with circular background
        Card(
            modifier = Modifier.size(240.dp),
            shape = CircleShape,
            backgroundColor = Color.White,
            elevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_money),
                    contentDescription = null,
                    modifier = Modifier.size(150.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_welcome_desc),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray,
            lineHeight = 28.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun SmsPermissionScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // SMS icon with circular background
        Card(
            modifier = Modifier.size(240.dp),
            shape = CircleShape,
            backgroundColor = Color.White,
            elevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_sms),
                    contentDescription = null,
                    modifier = Modifier.size(150.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = stringResource(R.string.onboarding_sms_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_sms_desc),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray,
            lineHeight = 28.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun SupportedBanksScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Bank icon with circular background
        Card(
            modifier = Modifier.size(240.dp),
            shape = CircleShape,
            backgroundColor = Color.White,
            elevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_bank),
                    contentDescription = null,
                    modifier = Modifier.size(150.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = stringResource(R.string.onboarding_banks_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_banks_desc),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray,
            lineHeight = 28.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun CompletionScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Check icon with circular background
        Card(
            modifier = Modifier.size(240.dp),
            shape = CircleShape,
            backgroundColor = Color.White,
            elevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(150.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = stringResource(R.string.onboarding_complete_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_complete_desc),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray,
            lineHeight = 28.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
} 