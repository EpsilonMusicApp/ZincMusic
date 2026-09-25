package com.zincmusic.app.push

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Central hub for in-app notifications delivered through Firebase Cloud Messaging.
 *
 * [ZincFirebaseMessagingService] posts messages here whenever a push arrives while
 * the app is in the foreground; [InAppNotificationHost] — mounted at the root of
 * MainActivity — turns them into an elegant banner that slides in from the top of
 * the screen. This works without any notification permission and never interrupts
 * playback.
 */
object InAppNotificationCenter {

    data class Message(
        val id: Long,
        val title: String,
        val body: String
    )

    private val idCounter = AtomicLong(System.currentTimeMillis())

    private val _messages = MutableSharedFlow<Message>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Hot stream of incoming announcement messages. */
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    /** Post a message to the in-app banner. Safe to call from any thread. */
    fun post(title: String, body: String) {
        _messages.tryEmit(Message(idCounter.incrementAndGet(), title, body))
    }
}

private const val BANNER_AUTO_DISMISS_MS = 5_000L

/**
 * Overlay host that renders incoming [InAppNotificationCenter] messages as a
 * Material 3 Expressive banner pinned below the status bar, above every screen
 * and player sheet. Mount once, at the root of the activity content.
 */
@Composable
fun InAppNotificationHost(modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<InAppNotificationCenter.Message?>(null) }
    var visible by remember { mutableStateOf(false) }

    // A newer message replaces the one on screen and restarts the dismiss timer.
    LaunchedEffect(Unit) {
        InAppNotificationCenter.messages.collect { incoming ->
            message = incoming
            visible = true
        }
    }

    LaunchedEffect(message?.id) {
        if (message != null) {
            delay(BANNER_AUTO_DISMISS_MS)
            visible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(1000f),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { -it } + fadeIn(tween(180)),
            exit = slideOutVertically(animationSpec = tween(220)) { -it } + fadeOut(tween(180))
        ) {
            message?.let { current ->
                InAppNotificationBanner(
                    message = current,
                    onDismiss = { visible = false }
                )
            }
        }
    }
}

@Composable
private fun InAppNotificationBanner(
    message: InAppNotificationCenter.Message,
    onDismiss: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.inverseSurface)
            .clickable(onClick = onDismiss)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 20.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Campaign,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.inversePrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (message.title.isNotBlank()) {
                Text(
                    text = message.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (message.body.isNotBlank()) {
                if (message.title.isNotBlank()) Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
