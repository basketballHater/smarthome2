package com.example.smarthome

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

// -----------------------------------------------------------------
// Palette pulled from the "Connected Devices" screen mockup
// -----------------------------------------------------------------
object SmartHomeColors {
    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF262A31),
            Color(0xFF1E5E6E)
        )
    )
    val CardBackground = Color(0xFFA5BFC6)
    val StatBoxBackground = Color(0xFF85ABB5)
    val IconCircleBackground = Color(0xFF1C3A3E)
    val AccentTeal = Color(0xFF1E5E6E)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF000000)
    val TextLight = Color(0xFF454652)
    val IconAsh = Color(0xFF9AA5AA) // matches the unselected bottom-nav icon tone
}

// Fixed sentinel values for "no filter applied" in the dropdowns.
private const val ALL_FLOORS = "All Floors"
private const val ALL_ROOMS = "All Rooms"
private const val ALL_CATEGORIES = "All"

// Category chip labels shown in the UI -> the plural key actually stored in Firebase
// (matches the "Cameras" / "Lights" / "Acs" / "Outlets" nodes under each room).
private val categoryLabelToFirebaseKey = mapOf(
    "Lights" to "Lights",
    "Outlets" to "Outlets",
    "AC" to "Acs",
    "CCTV" to "Cameras"
)

// ===================================================================
// DATA MODEL: one entry per VIRTUAL device (from AppData.virtualDeviceList),
// carrying the custom name/wattage the user gave it plus the floor/room/
// category parsed out of its linkedPath. This is now the single source of
// truth for the floor dropdown, room dropdown, category chips, and the
// connected-devices list -- we no longer scan Firebase's Floors tree
// directly to build this screen's structure.
// ===================================================================
data class DeviceEntry(
    val virtualId: String,   // AppData.virtualDeviceList entry's id
    val customName: String,  // user-given name, e.g. "Reading Lamp"
    val wattage: Double,
    val category: String,    // plural Firebase key, parsed from linkedPath, e.g. "Lights"
    val typeLabel: String,   // singular display label, e.g. "Light"
    val floorId: String,
    val roomId: String,
    val linkedPath: String,  // real Firebase path: "Floors/{floorId}/Rooms/{roomId}/{category}/{deviceId}"
    val floorName: String?,  // custom name from AppData.virtualFloorList, null if not saved
    val roomName: String?    // custom name from AppData.virtualRoomList, null if not saved
)

/**
 * Builds the device list purely from AppData.virtualDeviceList -- no Firebase
 * read here at all. AppData.virtualDeviceList is a mutableStateListOf, so Compose
 * already recomposes this whenever it changes; we just need to read it.
 *
 * Virtual devices whose linkedPath is null (not yet mapped to a real device)
 * are skipped, since there's no real device to show state for or toggle.
 */
@Composable
fun rememberVirtualDeviceEntries(): List<DeviceEntry> {
    return AppData.virtualDeviceList.mapNotNull { vd ->
        val path = vd.linkedPath ?: return@mapNotNull null
        // Expected shape: "Floors/{floorId}/Rooms/{roomId}/{category}/{deviceId}"
        val parts = path.split("/")
        if (parts.size < 6) return@mapNotNull null
        val category = parts[4]
        DeviceEntry(
            virtualId = vd.id,
            customName = vd.customName,
            wattage = vd.wattage,
            category = category,
            typeLabel = category.dropLast(1),
            floorId = parts[1],
            roomId = parts[3],
            linkedPath = path,
            floorName = virtualFloorNameForPath(path),
            roomName = virtualRoomNameForPath(path)
        )
    }
}

/**
 * Live on/off state for a single real device, read from "{linkedPath}/state".
 * Shared by the top card and every connected-device row so each subscribes
 * only to the one path it actually needs instead of one big multi-path listener.
 */
@Composable
fun rememberDeviceState(linkedPath: String): String {
    var state by remember(linkedPath) { mutableStateOf("off") }

    DisposableEffect(linkedPath) {
        val ref = FirebaseDatabase.getInstance().getReference("$linkedPath/state")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                state = snapshot.getValue(String::class.java) ?: "off"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DEVICE_DETAIL", "Failed to read $linkedPath/state", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    return state
}

fun toggleDeviceState(linkedPath: String, isChecked: Boolean) {
    val newState = if (isChecked) "on" else "off"
    FirebaseDatabase.getInstance()
        .getReference("$linkedPath/state")
        .setValue(newState)
        .addOnFailureListener { e ->
            Log.e("DEVICE_DETAIL", "Failed to update $linkedPath/state", e)
        }
}

// Resolves a real device path to the custom name of the SAVED virtual floor
// it falls under, by checking which virtualFloorList entry's linkedPath is a
// prefix of the device path. Returns null if that floor was never saved.
private fun virtualFloorNameForPath(devicePath: String): String? =
    AppData.virtualFloorList.firstOrNull { floor ->
        floor.linkedPath?.let { devicePath.startsWith("$it/") } == true
    }?.customName

// Same idea, for the SAVED virtual room.
private fun virtualRoomNameForPath(devicePath: String): String? =
    AppData.virtualRoomList.firstOrNull { room ->
        room.linkedPath?.let { devicePath.startsWith("$it/") } == true
    }?.customName

// ===================================================================
// FILTERS: floor/room dropdown pills + category chip row
// ===================================================================

@Composable
fun DropdownPill(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .background(SmartHomeColors.AccentTeal, RoundedCornerShape(20.dp))
            .clickable { expanded = true }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = selected,
            color = SmartHomeColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = SmartHomeColors.TextPrimary
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option) },
                onClick = {
                    onSelected(option)
                    expanded = false
                }
            )
        }
    }
}

@Composable
fun FloorRoomSelector(
    selectedFloor: String,
    floors: List<String>,
    onFloorSelected: (String) -> Unit,
    selectedRoom: String,
    rooms: List<String>,
    onRoomSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        DropdownPill(selected = selectedFloor, options = floors, onSelected = onFloorSelected)
        Spacer(modifier = Modifier.width(10.dp))
        DropdownPill(selected = selectedRoom, options = rooms, onSelected = onRoomSelected)
    }
}

@Composable
fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            Text(
                text = category,
                color = if (isSelected) SmartHomeColors.TextSecondary else SmartHomeColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .background(
                        color = if (isSelected) SmartHomeColors.TextPrimary else SmartHomeColors.IconCircleBackground,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

// ===================================================================
// DEVICE DETAIL CARD: icon, name/location, toggle, usage stats
// ===================================================================

@Composable
fun DeviceDetailCard(
    deviceName: String,
    location: String,
    category: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    todayUsageKwh: String,
    onForLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SmartHomeColors.CardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(SmartHomeColors.IconCircleBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconFor(category),
                        contentDescription = category,
                        tint = SmartHomeColors.IconAsh,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deviceName,
                        color = SmartHomeColors.TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = location,
                        color = SmartHomeColors.TextLight,
                        fontSize = 13.sp
                    )
                }

                Switch(
                    checked = isOn,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = SmartHomeColors.AccentTeal,
                        checkedThumbColor = SmartHomeColors.TextPrimary,
                        uncheckedTrackColor = SmartHomeColors.StatBoxBackground,
                        uncheckedThumbColor = SmartHomeColors.TextSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatBox(label = "Today's usage", value = todayUsageKwh, modifier = Modifier.weight(1f))
                StatBox(label = "On for", value = onForLabel, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(SmartHomeColors.StatBoxBackground, RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp, horizontal = 14.dp)
    ) {
        Column {
            Text(text = label, color = SmartHomeColors.TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = SmartHomeColors.TextLight,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ===================================================================
// CONNECTED DEVICE ROW: one line item in the filtered list below the card
// ===================================================================

private fun iconFor(category: String) = when (category) {
    "Lights" -> Icons.Filled.Lightbulb
    "Outlets" -> Icons.Filled.ElectricalServices
    "Acs" -> Icons.Filled.AcUnit
    "Cameras" -> Icons.Filled.Videocam
    else -> Icons.Filled.ElectricalServices
}

@Composable
fun ConnectedDeviceRow(
    entry: DeviceEntry,
    modifier: Modifier = Modifier
) {
    val state = rememberDeviceState(entry.linkedPath)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SmartHomeColors.IconCircleBackground, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(SmartHomeColors.CardBackground, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconFor(entry.category),
                contentDescription = entry.category,
                tint = SmartHomeColors.IconAsh,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.customName, color = SmartHomeColors.TextPrimary, fontSize = 14.sp)
            Text(text = "${entry.floorName ?: entry.floorId} · ${entry.roomName ?: entry.roomId}", color = SmartHomeColors.TextPrimary.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Switch(
            checked = state == "on",
            onCheckedChange = { isChecked -> toggleDeviceState(entry.linkedPath, isChecked) },
            colors = SwitchDefaults.colors(
                checkedTrackColor = SmartHomeColors.AccentTeal,
                checkedThumbColor = SmartHomeColors.TextPrimary,
                uncheckedTrackColor = SmartHomeColors.StatBoxBackground,
                uncheckedThumbColor = SmartHomeColors.TextSecondary
            )
        )
    }
}

// ===================================================================
// FULL PAGE: filters on top, device card below, connected devices list,
// all wired to Firebase.
//
// devicePath / deviceId identify the device that was tapped (e.g. from the
// floor plan / navigation tab). The floor + room filters default to THAT
// device's floor/room, and the connected-devices list defaults to every
// other device in that same floor/room.
// ===================================================================

@Composable
fun DeviceDetailScreen(
    devicePath: String,
    deviceId: String,
    modifier: Modifier = Modifier
) {
    var isEmpty by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val devicesRef = FirebaseDatabase.getInstance().getReference("Floors")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    isEmpty = true
                    AppData.deviceList.clear()
                    return
                }
                isEmpty = false
                val newList1 = mutableStateListOf<Device>()
                val newList2 = mutableStateListOf<Room>()
                val newList3 = mutableStateListOf<Floor>()

                var roomNum = 0
                var floorNum = 0


                snapshot.children.forEachIndexed { index, floorSnap ->
                    val floorId = floorSnap.key ?: return@forEachIndexed
                    newList3.add(
                        Floor(id = index, name = floorId,path = "Floors/$floorId"  )
                    )
                    val floorNum = index
                    val roomsSnap = floorSnap.child("Rooms")
                    roomsSnap.children.forEachIndexed { index, roomSnap ->
                        val roomId = roomSnap.key ?: return@forEachIndexed
                        val categories = listOf("Cameras", "Lights", "Acs", "Outlets")
                        newList2.add(
                            Room(id = index, name = "$floorNum-$roomId",floorId = floorNum ,path = "Floors/$floorId/Rooms/$roomId"  )
                        )
                        val roomNum = index
                        for (category in categories) {
                            val typeLabel = category.dropLast(1)
                            val categorySnap = roomSnap.child(category)

                            categorySnap.children.forEachIndexed { index, deviceSnap ->
                                val devName = deviceSnap.key
                                val state = deviceSnap.child("state")
                                    .getValue(String::class.java) ?: "off"
                                val path = "Floors/$floorId/Rooms/$roomId/$category/$devName"

                                newList1.add(
                                    Device(id = index, name ="F-$floorNum-R-$roomNum-$typeLabel-$index", type = typeLabel, state = state, path = path)
                                )
                            }
                        }
                    }
                }
                AppData.deviceList = newList1
                AppData.roomList = newList2
                AppData.floorList = newList3
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SMARTHOME", "Firebase read failed", error.toException())
            }
        }
        devicesRef.addValueEventListener(listener)
        onDispose { devicesRef.removeEventListener(listener) }
    }

    if (isEmpty) {
        Text(
            text = "No data found at 'Floors'. Check your Firebase path/rules.",
            modifier = modifier.padding(16.dp)
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            for (device in AppData.deviceList) {
                DeviceRow(device = device)
            }
        }
    }
    // The device list, floor list, and room list all now come from the
    // VIRTUAL layer (AppData.virtualDeviceList) instead of scanning Firebase's
    // Floors tree directly -- this reflects whatever devices have been
    // added/named/mapped via the virtual device screen.
    val virtualDevices = rememberVirtualDeviceEntries()
        .filter { it.floorName != null && it.roomName != null }

    val pathParts = devicePath.split("/")
    val fullDevicePath = "$devicePath/$deviceId"

    val initialFloorName = virtualFloorNameForPath(fullDevicePath) ?: ALL_FLOORS
    val initialRoomName = virtualRoomNameForPath(fullDevicePath) ?: ALL_ROOMS

    var selectedFloor by remember { mutableStateOf(initialFloorName) }
    var selectedRoom by remember { mutableStateOf(initialRoomName) }
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORIES) }

    val matchingVirtual = virtualDevices.find { it.linkedPath == fullDevicePath }
    val cardCategory = matchingVirtual?.category ?: (pathParts.getOrNull(4) ?: "Lights")
    val cardName = matchingVirtual?.customName ?: deviceId
    val cardState = rememberDeviceState(fullDevicePath)

// --- Dropdown option lists, now driven by the SAVED virtual floors/rooms ---
    val floorOptions = listOf(ALL_FLOORS) + AppData.virtualFloorList.map { it.customName }.distinct().sorted()

    val selectedVirtualFloor = AppData.virtualFloorList.find { it.customName == selectedFloor }
    val roomOptions = listOf(ALL_ROOMS) + AppData.virtualRoomList.filter { room ->
        selectedFloor == ALL_FLOORS ||
                (selectedVirtualFloor?.linkedPath != null &&
                        room.linkedPath?.startsWith("${selectedVirtualFloor.linkedPath}/") == true)
    }.map { it.customName }.distinct().sorted()

// --- The filtered "connected devices" list, matched by saved custom names ---
    val requestedCategoryKey = categoryLabelToFirebaseKey[selectedCategory]
    val connectedDevices = virtualDevices.filter { entry ->
        val matchesFloor = selectedFloor == ALL_FLOORS || entry.floorName == selectedFloor
        val matchesRoom = selectedRoom == ALL_ROOMS || entry.roomName == selectedRoom
        val matchesCategory = requestedCategoryKey == null || entry.category == requestedCategoryKey
        val isTheCardDevice = entry.linkedPath == fullDevicePath
        matchesFloor && matchesRoom && matchesCategory && !isTheCardDevice
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SmartHomeColors.BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        DeviceDetailCard(
            deviceName = cardName,
            location = "${matchingVirtual?.floorName ?: initialFloorName} · ${matchingVirtual?.roomName ?: initialRoomName}",
            category = cardCategory,
            isOn = cardState == "on",
            onToggle = { isChecked -> toggleDeviceState(fullDevicePath, isChecked) },
            todayUsageKwh = matchingVirtual?.let { "${it.wattage} W" } ?: "—",
            onForLabel = "—"
        )

        Spacer(modifier = Modifier.height(16.dp))

        FloorRoomSelector(
            selectedFloor = selectedFloor,
            floors = floorOptions,
            onFloorSelected = { newFloor ->
                selectedFloor = newFloor
                // Changing floor invalidates the previously selected room.
                selectedRoom = ALL_ROOMS
            },
            selectedRoom = selectedRoom,
            rooms = roomOptions,
            onRoomSelected = { selectedRoom = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        CategoryFilterRow(
            categories = listOf(ALL_CATEGORIES) + categoryLabelToFirebaseKey.keys,
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connected devices",
            color = SmartHomeColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (connectedDevices.isEmpty()) {
            Text(
                text = "No other devices match this filter.",
                color = SmartHomeColors.TextPrimary.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                connectedDevices.forEach { entry ->
                    ConnectedDeviceRow(entry = entry)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceDetailCardPreview() {
    MaterialTheme {
        DeviceDetailCard(
            deviceName = "Light bulb 01",
            location = "F1 · Bedroom",
            category = "Lights",
            isOn = true,
            onToggle = {},
            todayUsageKwh = "0.4 kWh",
            onForLabel = "2h 10m"
        )
    }
}