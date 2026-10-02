package com.aetherweb.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aetherweb.app.MeshState
import com.aetherweb.app.MeshViewModel
import com.aetherweb.app.ui.theme.AetherBackground
import com.aetherweb.app.ui.theme.EmberOrange
import com.aetherweb.app.ui.theme.FlameAmber
import com.aetherweb.app.ui.theme.FlameRed
import com.aetherweb.app.ui.theme.NostrPurple
import com.aetherweb.app.ui.theme.flameGradient
import kotlinx.coroutines.launch

/**
 * 3-page first-run onboarding with a dark campfire aesthetic.
 * Page 1: the idea. Page 2: pick a fire name. Page 3: start your first Room.
 */
@Composable
fun OnboardingScreens(viewModel: MeshViewModel, onDone: () -> Unit) {
    val uiState: MeshState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var fireName by remember { mutableStateOf(viewModel.uiState.value.localUserName) }
    var glowColor by remember { mutableStateOf(EmberOrange) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AetherBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < 2) {
                    TextButton(onClick = onDone) {
                        Text("Skip", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardHeroPage()
                    1 -> OnboardNamePage(
                        fireName = fireName,
                        onNameChange = { fireName = it },
                        glowColor = glowColor,
                        onGlowChange = { glowColor = it }
                    )
                    2 -> OnboardRoomPage(onDone = onDone)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }
                    ) {
                        Text("Back", color = Color.White.copy(alpha = 0.7f))
                    }
                } else {
                    Spacer(modifier = Modifier.width(72.dp))
                }

                PagerDots(currentPage = pagerState.currentPage, pageCount = 3)

                when (pagerState.currentPage) {
                    0 -> TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                    ) {
                        Text("Next", color = EmberOrange, fontWeight = FontWeight.Bold)
                    }
                    1 -> TextButton(
                        onClick = {
                            val finalName = fireName.trim().ifBlank { uiState.localUserName }
                            viewModel.updateUserName(newName = finalName)
                            scope.launch { pagerState.animateScrollToPage(2) }
                        }
                    ) {
                        Text("Save & continue", color = EmberOrange, fontWeight = FontWeight.Bold)
                    }
                    else -> Spacer(modifier = Modifier.width(72.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardHeroPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to EmberOrange.copy(alpha = 0.55f),
                        1f to Color.Transparent
                    ),
                    radius = size.minDimension / 2f
                )
            }
            Icon(
                imageVector = Icons.Filled.Whatshot,
                contentDescription = null,
                tint = EmberOrange,
                modifier = Modifier.size(84.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Your phone is the network.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Chat, music, reels, games and calls with the people around you — no towers, no internet, no accounts.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun OnboardNamePage(
    fireName: String,
    onNameChange: (String) -> Unit,
    glowColor: Color,
    onGlowChange: (Color) -> Unit
) {
    val glowChoices = listOf(
        EmberOrange,
        FlameAmber,
        FlameRed,
        NostrPurple,
        Color(0xFF25D366)
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(glowColor.copy(alpha = 0.22f))
                .border(2.dp, glowColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fireName.firstOrNull()?.uppercase() ?: "?",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = glowColor
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Pick your fire name.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "How nearby friends see you. No account, no sign-up — just a name.",
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = fireName,
            onValueChange = onNameChange,
            label = { Text("Fire name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmberOrange,
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                focusedLabelColor = EmberOrange,
                unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                cursorColor = EmberOrange,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Avatar glow",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            glowChoices.forEach { colorChoice ->
                val selected = colorChoice == glowColor
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colorChoice)
                        .then(
                            if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier
                        )
                        .clickable { onGlowChange(colorChoice) }
                )
            }
        }
    }
}

@Composable
private fun OnboardRoomPage(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(flameGradient()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.QrCode2,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Start your first Room.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "One tap puts a QR on your screen. Friends scan it with their camera — no app needed.",
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(36.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(29.dp))
                .background(flameGradient())
                .clickable { onDone() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Enter AetherWeb",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun PagerDots(currentPage: Int, pageCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .width(if (index == currentPage) 28.dp else 8.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) EmberOrange
                        else Color.White.copy(alpha = 0.25f)
                    )
            )
        }
    }
}
