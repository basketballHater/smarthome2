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
import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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
    val floorName: String,
    val roomName: String,
    val linkedPath: String,
    val videoUri: Uri? = null,
    val isOnline: Boolean = false
)

data class CameraRuntime(
    val state: String = "OFF",
    val videoUrl: String? = null,
    val videoKey: String? = null
)


private val OnlineGreen = Color(0xFF36D36E)
private val OfflineRed = Color(0xFFD61F2C)
private val OfflineCard = Color(0xFFE8E7EF)
private val OfflineContent = Color(0xFFA8A8B2)

private fun buildMappedCameraFeeds(): List<CameraFeed> {
    return AppData.virtualDeviceList.mapNotNull { virtualDevice ->

        val linkedPath = virtualDevice.linkedPath
            ?: return@mapNotNull null

        val parts = linkedPath.split("/")

        // Floors/floor_01/Rooms/room_01/Cameras/camera_01
        if (parts.size < 6 || !parts[4].equals("Cameras", ignoreCase = true)) {
            return@mapNotNull null
        }

        val virtualFloor = AppData.virtualFloorList.firstOrNull { floor ->
            floor.linkedPath?.let { linkedPath.startsWith("$it/") } == true
        }

        val virtualRoom = virtualFloor?.rooms?.firstOrNull { room ->
            room.linkedPath?.let { linkedPath.startsWith("$it/") } == true
        } ?: AppData.virtualRoomList.firstOrNull { room ->
            room.linkedPath?.let { linkedPath.startsWith("$it/") } == true
        }

        CameraFeed(
            // Full path is used because camera IDs can repeat on different floors.
            id = linkedPath,
            name = virtualDevice.customName,
            floorName = virtualFloor?.customName ?: parts[1],
            roomName = virtualRoom?.customName ?: parts[3],
            linkedPath = linkedPath
        )
    }
}

@Composable
private fun rememberCameraRuntime(linkedPath: String): CameraRuntime {
    var runtime by remember(linkedPath) {
        mutableStateOf(CameraRuntime())
    }

    DisposableEffect(linkedPath) {
        val cameraReference = FirebaseDatabase.getInstance()
            .getReference(linkedPath)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rawState = snapshot.child("state").value

                val normalizedState = when (rawState) {
                    is Boolean -> if (rawState) "ON" else "OFF"

                    is Number -> if (rawState.toInt() == 1) {
                        "ON"
                    } else {
                        "OFF"
                    }

                    is String -> when (rawState.trim().uppercase()) {
                        "ON", "TRUE", "1" -> "ON"
                        else -> "OFF"
                    }

                    else -> "OFF"
                }

                runtime = CameraRuntime(
                    state = normalizedState,
                    videoUrl = snapshot.child("videoUrl")
                        .getValue(String::class.java),
                    videoKey = snapshot.child("videoKey")
                        .getValue(String::class.java)
                )

                Log.e(
                    "CAMERA_STATE",
                    "path=${linkedPath}, state=${normalizedState}, " +
                            "videoKey=${runtime.videoKey}"
                )
            }

            override fun onCancelled(error: DatabaseError) {
                runtime = CameraRuntime(state = "OFF")

                Log.e(
                    "CAMERA_SCREEN",
                    "Failed to read camera: ${linkedPath}",
                    error.toException()
                )
            }
        }

        cameraReference.addValueEventListener(listener)

        onDispose {
            cameraReference.removeEventListener(listener)
        }
    }

    return runtime
}

fun writeCameraState(linkedPath: String, isOn: Boolean) {
    val newState = if (isOn) "ON" else "OFF"
    val statePath = "${linkedPath}/state"

    FirebaseDatabase.getInstance()
        .getReference(statePath)
        .setValue(newState)
        .addOnSuccessListener {
            Log.e(
                "CAMERA_SWITCH",
                "Updated ${statePath} to ${newState}"
            )
        }
        .addOnFailureListener { error ->
            Log.e(
                "CAMERA_SWITCH",
                "Failed to update ${statePath}",
                error
            )
        }
}

private fun resolveCameraVideoUri(
    context: Context,
    feed: CameraFeed,
    runtime: CameraRuntime
): Uri? {
    val realDeviceName = AppData.deviceList
        .firstOrNull { device -> device.path == feed.linkedPath }
        ?.name
        .orEmpty()

    // A configured remote stream has first priority.
    runtime.videoUrl
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { url ->
            Log.e(
                "CAMERA_VIDEO",
                "camera=${feed.name}, using videoUrl=${url}"
            )
            return Uri.parse(url)
        }

    val firebaseKey = runtime.videoKey
        ?.trim()
        ?.lowercase()
        ?.replace("-", "_")
        ?.replace(" ", "_")

    val cameraInformation = buildString {
        append(feed.name)
        append(" ")
        append(feed.roomName)
        append(" ")
        append(feed.linkedPath)
    }.lowercase()
        .replace("-", "_")
        .replace(" ", "_")

    // The requested generated-device mapping has first priority. Other cameras
    // use Firebase videoKey, custom camera/room name, or a unique camera ID.
    val selectedKey = when {
        realDeviceName.equals(
            other = "F-0-R-2-Camera-1",
            ignoreCase = true
        ) -> {
            "living_room"
        }

        firebaseKey != null -> {
            firebaseKey
        }

        cameraInformation.contains("front_door") ||
                cameraInformation.contains("front") ||
                cameraInformation.contains("entrance") -> {
            "front_door"
        }

        cameraInformation.contains("living_room") ||
                cameraInformation.contains("living") -> {
            "living_room"
        }

        cameraInformation.contains("kitchen") -> {
            "kitchen"
        }

        cameraInformation.contains("backyard") ||
                cameraInformation.contains("back_yard") -> {
            "backyard"
        }


        else -> null
    }

    val videoResource = when (selectedKey) {
        "front_door" -> R.raw.front_door
        "living_room" -> R.raw.living_room
        "kitchen" -> R.raw.kitchen
        "backyard" -> R.raw.backyard

        else -> {
            Log.e(
                "CAMERA_VIDEO",
                "Cannot select video for ${feed.name}, " +
                        "path=${feed.linkedPath}"
            )
            return null
        }
    }

    Log.e(
        "CAMERA_VIDEO",
        "device=${realDeviceName}, camera=${feed.name}, " +
                "key=${selectedKey}, resource=${videoResource}"
    )

    return Uri.parse(
        "android.resource://${context.packageName}/${videoResource}"
    )
}

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
    val cameraFeeds = buildMappedCameraFeeds()

    Log.e(
        "CAMERA_TEST",
        "CameraScreen opened. Camera count=${cameraFeeds.size}"
    )

    val camerasByFloor = cameraFeeds
        .groupBy { it.floorName }
        .toSortedMap()

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
                text = "Live surveillance cameras from every floor.",
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
        }

        if (cameraFeeds.isEmpty()) {
            item {
                Text(
                    text = "No cameras have been mapped.",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            }
        }

        camerasByFloor.forEach { (floorName, floorCameras) ->
            item(key = "floor-$floorName") {
                Text(
                    text = floorName,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(
                items = floorCameras,
                key = { it.id }
            ) { feed ->
                CameraFeedItem(feed)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CameraFeedItem(feed: CameraFeed) {
    val context = LocalContext.current
    val runtime = rememberCameraRuntime(feed.linkedPath)

    val isOnline = runtime.state.equals(
        other = "ON",
        ignoreCase = true
    )

    val videoUri = remember(
        feed.id,
        feed.name,
        feed.roomName,
        feed.linkedPath,
        runtime.videoUrl,
        runtime.videoKey,
        context.packageName
    ) {
        resolveCameraVideoUri(
            context = context,
            feed = feed,
            runtime = runtime
        )
    }

    Log.e(
        "CAMERA_TEST",
        "name=${feed.name}, path=${feed.linkedPath}, " +
                "state=${runtime.state}, videoKey=${runtime.videoKey}, " +
                "videoUri=${videoUri}"
    )

    val currentFeed = feed.copy(
        isOnline = isOnline,
        videoUri = videoUri
    )

    if (isOnline && videoUri != null) {
        LiveCameraCard(feed = currentFeed)
    } else {
        OfflineCameraCard(feed = currentFeed)
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
    var playbackState by remember(feed.id, feed.videoUri) { mutableIntStateOf(Player.STATE_IDLE) }
    var playbackError by remember(feed.id, feed.videoUri) { mutableStateOf<PlaybackException?>(null) }

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
            Column(
                modifier = Modifier.padding(start = 9.dp)
            ) {
                Text(
                    text = feed.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = feed.roomName,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp
                )
            }
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
        Column(
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = feed.name,
                color = Color(0xFF565561),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${feed.floorName} · ${feed.roomName}",
                color = OfflineContent,
                fontSize = 11.sp
            )
        }

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
                text = "Camera is turned off",
                color = OfflineContent,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}