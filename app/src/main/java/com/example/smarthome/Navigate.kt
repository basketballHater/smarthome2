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
    val position: Point
)


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Navigate(modifier: Modifier = Modifier) {
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
            if (AppData.virtualFloorList.isNotEmpty()) {

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = Color(0xFF9AA0A8), shape = RoundedCornerShape(16.dp))
                        .padding(5.dp)

                ) {
                    items(AppData.virtualFloorList.size) { index ->

                        val floor = AppData.virtualFloorList[index]

                        Column(
                            modifier = Modifier.padding(2.dp)
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

                    item() {
                        Button(
                            modifier = Modifier.padding(5.dp),
                            onClick = {
                                showAddDialog = true
                                objectType = 0
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add"
                            )
                        }
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
                        .background(color = Color(0xFF9AA0A8), shape = RoundedCornerShape(16.dp))
                        .padding(5.dp)
                ) {
                    LazyRow(
                        modifier = Modifier
                    ) {
                        // NEW
                        val currentRooms = safeRooms(AppData.virtualFloorList.getOrNull(selectedFloor)?.rooms)
                        Log.d("NAVIGATE_DEBUG1", "selectedFloor=$selectedFloor, room count=${currentRooms.size}, rooms=$currentRooms")
                        Log.d("NAVIGATE_DEBUG2", "bruh=${AppData.virtualFloorList[selectedFloor].rooms}")
                        Log.d("NAVIGATE_DEBUG3", "bruh=${AppData.virtualFloorList.isNotEmpty()}, bruh=${currentRooms.isNotEmpty()}")

                        if (AppData.virtualFloorList.isNotEmpty() && currentRooms.isNotEmpty()) {
                            // NEW
                            items(currentRooms.size) { index ->
                                val room = currentRooms[index]
//                            items(AppData.virtualRoomList.size) { index ->
//                                val room = AppData.virtualRoomList[index]

                                Column(
                                    modifier = Modifier.padding(2.dp)
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
                                    showAddDialog = true
                                    objectType = 1
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
                            Button(
                                modifier = Modifier.padding(5.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5E6E), contentColor = Color.White),
                                onClick = {
                                    updateRoom(selectedFloor, currentRoom.id) { it.copy(walls = emptyList(), wallSet = false) }
                                    VirtualStorage.save(context)
                                    wallList = emptyList()
                                    shapeComplete = false
                                    instantComplete = false
                                    resetTrigger++
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                            }
                            Text(text = selectedRoom.toString())
                        }
                        deviceInfoDialog?.let { pd ->
                            AlertDialog(
                                onDismissRequest = { deviceInfoDialog = null },
                                title = { Text(pd.label) },
                                text = { Text("Placed at (${pd.placement.position.x.toInt()}, ${pd.placement.position.y.toInt()})") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        updateRoom(selectedFloor, currentRoom.id) { r ->
                                            r.copy(devices = r.devices.filterNot {
                                                it.virtualDeviceId == pd.placement.virtualDeviceId && it.position == pd.placement.position
                                            })
                                        }
                                        VirtualStorage.save(context)
                                        deviceInfoDialog = null
                                    }) { Text("Remove", color = Color.Red) }
                                },
                                dismissButton = { TextButton(onClick = { deviceInfoDialog = null }) { Text("Close") } }
                            )
                        }
                    }
                }


                }
            }


            Text(
                text = "Device Overview",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Normal,
                textAlign = TextAlign.Center
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color(0xFF9AA0A8), shape = RoundedCornerShape(16.dp))
                    .padding(5.dp)

            ) {
                items(AppData.virtualDeviceList.size) { index ->

                    val device = AppData.virtualDeviceList[index]

                    Column(
                        modifier = Modifier.padding(2.dp)
                            .width(80.dp)
                            .combinedClickable(
                                onClick = { selectedDevice = index },
                                onLongClick = { devicePendingDelete = device }
                            )
                            .height(60.dp)
                            .background(
                                color = if (selectedDevice == index)
                                    Color(0xFF1E5E6E)
                                else
                                    Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        verticalArrangement = Arrangement.Center,

                        ) {
                        Text(
                            text = device.customName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item() {
                    Button(
                        modifier = Modifier.padding(5.dp),
                        onClick = {
                            showAddDialog = true
                            objectType = 2
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add"
                        )
                    }
                }
            }
        }


    floorPendingDelete?.let { floor ->
        AlertDialog(
            onDismissRequest = { floorPendingDelete = null },
            title = { Text("Delete Floor?") },
            text = { Text("Are you sure you want to delete \"${floor.customName}\"?") },
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
                    Text("Cancel")
                }
            }
        )
    }
    roomPendingDelete?.let { room ->
        AlertDialog(
            onDismissRequest = { roomPendingDelete = null },
            title = { Text("Delete Room?") },
            text = { Text("Are you sure you want to delete \"${room.customName}\"?") },
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
                    Text("Cancel")
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
                    Text("Cancel")
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
                AddVirtualObjectScreen(
                    context = context,
                    objectType = objectType,
                    floor = selectedFloor,
                    placementRoomId = deviceDialogRoomId,
                    placementPosition = pendingDevicePosition,
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

fun safeRooms(list: MutableList<VirtualRoom>?): MutableList<VirtualRoom> = list ?: mutableListOf()
@Composable
fun AddVirtualObjectScreen(
    context: Context,
    objectType: Int,
    floor: Int,
    placementRoomId: String? = null,
    placementPosition: Point? = null,
    onDone: () -> Unit
) {
    var customName by remember { mutableStateOf("") }
    var wattage by remember { mutableDoubleStateOf(0.0) }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var selectedRoom by remember { mutableStateOf<Room?>(null) }
    var selectedFloor by remember { mutableStateOf<Floor?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        if (placementPosition != null) {
            Text(
                text = "Placing device at (${placementPosition.x.toInt()}, ${placementPosition.y.toInt()})",
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

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
                    selectedFloor,
                    onRoomSelected = { selectedRoom = it }
                )
            }
            2-> {
                DevicePicker(
                    selectedDevice = selectedDevice,
                    onDeviceSelected = { selectedDevice = it },
                    wattage = wattage,
                    onWattageSelected = { wattage = it.toDoubleOrNull() ?: 0.0 }
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
                            AppData.virtualDeviceList.add(
                                VirtualDevice(
                                    id = "vdev${AppData.virtualDeviceList.size + 1}",
                                    customName = customName,
                                    linkedPath = selectedDevice!!.path,
                                    wattage = wattage
                                )
                            )
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
    onWattageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var wattageT by remember { mutableStateOf("") }
    var needsWattage by remember { mutableStateOf(false) }

    val alreadyMappedPaths = AppData.virtualDeviceList.map { it.linkedPath }
    val availableDevices = AppData.deviceList.filter { it.path !in alreadyMappedPaths }

    Box {
        Column() {

            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedDevice?.name?.toString() ?: "Select a device")
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
    var walls by remember(initialWalls) { mutableStateOf(initialWalls) }
    var lastPoint by remember(initialWalls) { mutableStateOf(initialWalls.lastOrNull()?.end) }
    var firstPoint by remember(initialWalls) { mutableStateOf(initialWalls.firstOrNull()?.start) }
    var dragPoint by remember { mutableStateOf<Point?>(null) }
    var snapX by remember { mutableStateOf<Float?>(null) }
    var snapY by remember { mutableStateOf<Float?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    fun screenToWorld(p: Offset): Point =
        Point((p.x - panOffset.x) / scale, (p.y - panOffset.y) / scale)

    fun snap(point: Point, candidates: List<Point>): Point {
        var x = point.x
        var y = point.y
        snapX = null
        snapY = null
        for (c in candidates) {
            if (abs(x - c.x) < SNAP_DISTANCE) { x = c.x; snapX = c.x }
            if (abs(y - c.y) < SNAP_DISTANCE) { y = c.y; snapY = c.y }
        }
        return Point(x, y)
    }

    fun snapCandidates(): List<Point> =
        backgroundWalls.flatMap { listOf(it.start, it.end) } +
                walls.flatMap { listOf(it.start, it.end) } +
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

    var undoReady by remember { mutableStateOf(false) }
    LaunchedEffect(undoSignal) {
        if (!undoReady) { undoReady = true; return@LaunchedEffect }
        if (walls.isEmpty()) return@LaunchedEffect

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
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .pointerInput(shapeComplete, placingDevice) {
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
                                dragPoint = screenToWorld(pos)
                            } else if (!shapeComplete) {
                                dragPoint = snap(screenToWorld(pos), snapCandidates())
                            }
                            change.consume()

                        } else if (pressed.isEmpty()) {
                            if (!isTransforming) {
                                if (placingDevice) {
                                    if (!moved) {
                                        onDevicePointSelected(screenToWorld(downPos))
                                    }
                                } else {
                                    val tapWorld = dragPoint ?: screenToWorld(downPos)
                                    val clickedDevice = if (!moved) findClosestDevice(tapWorld, devicePlacements) else null

                                    if (clickedDevice != null) {
                                        onDeviceClicked(clickedDevice.placement)
                                    } else if (!shapeComplete) {
                                        if (lastPoint == null) {
                                            val placed = dragPoint ?: snap(screenToWorld(downPos), snapCandidates())
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
                                                val newWall = if (isNearStart) Wall(start, begin!!) else Wall(start, end)
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
                drawCircle(
                    color = Color(0xFFFFA500),
                    radius = 10f,
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
