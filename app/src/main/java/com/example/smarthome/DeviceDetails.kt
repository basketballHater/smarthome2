package com.example.smarthome

import android.util.Log
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
// DATA MODEL: one entry per device, carrying its floor/room/category
// so the screen can filter purely on this list -- no re-querying
// Firebase every time a dropdown changes.
// ===================================================================
data class DeviceEntry(
    val id: String,
    val category: String,   // plural Firebase key, e.g. "Lights"
    val typeLabel: String,  // singular display label, e.g. "Light"
    val state: String,      // "on" / "off"
    val floorId: String,
    val roomId: String,
    val parentPath: String  // "Floors/{floorId}/Rooms/{roomId}/{category}" -- no id/state suffix
)

/**
 * Loads the ENTIRE Floors tree once and keeps it live-updated.
 * This is the single source of truth that the floor dropdown, room dropdown,
 * category chips, and connected-devices list all filter against locally.
 */
@Composable
fun rememberAllDeviceEntries(): List<DeviceEntry> {
    var entries by remember { mutableStateOf(listOf<DeviceEntry>()) }

    DisposableEffect(Unit) {
        val devicesRef = FirebaseDatabase.getInstance().getReference("Floors")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    entries = emptyList()
                    return
                }
                val newList = mutableListOf<DeviceEntry>()

                for (floorSnap in snapshot.children) {
                    val floorId = floorSnap.key ?: continue
                    val roomsSnap = floorSnap.child("Rooms")

                    for (roomSnap in roomsSnap.children) {
                        val roomId = roomSnap.key ?: continue
                        val categories = listOf("Cameras", "Lights", "Acs", "Outlets")

                        for (category in categories) {
                            val typeLabel = category.dropLast(1)
                            val categorySnap = roomSnap.child(category)

                            for (deviceSnap in categorySnap.children) {
                                val deviceId = deviceSnap.key ?: continue
                                val state = deviceSnap.child("state")
                                    .getValue(String::class.java) ?: "off"
                                val parentPath = "Floors/$floorId/Rooms/$roomId/$category"

                                newList.add(
                                    DeviceEntry(
                                        id = deviceId,
                                        category = category,
                                        typeLabel = typeLabel,
                                        state = state,
                                        floorId = floorId,
                                        roomId = roomId,
                                        parentPath = parentPath
                                    )
                                )
                            }
                        }
                    }
                }
                entries = newList
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SMARTHOME", "Failed to read Floors tree", error.toException())
            }
        }
        devicesRef.addValueEventListener(listener)
        onDispose { devicesRef.removeEventListener(listener) }
    }

    return entries
}

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
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SmartHomeColors.StatBoxBackground, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(SmartHomeColors.IconCircleBackground, CircleShape),
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
            Text(text = "${entry.typeLabel}: ${entry.id}", color = SmartHomeColors.TextSecondary, fontSize = 14.sp)
            Text(text = "${entry.floorId} · ${entry.roomId}", color = SmartHomeColors.TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Switch(
            checked = entry.state == "on",
            onCheckedChange = onToggle,
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
    // Full live list of every device in the house -- the single source of
    // truth that floors/rooms/categories/connected-list all filter against.
    val allDevices = rememberAllDeviceEntries()

    // Pull the tapped device's floor/room straight out of its path:
    // "Floors/{floorId}/Rooms/{roomId}/{category}"
    val pathParts = devicePath.split("/")
    val initialFloor = pathParts.getOrNull(1) ?: ALL_FLOORS
    val initialRoom = pathParts.getOrNull(3) ?: ALL_ROOMS

    var device by remember { mutableStateOf<DeviceEntry?>(null) }
    var selectedFloor by remember { mutableStateOf(initialFloor) }
    var selectedRoom by remember { mutableStateOf(initialRoom) }
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORIES) }

    // Keep the top card's device in sync with the live list (so toggling
    // reflects immediately, same as the rest of the app).
    DisposableEffect(devicePath, deviceId) {
        val ref = FirebaseDatabase.getInstance().getReference("$devicePath/$deviceId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.child("state").getValue(String::class.java) ?: "off"
                val category = pathParts.getOrNull(4) ?: "Lights"
                device = DeviceEntry(
                    id = deviceId,
                    category = category,
                    typeLabel = category.dropLast(1),
                    state = state,
                    floorId = initialFloor,
                    roomId = initialRoom,
                    parentPath = devicePath
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DEVICE_DETAIL", "Failed to read $devicePath/$deviceId", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    // --- Dropdown option lists, derived live from Firebase data ---
    val floorOptions = listOf(ALL_FLOORS) + allDevices.map { it.floorId }.distinct().sorted()
    val roomOptions = listOf(ALL_ROOMS) + allDevices
        .filter { selectedFloor == ALL_FLOORS || it.floorId == selectedFloor }
        .map { it.roomId }
        .distinct()
        .sorted()

    // --- The filtered "connected devices" list ---
    val requestedCategoryKey = categoryLabelToFirebaseKey[selectedCategory] // null == "All"
    val connectedDevices = allDevices.filter { entry ->
        val matchesFloor = selectedFloor == ALL_FLOORS || entry.floorId == selectedFloor
        val matchesRoom = selectedRoom == ALL_ROOMS || entry.roomId == selectedRoom
        val matchesCategory = requestedCategoryKey == null || entry.category == requestedCategoryKey
        val isTheCardDevice = entry.id == deviceId && entry.parentPath == devicePath
        matchesFloor && matchesRoom && matchesCategory && !isTheCardDevice
    }

    fun toggleDevice(entry: DeviceEntry, isChecked: Boolean) {
        val newState = if (isChecked) "on" else "off"
        FirebaseDatabase.getInstance()
            .getReference("${entry.parentPath}/${entry.id}/state")
            .setValue(newState)
            .addOnFailureListener { e ->
                Log.e("DEVICE_DETAIL", "Failed to update ${entry.parentPath}/${entry.id}/state", e)
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SmartHomeColors.BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        val currentDevice = device
        if (currentDevice == null) {
            Text("Loading device...",
                color = SmartHomeColors.TextPrimary,
                fontSize = 14.sp,)
        } else {
            DeviceDetailCard(
                deviceName = currentDevice.id,
                location = "${currentDevice.floorId} · ${currentDevice.roomId}",
                category = currentDevice.category,
                isOn = currentDevice.state == "on",
                onToggle = { isChecked -> toggleDevice(currentDevice, isChecked) },
                todayUsageKwh = "—",
                onForLabel = "—"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Other Devices...",
            color = SmartHomeColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

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

        if (connectedDevices.isEmpty()) {
            Text(
                text = "No other devices match this filter.",
                color = SmartHomeColors.TextPrimary.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                connectedDevices.forEach { entry ->
                    ConnectedDeviceRow(
                        entry = entry,
                        onToggle = { isChecked -> toggleDevice(entry, isChecked) }
                    )
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