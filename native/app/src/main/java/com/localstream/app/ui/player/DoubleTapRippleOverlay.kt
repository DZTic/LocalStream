package com.localstream.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.ui.theme.AppIcons
import com.localstream.app.ui.theme.White
import kotlinx.coroutines.delay

private const val RIPPLE_ANIMATION_DURATION_MS = 600
private const val RIPPLE_DISMISS_DELAY_MS = 750L

@Composable
fun DoubleTapRippleOverlay(
    rippleState: DoubleTapRippleState?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rippleState == null) return

    val scaleAnim = remember(rippleState.timestamp) { Animatable(0.7f) }
    val isLeft = rippleState.side == RippleSide.LEFT

    LaunchedEffect(rippleState.timestamp) {
        scaleAnim.animateTo(
            targetValue = 1.15f,
            animationSpec = tween(durationMillis = RIPPLE_ANIMATION_DURATION_MS),
        )
    }

    LaunchedEffect(rippleState.timestamp) {
        delay(RIPPLE_DISMISS_DELAY_MS)
        onDismiss()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.45f)
                .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = if (isLeft) {
                                listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
                            } else {
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.25f))
                            },
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scaleAnim.value)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (isLeft) AppIcons.Replay10 else AppIcons.Forward10,
                            contentDescription = null,
                            tint = White,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = "${if (isLeft) "-" else "+"}${rippleState.secondsAccumulated}s",
                            color = White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
