package com.example.smarthome

import android.R
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.layout
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TextButton
import kotlin.collections.plus
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.material3.ButtonDefaults
import kotlin.collections.List
import kotlin.math.abs
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.draw.clipToBounds

data class PlacedDeviceView(val placement: DevicePlacement, val label: String)

private const val DEVICE_HIT_RADIUS = 20f

fun findClosestDevice(tap: Point, placements: List<PlacedDeviceView>): PlacedDeviceView? {
    return placements
        .minByOrNull { kotlin.math.hypot(tap.x - it.placement.position.x, tap.y - it.placement.position.y) }
        ?.takeIf {
            kotlin.math.hypot(tap.x - it.placement.position.x, tap.y - it.placement.position.y) < DEVICE_HIT_RADIUS
        }
}

data class Point(val x: Float, val y: Float)

data class Wall(val start: Point, val end: Point)

data class Door(
    val wallIndex: Int,     // which wall in the room's wall list this door is on
    val positionOnWall: Float  // 0.0 = start of wall, 1.0 = end of wall
)

data class DevicePlacement(
    val virtualDeviceId: String,  // matches VirtualDevice.id
    val position: Point,
    val category: DeviceCategories
)

enum class DeviceCategories {
    OUTLET,
    AC,
    LIGHT,
    FAN,
    CAMERA
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Navigate(modifier: Modifier = Modifier, onDeviceSelected: (devicePath: String, deviceId: String) -> Unit = { _, _ -> }) {
    var selectedFloor by remember { mutableIntStateOf(0) }
    var selectedRoom by remember { mutableIntStateOf(0) }
    var selectedDevice by remember { mutableIntStateOf(0) }
    var roomPendingDelete by remember { mutableStateOf<VirtualRoom?>(null) }
    var pendingDevicePosition by remember { mutableStateOf<Point?>(null) }
    var deviceDialogRoomId by remember { mutableStateOf<String?>(null) }
    var floorPendingDelete by remember { mutableStateOf<VirtualFloor?>(null) }
    var devicePendingDelete by remember { mutableStateOf<VirtualDevice?>(null) }
    var objectType by remember { mutableIntStateOf(-1) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF262A31),
                        Color(0xFF1E5E6E)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Floor Overview",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Normal,
                textAlign = TextAlign.Center
            )


                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = Color(0xFF85ABB5), shape = RoundedCornerShape(16.dp))
                        .padding(5.dp)

                ) {
                    if (AppData.virtualFloorList.isNotEmpty()) {
                        items(AppData.virtualFloorList.size) { index ->

                            val floor = AppData.virtualFloorList[index]

                            Column(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .width(80.dp)
                                    .combinedClickable(
                                        onClick = {
                                            selectedFloor = index
                                            selectedRoom = 0
                                        },
                                        onLongClick = { floorPendingDelete = floor }
                                    )
                                    .height(60.dp)
                                    .background(
                                        color = if (selectedFloor == index)
                                            Color(0xFF1E5E6E)
                                        else
                                            Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                verticalArrangement = Arrangement.Center,

                                ) {
                                Text(
                                    text = floor.customName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    item() {
                        Button(
                            modifier = Modifier.padding(5.dp),
                            onClick = {
                                // Floor mapping doesn't need a placement point.
                                pendingDevicePosition = null
                                deviceDialogRoomId = null
                                objectType = 0
                                showAddDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add"
                            )
                        }

                }
            }

            Text(
                text = "Room Overview",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Normal,
                textAlign = TextAlign.Center
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color(0xFF85ABB5), shape = RoundedCornerShape(16.dp))
                    .padding(5.dp)
            ) {
                LazyRow(
                    modifier = Modifier
                ) {
                    // NEW
                    val currentRooms = safeRooms(AppData.virtualFloorList.getOrNull(selectedFloor)?.rooms)
//                    Log.d("NAVIGATE_DEBUG1", "selectedFloor=$selectedFloor, room count=${currentRooms.size}, rooms=$currentRooms")
//                    Log.d("NAVIGATE_DEBUG2", "bruh=${AppData.virtualFloorList[selectedFloor].rooms}")
//                    Log.d("NAVIGATE_DEBUG3", "bruh=${AppData.virtualFloorList.isNotEmpty()}, bruh=${currentRooms.isNotEmpty()}")

                    if (AppData.virtualFloorList.isNotEmpty() && currentRooms.isNotEmpty()) {
                        // NEW
                        items(currentRooms.size) { index ->
                            val room = currentRooms[index]
//                            items(AppData.virtualRoomList.size) { index ->
//                                val room = AppData.virtualRoomList[index]

                            Column(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .width(80.dp)
                                    .combinedClickable(
                                        onClick = { selectedRoom = index },
                                        onLongClick = { roomPendingDelete = room }
                                    )
                                    .height(60.dp)
                                    .background(
                                        color = if (selectedRoom == index)
                                            Color(0xFF1E5E6E)
                                        else
                                            Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                verticalArrangement = Arrangement.Center,

                                ) {
                                Text(
                                    text = room.customName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    item() {
                        Button(
                            modifier = Modifier.padding(5.dp),
                            onClick = {
                                // Room mapping doesn't need a placement point either.
                                pendingDevicePosition = null
                                deviceDialogRoomId = null
                                objectType = 1
                                showAddDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add"
                            )
                        }
                    }
                }
                val currentRooms = safeRooms(AppData.virtualFloorList.getOrNull(selectedFloor)?.rooms)
                val currentRoom = currentRooms.getOrNull(selectedRoom)
                var instantComplete by remember { mutableStateOf(false) }
                var undoTrigger by remember { mutableIntStateOf(0) }
                var resetTrigger by remember { mutableIntStateOf(0) }

                if (AppData.virtualFloorList.isNotEmpty() && currentRoom != null) {
                    Column(
                        modifier = Modifier
                            .height(300.dp)
                            .padding(5.dp)
                    ) {
                        var shapeComplete by remember(selectedRoom, selectedFloor) {
                            mutableStateOf(currentRoom.wallSet)
                        }
                        // NOTE: undoTrigger is deliberately NOT a key here anymore -- undo is now
                        // handled live inside RoomEditor, not by re-deriving from saved state.
                        var wallList by remember(selectedRoom, selectedFloor, resetTrigger) {
                            mutableStateOf(currentRoom.walls)
                        }

                        // Every other room's walls on this floor -- drawn translucent as context.
                        // Computed fresh each recomposition so it never goes stale.
                        val backgroundWalls = currentRooms
                            .filterIndexed { index, _ -> index != selectedRoom }
                            .flatMap { it.walls }
                        var placingDevice by remember(selectedRoom, selectedFloor) { mutableStateOf(false) }
                        var deviceInfoDialog by remember { mutableStateOf<PlacedDeviceView?>(null) }

                        val devicePlacements = currentRoom.devices.map { placement ->
                            val label = AppData.virtualDeviceList.firstOrNull { it.id == placement.virtualDeviceId }?.customName
                                ?: "Unknown device"
                            PlacedDeviceView(placement, label)
                        }

                        if (placingDevice) {
                            Text(
                                text = "Tap a spot in the room to place the device",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }

                        RoomEditor(
                            initialWalls = wallList,
                            onWallsChanged = { wallList = it },
                            shapeComplete = shapeComplete,
                            onShapeComplete = { shapeComplete = it },
                            instantComplete = instantComplete,
                            onInstantCompleteConsumed = { instantComplete = false },
                            undoSignal = undoTrigger,
                            backgroundWalls = backgroundWalls,
                            placingDevice = placingDevice,
                            onDevicePointSelected = { point ->
                                pendingDevicePosition = point
                                deviceDialogRoomId = currentRoom.id
                                placingDevice = false
                                objectType = 2
                                showAddDialog = true
                            },
                            devicePlacements = devicePlacements,
//                            onDeviceClicked = { placement ->
//                                deviceInfoDialog = devicePlacements.firstOrNull { it.placement == placement }
//                            }
                            onDeviceClicked = { placement ->
                                deviceInfoDialog = devicePlacements.firstOrNull { it.placement == placement }
                            }
                        )

                        // Save exactly once when the shape completes.
                        LaunchedEffect(shapeComplete) {
                            if (shapeComplete) {
                                updateRoom(selectedFloor, currentRoom.id) { it.copy(walls = wallList, wallSet = true) }
                                VirtualStorage.save(context)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                modifier = Modifier.padding(5.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5E6E), contentColor = Color.White),
                                onClick = { undoTrigger++ }
                            ) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Undo")
                            }
                            Button(
                                modifier = Modifier.padding(5.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5E6E), contentColor = Color.White),
                                onClick = { instantComplete = true }
                                // No save here anymore -- the shapeComplete LaunchedEffect above saves
                                // once RoomEditor has actually appended the closing wall segment.
                            ) {
                                Icon(imageVector = Icons.Default.Done, contentDescription = "Finish")
                            }
                            Button(
                                modifier = Modifier.padding(5.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (placingDevice) Color(0xFFFFA500) else Color(0xFF1E5E6E),
                                    contentColor = Color.White
                                ),
                                onClick = { placingDevice = !placingDevice }
                            ) {
                                Icon(
                                    imageVector = if (placingDevice) Icons.Default.Close else Icons.Default.Add,
                                    contentDescription = "Add Device"
                                )
                            }
//                            Button(
//                                modifier = Modifier.padding(5.dp),
//                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5E6E), contentColor = Color.White),
//                                onClick = {
//                                    updateRoom(selectedFloor, currentRoom.id) { it.copy(walls = emptyList(), wallSet = false) }
//                                    VirtualStorage.save(context)
//                                    wallList = emptyList()
//                                    shapeComplete = false
//                                    instantComplete = false
//                                    resetTrigger++
//                                }
//                            ) {
//                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
//                            }
//                            Text(text = selectedRoom.toString())
                        }
                        deviceInfoDialog?.let { pd ->
                            AlertDialog(
                                onDismissRequest = { deviceInfoDialog = null },
                                title = {
                                    Text(
                                        text = pd.label,
                                        color = Color(0xFF1E5E6E),
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column {
                                        Text(
                                            "Placed at (${pd.placement.position.x.toInt()}, ${pd.placement.position.y.toInt()})",
                                            color = Color(0xFF1E5E6E)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        TextButton(onClick = {
                                            val linkedPath = AppData.virtualDeviceList
                                                .firstOrNull { it.id == pd.placement.virtualDeviceId }
                                                ?.linkedPath

                                            if (linkedPath != null) {
                                                val lastSlash = linkedPath.lastIndexOf('/')
                                                if (lastSlash != -1) {
                                                    val devicePath = linkedPath.substring(0, lastSlash)
                                                    val deviceId = linkedPath.substring(lastSlash + 1)
                                                    onDeviceSelected(devicePath, deviceId)
                                                    deviceInfoDialog = null
                                                }
                                            }
                                        }) {
                                            Text("View Details", color = Color(0xFF1E5E6E), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        deleteVirtualDeviceEverywhere(context, pd.placement.virtualDeviceId)
                                        deviceInfoDialog = null
                                    }) { Text("Remove", color = Color.Red) }
                                },
                                dismissButton = { TextButton(onClick = { deviceInfoDialog = null }) { Text("Close", color = Color.Black) } }
                            )
                        }
                    }
                }


            }
        }

//
//        Text(
//            text = "Device Overview",
//            color = Color.White,
//            fontSize = 24.sp,
//            fontWeight = FontWeight.Bold,
//            fontStyle = FontStyle.Normal,
//            textAlign = TextAlign.Center
//        )
//
//        LazyRow(
//            modifier = Modifier
//                .fillMaxWidth()
//                .background(color = Color(0xFF9AA0A8), shape = RoundedCornerShape(16.dp))
//                .padding(5.dp)
//
//        ) {
//            items(AppData.virtualDeviceList.size) { index ->
//
//                val device = AppData.virtualDeviceList[index]
//
//                Column(
//                    modifier = Modifier.padding(2.dp)
//                        .width(80.dp)
//                        .combinedClickable(
//                            onClick = { selectedDevice = index },
//                            onLongClick = { devicePendingDelete = device }
//                        )
//                        .height(60.dp)
//                        .background(
//                            color = if (selectedDevice == index)
//                                Color(0xFF1E5E6E)
//                            else
//                                Color.Transparent,
//                            shape = RoundedCornerShape(16.dp)
//                        ),
//                    verticalArrangement = Arrangement.Center,
//
//                    ) {
//                    Text(
//                        text = device.customName,
//                        color = Color.White,
//                        fontWeight = FontWeight.Bold,
//                        textAlign = TextAlign.Center,
//                        modifier = Modifier.fillMaxWidth(),
//                    )
//                }
//            }
//
//            item() {
//                Button(
//                    modifier = Modifier.padding(5.dp),
//                    onClick = {
//                        // Direct device mapping (not placed on the map yet) -- no placement point.
//                        pendingDevicePosition = null
//                        deviceDialogRoomId = null
//                        objectType = 2
//                        showAddDialog = true
//                    }
//                ) {
//                    Icon(
//                        imageVector = Icons.Default.Add,
//                        contentDescription = "Add"
//                    )
//                }
//            }
//        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { showResetConfirm = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C3A3E), contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Reset")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset all Devices")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset everything?") },
            text = { Text("This deletes all mapped floors, rooms, and devices. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    clearAllVirtualMappings(context)
                    showResetConfirm = false
                }) { Text("Reset", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    floorPendingDelete?.let { floor ->
        AlertDialog(
            onDismissRequest = { floorPendingDelete = null },
            title = { Text(
                text = "Delete Floor?",
                color = Color(0xFF1E5E6E),
                fontWeight = FontWeight.Bold
            ) },
//            text = { Text("Are you sure you want to delete \"${room.customName}\"?") },
            text = { Text(
                text = "Are you sure you want to delete \"${floor.customName}\"?",
                color = Color(0xFF1E5E6E)
            ) },
            confirmButton = {
                TextButton(onClick = {
                    AppData.virtualFloorList.remove(floor)
                    VirtualStorage.save(context)
                    if (selectedFloor >= AppData.virtualFloorList.size) {
                        selectedFloor = (AppData.virtualFloorList.size - 1).coerceAtLeast(0)
                    }
                    floorPendingDelete = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { floorPendingDelete = null }) {
                    Text("Cancel",
                        color = Color.Black)
                }
            }
        )
    }
    roomPendingDelete?.let { room ->
        AlertDialog(
            onDismissRequest = { roomPendingDelete = null },
            containerColor = Color.White,
            title = { Text(
                text = "Delete Room?",
                color = Color(0xFF1E5E6E),
                fontWeight = FontWeight.Bold
            ) },
//            text = { Text("Are you sure you want to delete \"${room.customName}\"?") },
            text = { Text(
                text = "Are you sure you want to delete \"${room.customName}\"?",
                color = Color(0xFF1E5E6E)
            ) },

//            text = {
//                Text(
//                    text = "Device Overview",
//                    color = Color.White,
//                    fontSize = 24.sp,
//                    fontWeight = FontWeight.Bold,
//                    fontStyle = FontStyle.Normal,
//                    textAlign = TextAlign.Center
//                )
//            }

            // NEW
            confirmButton = {
                TextButton(onClick = {
                    AppData.virtualRoomList.remove(room)

                    if (selectedFloor in AppData.virtualFloorList.indices) {
                        val currentFloor = AppData.virtualFloorList[selectedFloor]
                        val updatedRooms = safeRooms(currentFloor.rooms).toMutableList().apply { remove(room) }
                        AppData.virtualFloorList[selectedFloor] = currentFloor.copy(rooms = updatedRooms)
                    }

                    VirtualStorage.save(context)
                    val remaining = safeRooms(AppData.virtualFloorList.getOrNull(selectedFloor)?.rooms).size
                    if (selectedRoom >= remaining) {
                        selectedRoom = (remaining - 1).coerceAtLeast(0)
                    }
                    roomPendingDelete = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { roomPendingDelete = null }) {
                    Text(
                        "Cancel",
                        color = Color(0XFF1C3A3A)
                    )

                }
            }
        )
    }
    devicePendingDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { devicePendingDelete = null },
            title = { Text("Delete Device?") },
            text = { Text("Are you sure you want to delete \"${device.customName}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    AppData.virtualDeviceList.remove(device)
                    VirtualStorage.save(context)
                    devicePendingDelete = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { devicePendingDelete = null }) {
                    Text("Cancelfgff", color = Color.Magenta)
                }
            }
        )
    }



    if (showAddDialog) {
        Dialog(onDismissRequest = {
            showAddDialog = false
            pendingDevicePosition = null
            deviceDialogRoomId = null
        }) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                // FIX: previously this whole screen was gated on `pendingDevicePosition != null`,
                // which is ONLY ever set by the "place device on map" flow (objectType == 2 via
                // RoomEditor's onDevicePointSelected). The Floor Overview and Room Overview "+"
                // buttons set showAddDialog = true and objectType = 0 / 1 but never touch
                // pendingDevicePosition, so the dialog opened with position == null and the
                // `if (position != null)` check silently skipped rendering AddVirtualObjectScreen
                // entirely -- that's why FloorPicker/RoomPicker never appeared.
                // Only objectType == 2 (placing a device on the map) actually needs a real point;
                // for the others we just pass a harmless placeholder that's never read.
                AddVirtualObjectScreen(
                    context = context,
                    objectType = objectType,
                    floor = selectedFloor,
                    placementRoomId = deviceDialogRoomId,
                    placementPosition = pendingDevicePosition ?: Point(0f, 0f),
                    onDone = {
                        showAddDialog = false
                        pendingDevicePosition = null
                        deviceDialogRoomId = null
                    }
                )
            }
        }
    }
}

// Replaces the old (unused) top-level deleteVirtualDevice(context, id)
fun deleteVirtualDeviceEverywhere(context: Context, deviceId: String) {
    // 1. remove the device itself
    AppData.virtualDeviceList.removeAll { it.id == deviceId }

    // 2. strip any placement referencing it from every room on every floor
    AppData.virtualFloorList.indices.forEach { floorIndex ->
        val floor = AppData.virtualFloorList[floorIndex]
        val rooms = safeRooms(floor.rooms).toMutableList()
        var changed = false

        for (i in rooms.indices) {
            val room = rooms[i]
            if (room.devices.any { it.virtualDeviceId == deviceId }) {
                rooms[i] = room.copy(devices = room.devices.filterNot { it.virtualDeviceId == deviceId })
                changed = true
            }
        }

        if (changed) {
            AppData.virtualFloorList[floorIndex] = floor.copy(rooms = rooms)
            // keep the flat virtualRoomList copy in sync, same pattern as updateRoom()
            rooms.forEach { r ->
                val flatIdx = AppData.virtualRoomList.indexOfFirst { it.id == r.id }
                if (flatIdx != -1) AppData.virtualRoomList[flatIdx] = r
            }
        }
    }

    VirtualStorage.save(context)
}

fun safeRooms(list: MutableList<VirtualRoom>?): MutableList<VirtualRoom> = list ?: mutableListOf()
@Composable
fun AddVirtualObjectScreen(
    context: Context,
    objectType: Int,
    floor: Int,
    placementRoomId: String? = null,
    placementPosition: Point,
    onDone: () -> Unit
) {
    var customName by remember { mutableStateOf("") }
    var wattage by remember { mutableDoubleStateOf(0.0) }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var selectedRoom by remember { mutableStateOf<Room?>(null) }
    var selectedFloor by remember { mutableStateOf<Floor?>(null) }
    var category by remember { mutableStateOf<DeviceCategories?>(null) }
    var maxDurationText by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Placing device at (${placementPosition.x.toInt()}, ${placementPosition.y.toInt()})",
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))


        OutlinedTextField(
            value = customName,
            onValueChange = { customName = it },
            label = { Text("Name") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("Link to Firebase device:")
        when (objectType) {
            0-> {
                FloorPicker(
                    selectedFloor = selectedFloor,
                    onFloorSelected = { selectedFloor = it }
                )
            }
            1-> {
                RoomPicker(
                    selectedRoom = selectedRoom,
                    selectedFloor = selectedFloor,
                    onRoomSelected = { selectedRoom = it }
                )
            }
            2-> {
                DevicePicker(
                    selectedDevice = selectedDevice,
                    onDeviceSelected = { selectedDevice = it },
                    wattage = wattage,
                    onWattageSelected = { wattage = it.toDoubleOrNull() ?: 0.0 },
                    category = category,
                    onDeviceCategorySelected = {category = it},
                    maxDurationMinutes = maxDurationText,
                    onMaxDurationSelected = { maxDurationText = it }

                )
            }
        }


        Spacer(modifier = Modifier.height(16.dp))
        when(objectType) {
            0 -> {
                Button(
                    onClick = {
                        if (customName.isNotBlank() && selectedFloor != null) {
                            AppData.virtualFloorList.add(
                                VirtualFloor(
                                    id = "vdev${AppData.virtualFloorList.size + 1}",
                                    customName = customName,
                                    linkedPath = selectedFloor!!.path
                                )
                            )
                            VirtualStorage.save(context)
                            onDone()
                        }
                    },
                    enabled = customName.isNotBlank() && selectedFloor != null
                ) {
                    Text("Save Mapping")
                }
            }
            // NEW
            1 -> {
                Button(
                    onClick = {
                        if (customName.isNotBlank() && selectedRoom != null && floor in AppData.virtualFloorList.indices) {
                            val newVirtualRoom = VirtualRoom(
                                id = "vdev${AppData.virtualRoomList.size + 1}",
                                customName = customName,
                                floorId = selectedRoom?.floorId ?: 0,
                                linkedPath = selectedRoom?.path ?: "0"
                            )
                            AppData.virtualRoomList.add(newVirtualRoom)

                            val currentFloor = AppData.virtualFloorList[floor]
                            val updatedRooms = safeRooms(currentFloor.rooms).toMutableList().apply { add(newVirtualRoom) }
                            AppData.virtualFloorList[floor] = currentFloor.copy(rooms = updatedRooms)

                            VirtualStorage.save(context)
                            onDone()
                        }
                    },
                    enabled = customName.isNotBlank() && selectedRoom != null
                ) {
                    Text("Save Mapping")
                }
            }
            2 -> {
                Button(
                    onClick = {
                        if (customName.isNotBlank() && selectedDevice != null) {
                            val newId = "vdev${AppData.virtualDeviceList.size + 1}"
                            val newVirtualDevice = VirtualDevice(
                                id = newId,
                                customName = customName,
                                linkedPath = selectedDevice!!.path,
                                wattage = wattage,
                                position = placementPosition,
                                maxOnDurationSeconds = maxDurationText.toLongOrNull()?.times(60)
                            )
                            AppData.virtualDeviceList.add(newVirtualDevice)

                            // NEW: actually attach this placement to the room it was placed in
                            if (placementRoomId != null && category != null) {
                                val cat = category!!
                                updateRoom(floor, placementRoomId) { r ->
                                    r.copy(devices = r.devices + DevicePlacement(newId, placementPosition, cat))
                                }
                            }

                            VirtualStorage.save(context)
                            onDone()
                        }
                    },
                    enabled = customName.isNotBlank() && selectedDevice != null
                ) {
                    Text("Save Mapping")
                }
            }

        }
    }
}

@Composable
fun DevicePicker(
    selectedDevice: Device?,
    onDeviceSelected: (Device) -> Unit,
    wattage: Double?,
    onWattageSelected: (String) -> Unit,
    category: DeviceCategories?,
    onDeviceCategorySelected: (DeviceCategories) -> Unit,
    maxDurationMinutes: String,              // NEW
    onMaxDurationSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var wattageT by remember { mutableStateOf("") }
    var needsWattage by remember { mutableStateOf(false) }

    val alreadyMappedPaths = AppData.virtualDeviceList.map { it.linkedPath }
    val availableDevices = AppData.deviceList.filter { it.path !in alreadyMappedPaths }

    Box {
        Column {

            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedDevice?.name?.toString() ?: "Select a device")
            }
            OutlinedButton(onClick = { categoryExpanded = true }) {
                Text(category?.name ?: "Select a category")
            }
            DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                DeviceCategories.entries.forEach { categoryType ->
                    DropdownMenuItem(
                        text = { Text(categoryType.name) },
                        onClick = {
                            onDeviceCategorySelected(categoryType)
                            categoryExpanded = false
                        }
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (availableDevices.isEmpty()) {
                    DropdownMenuItem(text = { Text("No unmapped devices") }, onClick = {})
                }
                availableDevices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name) },
                        onClick = {
                            onDeviceSelected(device)
                            expanded = false
                        }
                    )
                }
            }
            Text("Does Appliance have constant Wattage?")
            Checkbox(
                checked = needsWattage,
                onCheckedChange = { checked ->
                    needsWattage = checked
                }
            )
            if (needsWattage) {
                OutlinedTextField(
                    value = wattageT,
                    onValueChange = {
                        wattageT = it
                        onWattageSelected(it)
                    },
                    label = { Text("Wattage e.g. 6.7") }
                )
            }
            Text("Fire-hazard cutoff (optional)")
            OutlinedTextField(
                value = maxDurationMinutes,
                onValueChange = onMaxDurationSelected,
                label = { Text("Max ON minutes e.g. 30") }
            )
        }
    }
}

@Composable
fun FloorPicker(
    selectedFloor: Floor?,
    onFloorSelected: (Floor) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val alreadyMappedPaths = AppData.virtualFloorList.map { it.linkedPath }
    val availableFloors = AppData.floorList.filter { it.path !in alreadyMappedPaths }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedFloor?.name?.toString() ?: "Select a floor")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (availableFloors.isEmpty()) {
                DropdownMenuItem(text = { Text("No unmapped floors") }, onClick = {})
            }
            availableFloors.forEach { floor ->
                DropdownMenuItem(
                    text = { Text("${floor.name}") },
                    onClick = {
                        onFloorSelected(floor)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun RoomPicker(
    selectedRoom: Room?,
    selectedFloor: Floor?,
    onRoomSelected: (Room) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val alreadyMappedPaths = AppData.virtualRoomList.map { it.linkedPath }
    val availableRooms = AppData.roomList.filter { it.path !in alreadyMappedPaths }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedRoom?.name?.toString() ?: "Select a room")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (availableRooms.isEmpty()) {
                DropdownMenuItem(text = { Text("No unmapped devices") }, onClick = {})
            }
            availableRooms.forEach { room ->
                DropdownMenuItem(
                    text = { Text("${room.name}") },
                    onClick = {
                        onRoomSelected(room)
                        expanded = false
                    }
                )
            }
        }
    }
}


fun deleteVirtualDevice(context: Context, id: String) {
    AppData.virtualDeviceList.removeAll { it.id == id }
    VirtualStorage.save(context)
}

fun deleteVirtualRoom(context: Context, id: String) {
    AppData.virtualRoomList.removeAll { it.id == id }
    VirtualStorage.save(context)
}

fun deleteVirtualFloor(context: Context, id: String) {
    AppData.virtualFloorList.removeAll { it.id == id }
    VirtualStorage.save(context)
}

@Composable
fun RoomEditorCanvas(
    walls: List<Wall>,
    doors: List<Door>,
    devices: List<DevicePlacement>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw walls as thick black lines
        walls.forEach { wall ->
            drawLine(
                color = Color.Black,
                start = Offset(wall.start.x, wall.start.y),
                end = Offset(wall.end.x, wall.end.y),
                strokeWidth = 8f
            )
        }

        // Draw doors as a gap marker (small colored line) on top of their wall
        doors.forEach { door ->
            val wall = walls.getOrNull(door.wallIndex) ?: return@forEach
            val doorX = wall.start.x + (wall.end.x - wall.start.x) * door.positionOnWall
            val doorY = wall.start.y + (wall.end.y - wall.start.y) * door.positionOnWall
            drawCircle(color = Color.Green, radius = 10f, center = Offset(doorX, doorY))
        }

        // Draw devices as dots
        devices.forEach { device ->
            drawCircle(color = Color.Blue, radius = 12f, center = Offset(device.position.x, device.position.y))
        }
    }
}


private const val SNAP_DISTANCE = 15f
private const val TAP_SLOP = 12f
private const val MIN_SCALE = 0.4f
private const val MAX_SCALE = 5f



@Composable
fun RoomEditor(
    initialWalls: List<Wall>,
    onWallsChanged: (List<Wall>) -> Unit,
    shapeComplete: Boolean,
    onShapeComplete: (Boolean) -> Unit,
    instantComplete: Boolean,
    onInstantCompleteConsumed: () -> Unit,
    undoSignal: Int,
    backgroundWalls: List<Wall>,
    placingDevice: Boolean,
    onDevicePointSelected: (Point) -> Unit,
    devicePlacements: List<PlacedDeviceView>,
    onDeviceClicked: (DevicePlacement) -> Unit
) {
    var walls by remember { mutableStateOf(initialWalls) }
    var lastPoint by remember { mutableStateOf(initialWalls.lastOrNull()?.end) }
    var firstPoint by remember { mutableStateOf(initialWalls.firstOrNull()?.start) }

    // initialWalls changes every time we call onWallsChanged() (undo, drawing a new
    // wall, resetting the room, switching rooms) since the parent just mirrors it
    // straight back down. We only want to actually resync local state when that
    // change came from *outside* (room switch / reset button), not when it's just
    // our own edit echoing back -- otherwise we'd be fighting ourselves. Comparing
    // by value (not identity) lets the echo-back case fall through as a no-op.
    LaunchedEffect(initialWalls) {
        if (initialWalls != walls) {
            walls = initialWalls
            lastPoint = initialWalls.lastOrNull()?.end
            firstPoint = initialWalls.firstOrNull()?.start
        }
    }
    var dragPoint by remember { mutableStateOf<Point?>(null) }
    var snapX by remember { mutableStateOf<Float?>(null) }
    var snapY by remember { mutableStateOf<Float?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    fun screenToWorld(p: Offset): Point =
        Point((p.x - panOffset.x) / scale, (p.y - panOffset.y) / scale)

    // Snap to the nearest point ALONG any wall segment (not just its endpoints).
    // Lets devices slide onto/along a wall rather than only snapping to corners.
    fun snapToNearestWallPoint(point: Point, wallList: List<Wall>): Point? {
        var best: Point? = null
        var bestDist = SNAP_DISTANCE

        for (wall in wallList) {
            val dx = wall.end.x - wall.start.x
            val dy = wall.end.y - wall.start.y
            val lengthSq = dx * dx + dy * dy
            if (lengthSq == 0f) continue

            var t = ((point.x - wall.start.x) * dx + (point.y - wall.start.y) * dy) / lengthSq
            t = t.coerceIn(0f, 1f)

            val closestX = wall.start.x + t * dx
            val closestY = wall.start.y + t * dy
            val dist = kotlin.math.hypot(point.x - closestX, point.y - closestY)

            if (dist < bestDist) {
                bestDist = dist
                best = Point(closestX, closestY)
            }
        }
        return best
    }

    fun snap(point: Point, candidates: List<Point>, wallsToSnap: List<Wall> = emptyList()): Point {
        var x = point.x
        var y = point.y
        snapX = null
        snapY = null
        for (c in candidates) {
            if (abs(x - c.x) < SNAP_DISTANCE) { x = c.x; snapX = c.x }
            if (abs(y - c.y) < SNAP_DISTANCE) { y = c.y; snapY = c.y }
        }

        // Only fall back to wall-line snapping if we didn't already lock onto a point --
        // corner/device point snaps take priority over sliding along a wall.
        if (snapX == null && snapY == null && wallsToSnap.isNotEmpty()) {
            snapToNearestWallPoint(Point(x, y), wallsToSnap)?.let {
                x = it.x
                y = it.y
            }
        }

        return Point(x, y)
    }

    // Includes device positions, so walls being drawn can snap to existing devices too.
    fun snapCandidates(): List<Point> =
        backgroundWalls.flatMap { listOf(it.start, it.end) } +
                walls.flatMap { listOf(it.start, it.end) } +
                devicePlacements.map { it.placement.position } +
                listOfNotNull(firstPoint)

    LaunchedEffect(instantComplete) {
        if (instantComplete) {
            val previous = lastPoint
            val start = firstPoint
            if (previous != null && start != null && previous != start) {
                val closed = walls + Wall(previous, start)
                walls = closed
                onWallsChanged(closed)
                lastPoint = start
                onShapeComplete(true)
            }
            onInstantCompleteConsumed()
        }
    }

    val lastProcessedUndo = remember { mutableIntStateOf(undoSignal) }
    LaunchedEffect(undoSignal) {
        if (undoSignal == lastProcessedUndo.intValue) return@LaunchedEffect
        lastProcessedUndo.intValue = undoSignal

        if (walls.isNotEmpty()) {
            val trimmed = walls.dropLast(1)
            walls = trimmed
            onWallsChanged(trimmed)
            if (trimmed.isEmpty()) {
                firstPoint = null
                lastPoint = null
            } else {
                lastPoint = trimmed.last().end
            }
            if (shapeComplete) onShapeComplete(false)
        } else if (firstPoint != null) {
            // No wall segments yet, but the user has placed the starting point (the red dot).
            // Undo should clear that too, instead of doing nothing.
            firstPoint = null
            lastPoint = null
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clipToBounds()
            .pointerInput(shapeComplete, placingDevice, devicePlacements) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    var isTransforming = false
                    var prevCentroid: Offset? = null
                    var prevDistance: Float? = null
                    val downPos = down.position
                    var moved = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.size >= 2) {
                            isTransforming = true
                            val p1 = pressed[0].position
                            val p2 = pressed[1].position
                            val centroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                            val distance = (p1 - p2).getDistance().coerceAtLeast(1f)

                            if (prevCentroid != null && prevDistance != null) {
                                panOffset += centroid - prevCentroid!!
                                val zoomFactor = distance / prevDistance!!
                                val newScale = (scale * zoomFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                                val worldAtCentroid = screenToWorld(centroid)
                                scale = newScale
                                panOffset = Offset(
                                    centroid.x - worldAtCentroid.x * newScale,
                                    centroid.y - worldAtCentroid.y * newScale
                                )
                            }
                            prevCentroid = centroid
                            prevDistance = distance
                            event.changes.forEach { it.consume() }
                            dragPoint = null

                        } else if (pressed.size == 1 && !isTransforming) {
                            val change = pressed[0]
                            val pos = change.position
                            if ((pos - downPos).getDistance() > TAP_SLOP) moved = true

                            if (placingDevice) {
                                dragPoint = snap(
                                    screenToWorld(pos),
                                    snapCandidates(),
                                    backgroundWalls + walls
                                )
                            } else if (!shapeComplete) {
                                dragPoint = snap(
                                    screenToWorld(pos),
                                    snapCandidates(),
                                    backgroundWalls + walls
                                )
                            }
                            change.consume()

                        } else if (pressed.isEmpty()) {
                            if (!isTransforming) {
                                if (placingDevice) {
                                    // FIX: previously gated on `!moved`, so dragging to line up
                                    // a snap position and releasing did nothing -- only a clean
                                    // tap placed the device. Now placement happens on release
                                    // regardless of whether the user dragged: use the live
                                    // dragPoint (already snapped, updated every move) if they
                                    // dragged, or compute a fresh snap from downPos if they
                                    // didn't move at all (a plain tap).
                                    val placed = dragPoint
                                        ?: snap(
                                            screenToWorld(downPos),
                                            snapCandidates(),
                                            backgroundWalls + walls
                                        )
                                    onDevicePointSelected(placed)
                                } else {
                                    val tapWorld = dragPoint ?: screenToWorld(downPos)
                                    val clickedDevice = if (!moved) findClosestDevice(
                                        tapWorld,
                                        devicePlacements
                                    ) else null

                                    if (clickedDevice != null) {
                                        onDeviceClicked(clickedDevice.placement)
                                    } else if (!shapeComplete) {
                                        if (lastPoint == null) {
                                            val placed = dragPoint ?: snap(
                                                screenToWorld(downPos),
                                                snapCandidates(),
                                                backgroundWalls + walls
                                            )
                                            firstPoint = placed
                                            lastPoint = placed
                                        } else if (moved) {
                                            val end = dragPoint
                                            val start = lastPoint
                                            if (end != null && start != null) {
                                                val begin = firstPoint
                                                val isNearStart =
                                                    begin != null &&
                                                            end.x > begin.x - 20f && end.x < begin.x + 20f &&
                                                            end.y > begin.y - 20f && end.y < begin.y + 20f
                                                val newWall =
                                                    if (isNearStart) Wall(start, begin!!) else Wall(
                                                        start,
                                                        end
                                                    )
                                                val updated = walls + newWall
                                                walls = updated
                                                lastPoint = newWall.end
                                                onWallsChanged(updated)
                                                if (isNearStart) onShapeComplete(true)
                                            }
                                        }
                                    }
                                }
                            }
                            dragPoint = null
                            snapX = null
                            snapY = null
                            break
                        }
                    }
                }
            }
    ) {
        withTransform({
            translate(left = panOffset.x, top = panOffset.y)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            backgroundWalls.forEach { wall ->
                drawLine(
                    color = Color.Black, alpha = 0.2f,
                    start = Offset(wall.start.x, wall.start.y),
                    end = Offset(wall.end.x, wall.end.y),
                    strokeWidth = 6f
                )
            }

            walls.forEach { wall ->
                drawLine(
                    color = Color.Black,
                    start = Offset(wall.start.x, wall.start.y),
                    end = Offset(wall.end.x, wall.end.y),
                    strokeWidth = 6f
                )
            }

            if (!shapeComplete && !placingDevice && lastPoint != null && dragPoint != null) {
                drawLine(
                    color = Color.Black, alpha = 0.4f,
                    start = Offset(lastPoint!!.x, lastPoint!!.y),
                    end = Offset(dragPoint!!.x, dragPoint!!.y),
                    strokeWidth = 6f
                )
            }

            if (!shapeComplete && firstPoint != null && walls.isEmpty()) {
                drawCircle(color = Color.Red, radius = 8f, center = Offset(firstPoint!!.x, firstPoint!!.y))
            }

            devicePlacements.forEach { pd ->
                drawDeviceIcon(
                    category = pd.placement.category,
                    center = Offset(pd.placement.position.x, pd.placement.position.y)
                )
            }

            if (placingDevice && dragPoint != null) {
                drawCircle(
                    color = Color(0xFFFFA500), alpha = 0.5f,
                    radius = 10f,
                    center = Offset(dragPoint!!.x, dragPoint!!.y)
                )
            }

            snapX?.let { x ->
                drawLine(color = Color.Red, alpha = 0.4f, start = Offset(x, -10000f), end = Offset(x, 10000f), strokeWidth = 3f / scale)
            }
            snapY?.let { y ->
                drawLine(color = Color.Blue, alpha = 0.4f, start = Offset(-10000f, y), end = Offset(10000f, y), strokeWidth = 3f / scale)
            }

            drawContext.canvas.nativeCanvas.apply {
                devicePlacements.forEach { pd ->
                    drawText(
                        pd.label,
                        pd.placement.position.x + 14f,
                        pd.placement.position.y,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 24f / scale
                        }
                    )
                }
            }
        }
    }
}


fun updateRoom(floorIndex: Int, roomId: String, transform: (VirtualRoom) -> VirtualRoom) {
    if (floorIndex !in AppData.virtualFloorList.indices) return
    val floor = AppData.virtualFloorList[floorIndex]
    val rooms = safeRooms(floor.rooms).toMutableList()
    val idx = rooms.indexOfFirst { it.id == roomId }
    if (idx == -1) return

    rooms[idx] = transform(rooms[idx])
    AppData.virtualFloorList[floorIndex] = floor.copy(rooms = rooms)

    // Keep the flat virtualRoomList in sync too, since it's a separate copy.
    val flatIdx = AppData.virtualRoomList.indexOfFirst { it.id == roomId }
    if (flatIdx != -1) AppData.virtualRoomList[flatIdx] = rooms[idx]
}

fun findClosestWallAndPosition(tap: Point, walls: List<Wall>): Pair<Int, Float>? {
    var bestIndex = -1
    var bestDistance = Float.MAX_VALUE
    var bestT = 0f

    walls.forEachIndexed { index, wall ->
        val dx = wall.end.x - wall.start.x
        val dy = wall.end.y - wall.start.y
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0f) return@forEachIndexed

        // t = how far along the wall (0 to 1) the closest point to tap is
        var t = ((tap.x - wall.start.x) * dx + (tap.y - wall.start.y) * dy) / lengthSq
        t = t.coerceIn(0f, 1f)

        val closestX = wall.start.x + t * dx
        val closestY = wall.start.y + t * dy
        val distance = kotlin.math.hypot(tap.x - closestX, tap.y - closestY)

        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
            bestT = t
        }
    }

    return if (bestIndex >= 0 && bestDistance < 30f) bestIndex to bestT else null

}

private fun categoryColor(category: DeviceCategories): Color = when (category) {
    DeviceCategories.OUTLET -> Color(0xFF4CAF50)
    DeviceCategories.AC -> Color(0xFF29B6F6)
    DeviceCategories.LIGHT -> Color(0xFFFFC107)
    DeviceCategories.FAN -> Color(0xFF9575CD)
    DeviceCategories.CAMERA -> Color(0xFFEF5350)
}

fun DrawScope.drawDeviceIcon(category: DeviceCategories, center: Offset, radius: Float = 14f) {
    val bg = categoryColor(category)
    val glyphColor = Color.White
    val r = radius * 0.6f

    // soft glow + badge + ring
    drawCircle(color = bg.copy(alpha = 0.25f), radius = radius * 1.6f, center = center)
    drawCircle(color = bg, radius = radius, center = center)
    drawCircle(color = Color.White, radius = radius, center = center, style = Stroke(width = radius * 0.15f))

    when (category) {
        DeviceCategories.OUTLET -> {
            val slotW = r * 0.22f
            val slotH = r * 0.9f
            drawRoundRect(
                color = glyphColor,
                topLeft = Offset(center.x - r * 0.45f - slotW / 2f, center.y - slotH / 2f),
                size = Size(slotW, slotH),
                cornerRadius = CornerRadius(slotW / 2f)
            )
            drawRoundRect(
                color = glyphColor,
                topLeft = Offset(center.x + r * 0.45f - slotW / 2f, center.y - slotH / 2f),
                size = Size(slotW, slotH),
                cornerRadius = CornerRadius(slotW / 2f)
            )
            drawArc(
                color = glyphColor,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - r * 0.55f, center.y - r * 0.15f),
                size = Size(r * 1.1f, r * 1.1f),
                style = Stroke(width = r * 0.18f)
            )
        }
        DeviceCategories.AC -> {
            for (angleDeg in listOf(0f, 60f, 120f)) {
                val rad = Math.toRadians(angleDeg.toDouble())
                val dx = (cos(rad) * r).toFloat()
                val dy = (sin(rad) * r).toFloat()
                drawLine(
                    color = glyphColor,
                    start = Offset(center.x - dx, center.y - dy),
                    end = Offset(center.x + dx, center.y + dy),
                    strokeWidth = r * 0.16f,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(color = glyphColor, radius = r * 0.14f, center = center)
        }
        DeviceCategories.LIGHT -> {
            val bulbRadius = r * 0.55f
            val bulbCenter = Offset(center.x, center.y - r * 0.15f)
            drawCircle(color = glyphColor, radius = bulbRadius, center = bulbCenter)
            drawRoundRect(
                color = glyphColor,
                topLeft = Offset(center.x - bulbRadius * 0.5f, bulbCenter.y + bulbRadius * 0.6f),
                size = Size(bulbRadius, bulbRadius * 0.5f),
                cornerRadius = CornerRadius(bulbRadius * 0.15f)
            )
            for (angleDeg in listOf(200f, 250f, 290f, 340f)) {
                val rad = Math.toRadians(angleDeg.toDouble())
                val inner = bulbRadius * 1.25f
                val outer = bulbRadius * 1.7f
                drawLine(
                    color = glyphColor,
                    start = Offset(
                        bulbCenter.x + (cos(rad) * inner).toFloat(),
                        bulbCenter.y + (sin(rad) * inner).toFloat()
                    ),
                    end = Offset(
                        bulbCenter.x + (cos(rad) * outer).toFloat(),
                        bulbCenter.y + (sin(rad) * outer).toFloat()
                    ),
                    strokeWidth = r * 0.12f,
                    cap = StrokeCap.Round
                )
            }
        }
        DeviceCategories.FAN -> {
            for (angleDeg in listOf(0f, 120f, 240f)) {
                rotate(degrees = angleDeg, pivot = center) {
                    drawOval(
                        color = glyphColor,
                        topLeft = Offset(center.x - r * 0.12f, center.y - r),
                        size = Size(r * 0.24f, r * 0.85f)
                    )
                }
            }
            drawCircle(color = glyphColor, radius = r * 0.18f, center = center)
        }
        DeviceCategories.CAMERA -> {
            val bodyW = r * 1.5f
            val bodyH = r * 1.0f
            drawRoundRect(
                color = glyphColor,
                topLeft = Offset(center.x - bodyW / 2f, center.y - bodyH / 2f),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(r * 0.2f)
            )
            drawCircle(color = bg, radius = r * 0.3f, center = center)
            drawCircle(color = glyphColor, radius = r * 0.18f, center = center)
            drawRoundRect(
                color = glyphColor,
                topLeft = Offset(center.x + bodyW * 0.1f, center.y - bodyH / 2f - r * 0.2f),
                size = Size(r * 0.3f, r * 0.2f),
                cornerRadius = CornerRadius(r * 0.05f)
            )
        }
    }
}