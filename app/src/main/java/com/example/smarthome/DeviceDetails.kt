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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay





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
//
// outletCount > 1 marks this entry as a MULTISWITCH: its own linkedPath has
// no direct "state" leaf, it's a parent node with outlet_1, outlet_2, ...
// children underneath. Everywhere that reads/writes state directly against
// entry.linkedPath needs to check outletCount first.
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
    val roomName: String?,
    val maxOnDurationSeconds: Long?,// custom name from AppData.virtualRoomList, null if not saved
    val outletCount: Int = 1,          // NEW: >1 means this entry is a multiswitch
    val outletLabels: List<String>? = null // NEW: optional custom outlet names
)

data class DeviceUsage(
    val state: String,
    val lastChangedAt: Long,
    val usageDate: String,
    val todayOnSeconds: Long
)

enum class DeviceStatus {
    ON, OFF, ERROR, DISCONNECTED;

    companion object {
        fun fromString(raw: String?): DeviceStatus = when (raw?.uppercase()) {
            "ON" -> ON
            "OFF" -> OFF
            "ERROR" -> ERROR
            "DISCONNECTED" -> DISCONNECTED
            else -> OFF
        }
    }

    val isControllable: Boolean get() = this == ON || this == OFF
}
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
            roomName = virtualRoomNameForPath(path),
            maxOnDurationSeconds = vd.maxOnDurationSeconds,
            outletCount = vd.outletCount,
            outletLabels = vd.outletLabels
        )
    }
}


@Composable
fun rememberDeviceUsage(linkedPath: String): DeviceUsage {
    var usage by remember(linkedPath) {
        mutableStateOf(DeviceUsage("off", System.currentTimeMillis(), currentDateString(), 0L))
    }

    DisposableEffect(linkedPath) {
        val ref = FirebaseDatabase.getInstance().getReference(linkedPath)
        ref.keepSynced(true)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usage = DeviceUsage(
                    state = snapshot.child("state").getValue(String::class.java) ?: "off",
                    lastChangedAt = snapshot.child("lastChangedAt").getValue(Long::class.java)
                        ?: System.currentTimeMillis(),
                    usageDate = snapshot.child("usageDate").getValue(String::class.java)
                        ?: currentDateString(),
                    todayOnSeconds = snapshot.child("todayOnSeconds").getValue(Long::class.java) ?: 0L
                )
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("DEVICE_DETAIL", "Failed to read $linkedPath", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }
    return usage
}

/**
 * Same as rememberDeviceUsage, but for all N outlets underneath a multiswitch's
 * base path at once: basePath/outlet_1 .. basePath/outlet_N. Returned in order,
 * so index i corresponds to outlet_(i+1).
 */
@Composable
fun rememberOutletUsages(basePath: String, count: Int): List<DeviceUsage> {
    return (1..count).map { i -> rememberDeviceUsage("$basePath/outlet_$i") }
}

// Recomposes callers once a minute so live durations keep advancing on screen
@Composable
fun rememberTicker(intervalMillis: Long = 10_000L): Long {
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(intervalMillis)
            tick = System.currentTimeMillis()
        }
    }
    return tick
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

fun currentDateString(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

fun toggleDeviceState(linkedPath: String, isChecked: Boolean) {
    val ref = FirebaseDatabase.getInstance().getReference(linkedPath)
    val now = System.currentTimeMillis()
    val today = currentDateString()

    ref.runTransaction(object : com.google.firebase.database.Transaction.Handler {
        override fun doTransaction(data: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
            val prevStatus = DeviceStatus.fromString(data.child("state").getValue(String::class.java))
            val prevChangedAt = data.child("lastChangedAt").getValue(Long::class.java) ?: now
            val prevUsageDate = data.child("usageDate").getValue(String::class.java) ?: today
            val prevTodaySeconds = data.child("todayOnSeconds").getValue(Long::class.java) ?: 0L

            var newTodaySeconds: Long
            if (prevUsageDate == today) {
                newTodaySeconds = prevTodaySeconds
            } else {
                // Day rolled over -- archive the previous day's total instead of
                // discarding it, so Week/Month reports have something to sum.
                if (prevTodaySeconds > 0) {
                    data.child("History").child(prevUsageDate).value = prevTodaySeconds
                }
                newTodaySeconds = 0L
            }
            if (prevStatus == DeviceStatus.ON) {
                val elapsed = ((now - prevChangedAt) / 1000).coerceAtLeast(0)
                newTodaySeconds += elapsed
            }

            data.child("state").value = if (isChecked) "ON" else "OFF"
            data.child("lastChangedAt").value = now
            data.child("usageDate").value = today
            data.child("todayOnSeconds").value = newTodaySeconds
            return com.google.firebase.database.Transaction.success(data)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
            if (error != null) Log.e("DEVICE_DETAIL", "Toggle failed for $linkedPath", error.toException())
        }
    })
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
//
// showToggle = false is used for multiswitch parents: there's no single
// on/off state to bind a Switch to (the real state lives one level down,
// per-outlet), so the switch is hidden and todayUsageKwh/onForLabel are
// expected to already be aggregates the caller computed across all outlets.
// ===================================================================

@Composable
fun DeviceDetailCard(
    deviceName: String,
    location: String,
    category: String,
    deviceStatus: DeviceStatus,
    onToggle: (Boolean) -> Unit,
    todayUsageKwh: String,
    onForLabel: String,
    wattage: Double,
    maxOnDurationSeconds: Long?,
    onSaveEdits: (newName: String, newWattage: Double, newMaxDurationSeconds: Long?) -> Unit,
    modifier: Modifier = Modifier,
    showToggle: Boolean = true
) {
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember(deviceName, isEditing) { mutableStateOf(deviceName) }
    var editWattageText by remember(wattage, isEditing) {
        mutableStateOf(if (wattage > 0) wattage.toString() else "")
    }
    var editMaxDurationText by remember(maxOnDurationSeconds, isEditing) {   // NEW
        mutableStateOf(maxOnDurationSeconds?.let { (it / 60).toString() } ?: "")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SmartHomeColors.CardBackground)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 32.dp),
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
                        if (isEditing) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = deviceName,
                                color = SmartHomeColors.TextSecondary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = buildString {
                                    append(if (wattage > 0) "$location · ${wattage.toInt()}W" else location)
                                    maxOnDurationSeconds?.takeIf { it > 0 }?.let {
                                        append(" · cutoff ${it / 60}min")
                                    }
                                },
                                color = SmartHomeColors.TextLight,
                                fontSize = 13.sp
                            )
                            Text(
                                text = deviceStatus.name,
                                color = when (deviceStatus) {
                                    DeviceStatus.ON -> Color(0xFF4CAF50)
                                    DeviceStatus.OFF -> SmartHomeColors.TextLight
                                    DeviceStatus.ERROR -> Color(0xFFEF5350)
                                    DeviceStatus.DISCONNECTED -> Color(0xFF9E9E9E)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!isEditing && showToggle) {
                        Switch(
                            checked = deviceStatus == DeviceStatus.ON,
                            onCheckedChange = onToggle,
                            enabled = deviceStatus.isControllable,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = SmartHomeColors.AccentTeal,
                                checkedThumbColor = SmartHomeColors.TextPrimary,
                                uncheckedTrackColor = SmartHomeColors.StatBoxBackground,
                                uncheckedThumbColor = SmartHomeColors.TextSecondary
                            )
                        )
                    }
                }

                if (isEditing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editWattageText,
                        onValueChange = { editWattageText = it },
                        label = { Text("Wattage (W)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(                                        // NEW
                        value = editMaxDurationText,
                        onValueChange = { editMaxDurationText = it },
                        label = { Text("Max ON minutes (fire-hazard cutoff, optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = {
                            val parsedWattage = editWattageText.toDoubleOrNull() ?: 0.0
                            val parsedMaxDuration = editMaxDurationText.toLongOrNull()?.times(60)  // NEW, null if blank/invalid
                            onSaveEdits(editName.ifBlank { deviceName }, parsedWattage, parsedMaxDuration)  // CHANGED
                            isEditing = false
                        }) {
                            Text("Save", color = SmartHomeColors.AccentTeal, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { isEditing = false }) {
                            Text("Cancel", color = SmartHomeColors.TextLight)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatBox(label = "Today's usage", value = todayUsageKwh, modifier = Modifier.weight(1f))
                    StatBox(label = "Current session", value = onForLabel, modifier = Modifier.weight(1f))
                }
            }

            if (!isEditing) {
                IconButton(
                    onClick = { isEditing = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = SmartHomeColors.TextLight)
                }
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
    // Multiswitches don't have a single state at entry.linkedPath (that path is
    // just the parent node), so there's nothing valid to bind a Switch to here.
    // Show a summary instead -- opening the multiswitch's own Device Details
    // page (via the floor plan) is where each outlet gets its own toggle.
    if (entry.outletCount > 1) {
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
                Text(
                    text = "${entry.floorName ?: entry.floorId} · ${entry.roomName ?: entry.roomId}",
                    color = SmartHomeColors.TextPrimary.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
            Text(
                text = "${entry.outletCount} outlets",
                color = SmartHomeColors.TextPrimary.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    val usage = rememberDeviceUsage(entry.linkedPath)

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
        val rowStatus = DeviceStatus.fromString(usage.state)
        Switch(
            checked = rowStatus == DeviceStatus.ON,
            enabled = rowStatus.isControllable,
            onCheckedChange = { isChecked ->
                if (rowStatus.isControllable) toggleDeviceState(entry.linkedPath, isChecked) },
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
// OUTLET ROW: one individually-controllable outlet within a multiswitch.
// Mirrors ConnectedDeviceRow's look, but takes an already-fetched DeviceUsage
// instead of subscribing itself, so a whole multiswitch section only opens
// one Firebase listener per outlet (shared with the aggregate header math)
// rather than duplicating listeners between the header and each row.
// ===================================================================

@Composable
private fun OutletRow(
    label: String,
    outletPath: String,
    usage: DeviceUsage,
    maxOnDurationSeconds: Long?,
    modifier: Modifier = Modifier
) {
    val now = rememberTicker()
    val status = DeviceStatus.fromString(usage.state)

    val onForSeconds = remember(usage, now) {
        if (status == DeviceStatus.ON) ((now - usage.lastChangedAt) / 1000).coerceAtLeast(0) else 0L
    }

    // Same fire-hazard cutoff behaviour as a normal device, just scoped to this
    // one outlet's own path. The multiswitch's maxOnDurationSeconds field applies
    // uniformly to every outlet underneath it (there's no per-outlet override yet).
    var showSafetyAlert by remember(outletPath) { mutableStateOf(false) }
    var cutoffTripped by remember(outletPath, usage.lastChangedAt) { mutableStateOf(false) }

    LaunchedEffect(status, onForSeconds) {
        if (status == DeviceStatus.ON && maxOnDurationSeconds != null && maxOnDurationSeconds > 0
            && onForSeconds >= maxOnDurationSeconds && !cutoffTripped) {
            cutoffTripped = true
            toggleDeviceState(outletPath, false)
            showSafetyAlert = true
        }
    }

    if (showSafetyAlert) {
        AlertDialog(
            onDismissRequest = { showSafetyAlert = false },
            title = { Text("Safety cutoff triggered") },
            text = { Text("$label was automatically turned off after exceeding its maximum safe ON duration.") },
            confirmButton = { TextButton(onClick = { showSafetyAlert = false }) { Text("OK") } }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SmartHomeColors.IconCircleBackground, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = SmartHomeColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                text = if (status == DeviceStatus.ON) "On for ${formatDuration(onForSeconds)}" else "Off",
                color = SmartHomeColors.TextPrimary.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
        Switch(
            checked = status == DeviceStatus.ON,
            enabled = status.isControllable,
            onCheckedChange = { isChecked -> if (status.isControllable) toggleDeviceState(outletPath, isChecked) },
            colors = SwitchDefaults.colors(
                checkedTrackColor = SmartHomeColors.AccentTeal,
                checkedThumbColor = SmartHomeColors.TextPrimary,
                uncheckedTrackColor = SmartHomeColors.StatBoxBackground,
                uncheckedThumbColor = SmartHomeColors.TextSecondary
            )
        )
    }
}

// The full outlet list for a multiswitch, shown below its header card on the
// Device Details page.
@Composable
fun MultiswitchOutletsSection(
    linkedPath: String,
    outletCount: Int,
    outletLabels: List<String>?,
    outletUsages: List<DeviceUsage>,
    maxOnDurationSeconds: Long?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Outlets",
            color = SmartHomeColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        for (i in 1..outletCount) {
//            val label = outletLabels?.getOrNull(i - 1)?.takeIf { it.isNotBlank() } ?: "Outlet $i"
            val label = "Switch $i"
            OutletRow(
                label = label,
                outletPath = "$linkedPath/outlet_$i",
                usage = outletUsages.getOrElse(i - 1) {
                    DeviceUsage("off", System.currentTimeMillis(), currentDateString(), 0L)
                },
                maxOnDurationSeconds = maxOnDurationSeconds
            )
        }
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

    val pathParts = devicePath.split("/")
    val fullDevicePath = "$devicePath/$deviceId"

    val initialFloorName = virtualFloorNameForPath(fullDevicePath) ?: ALL_FLOORS
    val initialRoomName = virtualRoomNameForPath(fullDevicePath) ?: ALL_ROOMS

    var selectedFloor by remember(fullDevicePath) { mutableStateOf(initialFloorName) }
    var selectedRoom by remember(fullDevicePath) { mutableStateOf(initialRoomName) }
    var selectedCategory by remember(fullDevicePath) { mutableStateOf(ALL_CATEGORIES) }

    val matchingVirtual = virtualDevices.find { it.linkedPath == fullDevicePath }
    val cardCategory = matchingVirtual?.category ?: (pathParts.getOrNull(4) ?: "Lights")
    val cardName = matchingVirtual?.customName ?: deviceId
    val isMultiswitch = (matchingVirtual?.outletCount ?: 1) > 1

    val usage = rememberDeviceUsage(fullDevicePath)
    val now = rememberTicker()

    val currentStatus = DeviceStatus.fromString(usage.state)

    // Per-outlet usages, only fetched when this device actually is a multiswitch.
    // Shared between the header's aggregate stats and each OutletRow below so we
    // don't open two Firebase listeners per outlet.
    val outletUsages = if (isMultiswitch) {
        rememberOutletUsages(fullDevicePath, matchingVirtual!!.outletCount)
    } else emptyList()

    val liveOnSecondsToday = remember(usage, now) {
        val today = currentDateString()
        val base = if (usage.usageDate == today) usage.todayOnSeconds else 0L
        val extra = if (currentStatus == DeviceStatus.ON) {
            // Clip elapsed time to the start of today, in case the device has been
            // on since before midnight -- don't leak yesterday's seconds into today's total.
            val startOfToday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .apply { isLenient = false }
                .parse(today)?.time ?: usage.lastChangedAt
            val sinceMillis = maxOf(usage.lastChangedAt, startOfToday)
            ((now - sinceMillis) / 1000).coerceAtLeast(0)
        } else 0L
        base + extra
    }
    val onForSeconds = remember(usage, now) {
        if (currentStatus == DeviceStatus.ON) ((now - usage.lastChangedAt) / 1000).coerceAtLeast(0) else 0L
    }

    // Single-device safety cutoff (fires the parent path's own "state"). Only meaningful
    // for ordinary (non-multiswitch) devices -- a multiswitch's parent path has no real
    // state of its own, so this block is skipped entirely and each OutletRow inside
    // MultiswitchOutletsSection runs its own independent cutoff check instead.
    var showSafetyAlert by remember(fullDevicePath) { mutableStateOf(false) }
    var cutoffTripped by remember(fullDevicePath, usage.lastChangedAt) { mutableStateOf(false) }

    if (!isMultiswitch) {
        LaunchedEffect(currentStatus, onForSeconds) {
            val maxDuration = matchingVirtual?.maxOnDurationSeconds
            if (currentStatus == DeviceStatus.ON && maxDuration != null && maxDuration > 0
                && onForSeconds >= maxDuration && !cutoffTripped) {
                cutoffTripped = true
                toggleDeviceState(fullDevicePath, false)
                showSafetyAlert = true
            }
        }

        if (showSafetyAlert) {
            AlertDialog(
                onDismissRequest = { showSafetyAlert = false },
                title = { Text("Safety cutoff triggered") },
                text = { Text("$cardName was automatically turned off after exceeding its maximum safe ON duration.") },
                confirmButton = {
                    TextButton(onClick = { showSafetyAlert = false }) { Text("OK") }
                }
            )
        }
    }

    val wattage = matchingVirtual?.wattage ?: 0.0

    // Aggregate stats for a multiswitch header: how many outlets are on, and the
    // sum of today's usage across all of them (each outlet assumed to draw the
    // same wattage as the unit's own wattage field, since outlets don't have
    // individual wattage yet).
    val onOutletCount = if (isMultiswitch) outletUsages.count { DeviceStatus.fromString(it.state) == DeviceStatus.ON } else 0
    val aggregateTodayUsageKwh = if (isMultiswitch) {
        outletUsages.sumOf { u ->
            val today = currentDateString()
            val base = if (u.usageDate == today) u.todayOnSeconds else 0L
            val extra = if (DeviceStatus.fromString(u.state) == DeviceStatus.ON) {
                ((now - u.lastChangedAt) / 1000).coerceAtLeast(0)
            } else 0L
            (base + extra) / 3600.0 * (wattage / 1000.0)
        }
    } else 0.0

    val todayUsageKwh: String
    val onForLabel: String
    val headerStatus: DeviceStatus

    if (isMultiswitch) {
        headerStatus = if (onOutletCount > 0) DeviceStatus.ON else DeviceStatus.OFF
        todayUsageKwh = when {
            wattage <= 0 -> "—"
            aggregateTodayUsageKwh < 0.01 -> "%.1f Wh".format(aggregateTodayUsageKwh * 1000)
            else -> "%.2f kWh".format(aggregateTodayUsageKwh)
        }
        onForLabel = "$onOutletCount/${matchingVirtual!!.outletCount} ON"
    } else {
        headerStatus = currentStatus
        todayUsageKwh = when {
            wattage <= 0 -> "—"
            (liveOnSecondsToday / 3600.0 * (wattage / 1000.0)) < 0.01 -> "%.1f Wh".format((liveOnSecondsToday / 3600.0) * wattage)
            else -> "%.2f kWh".format((liveOnSecondsToday / 3600.0) * (wattage / 1000.0))
        }
        onForLabel = if (currentStatus == DeviceStatus.ON) formatDuration(onForSeconds) else "Off"
    }

    val context = LocalContext.current

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

    val selectedFloorPath = if (selectedFloor == ALL_FLOORS) null
    else AppData.virtualFloorList.find { it.customName == selectedFloor }?.linkedPath

    val selectedRoomPath = if (selectedRoom == ALL_ROOMS) null
    else AppData.virtualRoomList.find { it.customName == selectedRoom }?.linkedPath

    val connectedDevices = virtualDevices.filter { entry ->
        val matchesFloor = selectedFloorPath == null || entry.linkedPath.startsWith("$selectedFloorPath/")
        val matchesRoom = selectedRoomPath == null || entry.linkedPath.startsWith("$selectedRoomPath/")
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
            deviceStatus = headerStatus,
            onToggle = { isChecked ->
                if (!isMultiswitch && currentStatus.isControllable) {
                    toggleDeviceState(fullDevicePath, isChecked)
                }
            },
            todayUsageKwh = todayUsageKwh,
            onForLabel = onForLabel,
            wattage = wattage,
            maxOnDurationSeconds = matchingVirtual?.maxOnDurationSeconds,   // NEW
            onSaveEdits = { newName, newWattage, newMaxDurationSeconds: Long? ->    // CHANGED
                matchingVirtual?.let { mv ->
                    val idx = AppData.virtualDeviceList.indexOfFirst { it.id == mv.virtualId }
                    if (idx != -1) {
                        AppData.virtualDeviceList[idx] = AppData.virtualDeviceList[idx].copy(
                            customName = newName,
                            wattage = newWattage,
                            maxOnDurationSeconds = newMaxDurationSeconds   // NEW
                        )
                        VirtualStorage.save(context)
                    }
                }
            },
            showToggle = !isMultiswitch
        )

        if (isMultiswitch && matchingVirtual != null) {
            Spacer(modifier = Modifier.height(16.dp))
            MultiswitchOutletsSection(
                linkedPath = fullDevicePath,
                outletCount = matchingVirtual.outletCount,
                outletLabels = matchingVirtual.outletLabels,
                outletUsages = outletUsages,
                maxOnDurationSeconds = matchingVirtual.maxOnDurationSeconds
            )
        }

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