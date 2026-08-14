package com.example.smarthome

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * One camera shown on the Security Center page.
 *
 * videoUri can be:
 * - a local res/raw MP4 URI
 * - an HTTPS MP4 URL
 * - an HTTPS HLS (.m3u8) URL
 * - an RTSP URL from an IP camera
 *
 * Keep usernames/passwords out of source code in a real application.
 */
data class CameraFeed(
    val id: String,
    val name: String,
    val videoUri: Uri?,
    val isOnline: Boolean
)


private val OnlineGreen = Color(0xFF36D36E)
private val OfflineRed = Color(0xFFD61F2C)
private val OfflineCard = Color(0xFFE8E7EF)
private val OfflineContent = Color(0xFFA8A8B2)

/**
 * Add this composable as the content of the Cameras item in your bottom bar.
 *
 * Before compiling, create these three files:
 * app/src/main/res/raw/front_door.mp4
 * app/src/main/res/raw/living_room.mp4
 * app/src/main/res/raw/backyard.mp4
 */
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val cameraFeeds = remember(context.packageName) {
        listOf(
            CameraFeed(
                id = "front-door",
                name = "Front Door",
                videoUri = Uri.parse(
                    "android.resource://${context.packageName}/${R.raw.front_door}"
                ),
                isOnline = true
            ),
            CameraFeed(
                id = "living-room",
                name = "Living Room",
                videoUri = Uri.parse(
                    "android.resource://${context.packageName}/${R.raw.living_room}"
                ),
                isOnline = true
            ),
            CameraFeed(
                id = "backyard",
                name = "Backyard",
                videoUri = Uri.parse(
                    "android.resource://${context.packageName}/${R.raw.backyard}"
                ),
                isOnline = true
            ),
            CameraFeed(
                id = "garage",
                name = "Garage",
                videoUri = null,
                isOnline = false
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SmartHomeColors.BackgroundGradient)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Security Center",
                color = Color.White,
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Live surveillance and environmental safety\nmonitoring.",
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        items(cameraFeeds, key = { it.id }) { feed ->
            if (feed.isOnline && feed.videoUri != null) {
                LiveCameraCard(feed = feed)
            } else {
                OfflineCameraCard(feed = feed)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LiveCameraCard(
    feed: CameraFeed,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackState by remember(feed.id) { mutableIntStateOf(Player.STATE_IDLE) }
    var playbackError by remember(feed.id) { mutableStateOf<PlaybackException?>(null) }

    val player = remember(feed.id, feed.videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(requireNotNull(feed.videoUri)))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }

        player.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(playerListener)
            player.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(194.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF171717))
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = { playerView ->
                playerView.player = player
            },
            modifier = Modifier.fillMaxSize()
        )

        // A small dark scrim keeps the labels readable on bright videos.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.Black.copy(alpha = 0.35f))
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LIVE",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
            Text(
                text = feed.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 9.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (playbackError == null) OnlineGreen else OfflineRed,
                        shape = RoundedCornerShape(50)
                    )
            )
            Text(
                text = if (playbackError == null) "ONLINE" else "ERROR",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        if (playbackState == Player.STATE_BUFFERING && playbackError == null) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
            )
        }

        playbackError?.let {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.VideocamOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    text = "Stream unavailable",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun OfflineCameraCard(
    feed: CameraFeed,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(194.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(OfflineCard)
            .padding(16.dp)
    ) {
        Text(
            text = feed.name,
            color = Color(0xFF565561),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart)
        )

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(OfflineRed, RoundedCornerShape(50))
            )
            Text(
                text = "OFFLINE",
                color = OfflineRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.VideocamOff,
                contentDescription = "Camera offline",
                tint = OfflineContent,
                modifier = Modifier.size(49.dp)
            )
            Text(
                text = "Signal Lost",
                color = OfflineContent,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
