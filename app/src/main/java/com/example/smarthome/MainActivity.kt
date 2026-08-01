package com.example.smarthome

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
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
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


// A plain data holder representing ONE device (a light, a camera, etc).
// It just stores values -- it has no idea Firebase exists.
data class Device(
    val id: Int,      // e.g. "Lamp1"
    val type: String,    // e.g. "Light"
    val name: String,    // e.g. "Light"
    val state: String,   // "on" or "off"
    val path: String      // where in Firebase to write updates, e.g. "Floors/F1/Rooms/Bedroom/Lights"
)

data class Floor(
    val id: Int,      // e.g. "Lamp1"
    val name : String,    // e.g. "Light"
    val path: String      // where in Firebase to write updates, e.g. "Floors/F1/Rooms/Bedroom/Lights"
)
data class Room(
    val id: Int,      // e.g. "Lamp1"
    val name : String,    // e.g. "Light"
    val path: String      // where in Firebase to write updates, e.g. "Floors/F1/Rooms/Bedroom/Lights"
)

data class VirtualDevice(
    val id: String,          // you generate this, e.g. "vdev1"
    val customName: String,  // name the user picks
    val linkedPath: String?,  // path of the real Device it's mapped to, null = not mapped yet
    val wattage: Double
)

data class VirtualRoom(
    val id: String,
    val customName: String,
    val linkedPath: String?
)

data class VirtualFloor(
    val id: String,
    val customName: String,
    val linkedPath: String?
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setContent replaces setContentView(R.layout...) from the old system.
        // Everything inside these braces IS the screen -- no XML file involved.
        VirtualStorage.load(this)
        setContent {
            // MaterialTheme gives Text/Switch/etc their default Android look (colors, fonts).
            MaterialTheme {
                // Surface just draws a themed background behind everything.
                Surface {
                    // Our actual screen content lives in this function, defined below.
                    Main()
                }
            }
        }
    }
}

@Composable
fun Main(){
    var selectedTab by remember{mutableIntStateOf(1)}
    Scaffold(
        bottomBar = {
            NavigationBar {
                // --- Devices tab ---
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Devices") },
                    label = { Text("Devices") }
                )
                // --- Cameras tab ---
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Star, contentDescription = "Cameras") },
                    label = { Text("Cameras") }
                )
                // --- Navigation Tab ---
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Devices") },
                    label = { Text("Navigate") }
                )
                // --- Configurations tab ---
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Configurations") },
                    label = { Text("Config") }
                )
            }
        }
    ) { innerPadding ->
        // innerPadding is the space Scaffold tells us to leave so content isn't hidden by the bottom bar.

        // Only show real content when "Devices" (index 1) is selected.
        // Otherwise show an empty/placeholder screen.
        when (selectedTab) {
            1 -> {
                // Apply innerPadding here so the device list doesn't get covered by the bottom bar.
                SmartHomeScreen(modifier = Modifier.padding(innerPadding))
            }
            0 -> {
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    Text("Navigations page coming soon", modifier = Modifier.padding(16.dp))
                }
            }
            2 -> {
                Navigate(modifier = Modifier.padding(innerPadding))
            }
            3 -> {
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    Text("Configurations page coming soon", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

object AppData {
    var floorList = mutableStateListOf<Floor>()
    var roomList = mutableStateListOf<Room>()
    var deviceList = mutableStateListOf<Device>()

    var virtualFloorList = mutableStateListOf<VirtualFloor>()
    var virtualRoomList = mutableStateListOf<VirtualRoom>()
    var virtualDeviceList = mutableStateListOf<VirtualDevice>()
}

object VirtualStorage {
    private const val PREFS_NAME = "virtual_prefs"
    private val gson = Gson()

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("devices", gson.toJson(AppData.virtualDeviceList.toList()))
            .putString("rooms", gson.toJson(AppData.virtualRoomList.toList()))
            .putString("floors", gson.toJson(AppData.virtualFloorList.toList()))
            .apply()
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.getString("devices", null)?.let { json ->
            val type = object : TypeToken<List<VirtualDevice>>() {}.type
            AppData.virtualDeviceList.clear()
            AppData.virtualDeviceList.addAll(gson.fromJson(json, type))
        }
        prefs.getString("rooms", null)?.let { json ->
            val type = object : TypeToken<List<VirtualRoom>>() {}.type
            AppData.virtualRoomList.clear()
            AppData.virtualRoomList.addAll(gson.fromJson(json, type))
        }
        prefs.getString("floors", null)?.let { json ->
            val type = object : TypeToken<List<VirtualFloor>>() {}.type
            AppData.virtualFloorList.clear()
            AppData.virtualFloorList.addAll(gson.fromJson(json, type))
        }
    }
}

@Composable
fun SmartHomeScreen(modifier: Modifier = Modifier) {

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
                            Room(id = index, name = "$floorNum-$roomId",path = "Floors/$floorId/Rooms/$roomId"  )
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
}

// A single row: label on the left, switch on the right, for ONE device.
@Composable
fun DeviceRow(device: Device) {

    // Row stacks its children horizontally, left to right.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp) // small gap above/below each row
    ) {
        // The text label, e.g. "Light: Lamp1"
        // weight(1f) makes it stretch to fill leftover space, pushing the Switch to the right
        Text(
            text = "${device.type}: ${device.id}",
            modifier = Modifier.weight(1f)
        )

        // The on/off switch itself.
        Switch(
            // "checked" controls what the switch currently shows -- true if state is "on"
            checked = device.state == "on",

            // This code runs whenever the USER taps the switch
            onCheckedChange = { isChecked ->

                // Decide the new text value to store: "on" or "off"
                val newState = if (isChecked) "on" else "off"

                // Write it directly to Firebase at this device's exact path
                FirebaseDatabase.getInstance()
                    .getReference("${device.path}/${device.id}/state")
                    .setValue(newState)
                    .addOnFailureListener { e ->
                        Log.e(
                            "SMARTHOME",
                            "Failed to update ${device.path}/${device.id}/state",
                            e
                        )
                    }

                // Note: we do NOT manually update the switch's appearance here.
                // Firebase will notify our listener above, which rebuilds deviceList,
                // which makes Compose redraw this Switch with the new "checked" value automatically.
            }
        )
    }
}