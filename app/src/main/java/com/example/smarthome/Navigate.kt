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

@Composable
fun Navigate(modifier: Modifier = Modifier) {
    var selectedFloor by remember { mutableIntStateOf(0) }
    var selectedRoom by remember { mutableIntStateOf(0) }
    var selectedDevice by remember { mutableIntStateOf(0) }
    var objectType by remember { mutableIntStateOf(-1) }
    var showAddDialog by remember { mutableStateOf(false) }
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
                    .background(color = Color(0xFF9AA0A8), shape = RoundedCornerShape(16.dp))
                    .padding(5.dp)

            ) {
                items(AppData.virtualFloorList.size) { index ->

                    val floor = AppData.virtualFloorList[index]

                    Column(
                        modifier = Modifier.padding(2.dp)
                            .width(80.dp)
                            .clickable {
                                selectedFloor = index
                            }
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

            Text(
                text = "Room Overview",
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
                items(AppData.virtualRoomList.size) { index ->

                    val floor = AppData.virtualRoomList[index]

                    Column(
                        modifier = Modifier.padding(2.dp)
                            .width(80.dp)
                            .clickable {
                                selectedRoom = index
                            }
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

                    val floor = AppData.virtualDeviceList[index]

                    Column(
                        modifier = Modifier.padding(2.dp)
                            .width(80.dp)
                            .clickable {
                                selectedDevice = index
                            }
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
    }



    if (showAddDialog) {
        val context = LocalContext.current
//            AddVirtualDeviceScreen(context = context, onDone = { showAddDialog = false })
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                AddVirtualObjectScreen(context = context,objectType ,onDone = { showAddDialog = false })
            }
        }
    }


}



@Composable
fun AddVirtualObjectScreen(context: Context, objectType: Int ,onDone: () -> Unit) {
    var customName by remember { mutableStateOf("") }
    var wattage by remember { mutableDoubleStateOf(0.0) }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var selectedRoom by remember { mutableStateOf<Room?>(null) }
    var selectedFloor by remember { mutableStateOf<Floor?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
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
            1 -> {
                Button(
                    onClick = {
                        if (customName.isNotBlank() && selectedRoom != null) {
                            AppData.virtualRoomList.add(
                                VirtualRoom(
                                    id = "vdev${AppData.virtualRoomList.size + 1}",
                                    customName = customName,
                                    linkedPath = selectedRoom!!.path
                                )
                            )
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
    onRoomSelected: (Room) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val alreadyMappedPaths = AppData.virtualRoomList.map { it.linkedPath }
    val availableRooms = AppData.roomList.filter { it.path !in alreadyMappedPaths }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedRoom?.name?.toString() ?: "Select a floor")
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