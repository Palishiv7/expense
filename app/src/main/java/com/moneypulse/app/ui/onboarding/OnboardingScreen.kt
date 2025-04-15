package com.moneypulse.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneypulse.app.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    
    val onboardingPages = listOf(
        OnboardingPage(
            title = "Welcome to MoneyPulse",
            description = "MoneyPulse helps you track your expenses automatically by analyzing your transaction SMS messages.",
            imageResId = R.drawable.onboarding_welcome
        ),
        OnboardingPage(
            title = "SMS Reading",
            description = "MoneyPulse needs SMS permission to detect your transactions. All data stays on your device and is never shared with anyone.",
            imageResId = R.drawable.onboarding_sms_permission
        ),
        OnboardingPage(
            title = "Supported Banks",
            description = "MoneyPulse works with most major banks and recognizes various transaction formats. Here are some examples.",
            imageResId = R.drawable.onboarding_supported_banks
        ),
        OnboardingPage(
            title = "You're All Set!",
            description = "Let's start tracking your finances automatically with MoneyPulse. You can always manage permissions in app settings.",
            imageResId = R.drawable.onboarding_all_set
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        // Onboarding content
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pager for onboarding pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                OnboardingPageItem(
                    page = onboardingPages[page]
                )
            }
            
            // Bottom section with indicators and button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicator
                Row(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pagerState.pageCount) { iteration ->
                        val color = if (pagerState.currentPage == iteration) {
                            MaterialTheme.colors.primary
                        } else {
                            MaterialTheme.colors.onBackground.copy(alpha = 0.3f)
                        }
                        
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Navigation buttons
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Only show "Get Started" button on the last page
                    androidx.compose.animation.AnimatedVisibility(
                        visible = pagerState.currentPage == pagerState.pageCount - 1,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Button(
                            onClick = onGetStarted,
                            modifier = Modifier
                                .height(48.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = "Get Started",
                                style = MaterialTheme.typography.button.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    
                    // Show "Next" button on all pages except the last
                    androidx.compose.animation.AnimatedVisibility(
                        visible = pagerState.currentPage < pagerState.pageCount - 1,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = "Next",
                                style = MaterialTheme.typography.button.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Next"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPageItem(
    page: OnboardingPage
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Image
        Image(
            painter = painterResource(id = page.imageResId),
            contentDescription = page.title,
            modifier = Modifier
                .size(280.dp)
                .padding(bottom = 24.dp),
            contentScale = ContentScale.Fit
        )
        
        // Title
        Text(
            text = page.title,
            style = MaterialTheme.typography.h5.copy(
                fontWeight = FontWeight.Bold
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Description
        Text(
            text = page.description,
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
        )
    }
}

data class OnboardingPage(
    val title: String,
    val description: String,
    val imageResId: Int
) 