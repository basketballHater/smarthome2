package com.example.smarthome

import android.os.Bundle
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Minimal working slice: one switch, synced live with Firebase Realtime Database.
 *
 * Data path used: switches/switch_01/state  -> "ON" | "OFF" | "ERROR" | "DISCONNECTED"
 *
 * Pattern to copy for every future device type:
 *   1. Get a DatabaseReference to the device's path.
 *   2. Attach a ValueEventListener -> update UI whenever the DB changes (from
 *      this app, the simulator, or a Cloud Function).
 *   3. On user interaction -> write the new value to that same path.
 *
 * That's the entire "bidirectional sync" mechanism for the whole project.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var switchRef: DatabaseReference
    private lateinit var toggle: Switch
    private lateinit var statusText: TextView

    // Guard flag so that when we update the UI from a DB event, we don't
    // immediately re-trigger a write back to the DB (which would be a no-op
    // but is good practice to avoid feedback loops once logic gets complex).
    private var updatingFromServer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggle = findViewById(R.id.switchToggle)
        statusText = findViewById(R.id.statusText)

        // Reference to a single device's state node.
        // Later: this becomes floors/{floorId}/devices/{deviceId}/state
        switchRef = FirebaseDatabase.getInstance()
            .getReference("switches/switch_01/state")

        // 1) LISTEN: any change in the DB (from this app, the web simulator,
        // or later a Cloud Function forcing a safety shutoff) updates the UI
        // automatically, with no manual refresh.
        switchRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(String::class.java) ?: "OFF"
                updatingFromServer = true
                when (state) {
                    "ON" -> {
                        toggle.isChecked = true
                        statusText.text = "Status: ON"
                    }
                    "OFF" -> {
                        toggle.isChecked = false
                        statusText.text = "Status: OFF"
                    }
                    "ERROR" -> {
                        toggle.isEnabled = false
                        statusText.text = "Status: ERROR"
                    }
                    "DISCONNECTED" -> {
                        toggle.isEnabled = false
                        statusText.text = "Status: DISCONNECTED"
                    }
                }
                updatingFromServer = false
            }

            override fun onCancelled(error: DatabaseError) {
                statusText.text = "Sync error: ${error.message}"
            }
        })

        // 2) WRITE: user taps the switch -> push new state to the DB.
        // The web simulator (and this app on another device) will update
        // instantly because they're listening to the same path.
        toggle.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            if (updatingFromServer) return@setOnCheckedChangeListener
            val newState = if (isChecked) "ON" else "OFF"
            switchRef.setValue(newState)
        }
    }
}
