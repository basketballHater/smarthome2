package com.example.smarthome

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
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

// A plain data holder representing ONE device (a light, a camera, etc).
// It just stores values -- it has no idea Firebase exists.
data class Device(
    val id: String,      // e.g. "Lamp1"
    val type: String,    // e.g. "Light"
    val state: String,   // "on" or "off"
    val path: String      // where in Firebase to write updates, e.g. "Floors/F1/Rooms/Bedroom/Lights"
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setContent replaces setContentView(R.layout...) from the old system.
        // Everything inside these braces IS the screen -- no XML file involved.
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
    var selectedTab by remember{mutableStateOf(1)}
    Scaffold(
        bottomBar = {
            NavigationBar {
                // --- Cameras tab ---
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            Icons.Filled.Videocam,
                            contentDescription = "Cameras"
                        )
                    },
                    label = { Text("Cameras") }
                )
                // --- Devices tab ---
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Devices") },
                    label = { Text("Devices") }
                )
                // --- Configurations tab ---
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Configurations") },
                    label = { Text("Configurations") }
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
<<<<<<< Updated upstream
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    Text("Cameras page coming soon", modifier = Modifier.padding(16.dp))
                }
=======
                CameraScreen(
                    modifier = Modifier.padding(innerPadding)
                )
>>>>>>> Stashed changes
            }
            2 -> {
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    Text("Configurations page coming soon", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}


@Composable
fun SmartHomeScreen(modifier: Modifier = Modifier) {

    var deviceList by remember { mutableStateOf(mutableListOf<Device>()) }
    var isEmpty by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val devicesRef = FirebaseDatabase.getInstance().getReference("Floors")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    isEmpty = true
                    deviceList = mutableListOf()
                    return
                }
                isEmpty = false
                val newList = mutableListOf<Device>()

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
                                val path = "Floors/$floorId/Rooms/$roomId/$category"

                                newList.add(
                                    Device(id = deviceId, type = typeLabel, state = state, path = path)
                                )
                            }
                        }
                    }
                }
                deviceList = newList
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
            for (device in deviceList) {
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