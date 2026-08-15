package com.example.smarthome

import android.util.Log
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ===================================================================
// DATA: per-device usage read straight from "{linkedPath}/History" plus
// the live "today" fields already used elsewhere (Devicedetails.kt).
// History is a map of dateString -> secondsOnThatDay, written by the
// updated toggleDeviceState() transaction below.
// ===================================================================

enum class ReportRange(val label: String, val days: Int) {
    DAY("Day", 1),
    WEEK("Week", 7),
    MONTH("Month", 30)
}

data class DeviceUsageHistory(
    val entry: DeviceEntry,
    val history: Map<String, Long>,   // date -> seconds on that day
    val todayUsage: DeviceUsage       // live value, same shape used in DeviceDetailScreen
)

@Composable
fun rememberAllDeviceUsageHistory(entries: List<DeviceEntry>): List<DeviceUsageHistory> {
    var results by remember { mutableStateOf<Map<String, Map<String, Long>>>(emptyMap()) }

    DisposableEffect(entries.map { it.linkedPath }) {
        val listeners = mutableListOf<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()

        entries.forEach { entry ->
            val ref = FirebaseDatabase.getInstance().getReference("${entry.linkedPath}/History")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val map = snapshot.children.mapNotNull { child ->
                        val date = child.key ?: return@mapNotNull null
                        val seconds = child.getValue(Long::class.java) ?: return@mapNotNull null
                        date to seconds
                    }.toMap()
                    results = results + (entry.linkedPath to map)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("USAGE_REPORTS", "Failed to read history for ${entry.linkedPath}", error.toException())
                }
            }
            ref.addValueEventListener(listener)
            listeners.add(ref to listener)
        }

        onDispose {
            listeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        }
    }

    return entries.map { entry ->
        DeviceUsageHistory(
            entry = entry,
            history = results[entry.linkedPath] ?: emptyMap(),
            todayUsage = rememberDeviceUsageForReport(entry.linkedPath)
        )
    }
}

// Lightweight variant of rememberDeviceUsage (Devicedetails.kt) so this file
// doesn't need to duplicate its DisposableEffect logic differently -- same shape.
@Composable
private fun rememberDeviceUsageForReport(linkedPath: String): DeviceUsage {
    return rememberDeviceUsage(linkedPath)
}

private fun dateNDaysAgo(n: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -n)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

/** Seconds-on for a device across [days] days ending today (inclusive of today's live value). */
private fun DeviceUsageHistory.secondsForRange(days: Int): Long {
    val today = currentDateString()
    var total = 0L
    for (i in 0 until days) {
        val date = dateNDaysAgo(i)
        total += if (date == today) {
            // today isn't archived into History yet -- use the live counter
            if (todayUsage.usageDate == today) todayUsage.todayOnSeconds else 0L
        } else {
            history[date] ?: 0L
        }
    }
    return total
}

/** Same as secondsForRange but for the PRECEDING period of equal length (for the efficiency score). */
private fun DeviceUsageHistory.secondsForPreviousRange(days: Int): Long {
    var total = 0L
    for (i in days until days * 2) {
        total += history[dateNDaysAgo(i)] ?: 0L
    }
    return total
}

private fun kwhFor(seconds: Long, wattage: Double): Double =
    (seconds / 3600.0) * (wattage / 1000.0)

// ===================================================================
// UI
// ===================================================================

@Composable
fun UsageReportsScreen(modifier: Modifier = Modifier) {
    val entries = rememberVirtualDeviceEntries()
    val usageHistory = rememberAllDeviceUsageHistory(entries)
    var range by remember { mutableStateOf(ReportRange.DAY) }

    val currentSeconds = usageHistory.associate { it.entry.virtualId to it.secondsForRange(range.days) }
    val previousSeconds = usageHistory.associate { it.entry.virtualId to it.secondsForPreviousRange(range.days) }

    val currentKwh = usageHistory.sumOf { kwhFor(currentSeconds[it.entry.virtualId] ?: 0L, it.entry.wattage) }
    val previousKwh = usageHistory.sumOf { kwhFor(previousSeconds[it.entry.virtualId] ?: 0L, it.entry.wattage) }

    val percentChange = if (previousKwh > 0.0) ((currentKwh - previousKwh) / previousKwh) * 100.0 else 0.0
    val efficiencyScore = (100.0 - percentChange).coerceIn(0.0, 100.0).toInt()
    val improved = percentChange < 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SmartHomeColors.BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Usage Reports",
            color = SmartHomeColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Deep insights into your home's performance.",
            color = SmartHomeColors.TextPrimary.copy(alpha = 0.75f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        RangeToggle(selected = range, onSelected = { range = it })

        Spacer(modifier = Modifier.height(16.dp))

        EfficiencyScoreCard(
            score = efficiencyScore,
            improved = improved,
            percentChange = kotlin.math.abs(percentChange)
        )

        Spacer(modifier = Modifier.height(16.dp))

        ActiveDurationCard(
            usageHistory = usageHistory,
            range = range,
            currentSeconds = currentSeconds
        )

        Spacer(modifier = Modifier.height(16.dp))

        EnergyConsumptionCard(
            usageHistory = usageHistory,
            currentSeconds = currentSeconds
        )
    }
}

@Composable
private fun RangeToggle(
    selected: ReportRange,
    onSelected: (ReportRange) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SmartHomeColors.IconCircleBackground, RoundedCornerShape(24.dp))
            .padding(4.dp)
    ) {
        ReportRange.entries.forEach { r ->
            val isSelected = r == selected
            Text(
                text = r.label,
                color = if (isSelected) SmartHomeColors.TextSecondary else SmartHomeColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (isSelected) SmartHomeColors.TextPrimary else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelected(r) }
                    .padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun EfficiencyScoreCard(
    score: Int,
    improved: Boolean,
    percentChange: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEDEBFB))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Efficiency Score",
                    color = SmartHomeColors.TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$score",
                        color = SmartHomeColors.AccentTeal,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "%",
                        color = SmartHomeColors.AccentTeal,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                }
                Text(
                    text = if (percentChange < 0.5) {
                        "Your usage is about the same as last period."
                    } else if (improved) {
                        "Your home is performing ${"%.0f".format(percentChange)}% better than last period. Great job!"
                    } else {
                        "Your home used ${"%.0f".format(percentChange)}% more than last period."
                    },
                    color = SmartHomeColors.TextLight,
                    fontSize = 13.sp
                )
            }
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(Color.Transparent, CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = SmartHomeColors.AccentTeal,
                        startAngle = -90f,
                        sweepAngle = 360f * (score / 100f),
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx())
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Spa,
                    contentDescription = "Efficiency",
                    tint = SmartHomeColors.AccentTeal
                )
            }
        }
    }
}

@Composable
private fun ActiveDurationCard(
    usageHistory: List<DeviceUsageHistory>,
    range: ReportRange,
    currentSeconds: Map<String, Long>
) {
    val ranked = usageHistory
        .map { it.entry to (currentSeconds[it.entry.virtualId] ?: 0L) }
        .sortedByDescending { it.second }
        .take(5)
    val maxSeconds = (ranked.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SmartHomeColors.CardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Duration",
                    color = SmartHomeColors.TextSecondary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Filled.History, contentDescription = null, tint = SmartHomeColors.TextLight)
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (ranked.isEmpty()) {
                Text("No device activity for this ${range.label.lowercase()}.", color = SmartHomeColors.TextLight, fontSize = 13.sp)
            }

            ranked.forEach { (entry, seconds) ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(entry.customName, color = SmartHomeColors.TextSecondary, fontSize = 13.sp)
                    Text(formatDuration(seconds), color = SmartHomeColors.TextLight, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(SmartHomeColors.StatBoxBackground, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (seconds.toFloat() / maxSeconds.toFloat()).coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .background(SmartHomeColors.AccentTeal, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

private val ChartBarColors = listOf(
    Color(0xFF1E5E6E), Color(0xFF85ABB5), Color(0xFF9575CD),
    Color(0xFF4CAF50), Color(0xFFFFC107), Color(0xFFEF5350)
)

@Composable
private fun EnergyConsumptionCard(
    usageHistory: List<DeviceUsageHistory>,
    currentSeconds: Map<String, Long>
) {
    val bars = usageHistory
        .map { it.entry to kwhFor(currentSeconds[it.entry.virtualId] ?: 0L, it.entry.wattage) }
        .filter { it.second > 0.0 }
        .sortedByDescending { it.second }
        .take(6)
    val maxKwh = (bars.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(0.01)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SmartHomeColors.IconCircleBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "REAL-TIME ANALYTICS",
                color = SmartHomeColors.AccentTeal,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Energy Consumption",
                color = SmartHomeColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Legend
            Row(modifier = Modifier.fillMaxWidth()) {
                bars.forEachIndexed { i, (entry, _) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp, bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(ChartBarColors[i % ChartBarColors.size], CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(entry.customName, color = SmartHomeColors.TextPrimary.copy(alpha = 0.8f), fontSize = 10.sp)
                    }
                }
            }

            if (bars.isEmpty()) {
                Text("No energy data yet for this period.", color = SmartHomeColors.TextPrimary.copy(alpha = 0.7f), fontSize = 13.sp)
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val barWidth = size.width / (bars.size * 2f)
                    bars.forEachIndexed { i, (_, kwh) ->
                        val barHeight = (kwh / maxKwh).toFloat() * size.height
                        val x = i * (barWidth * 2) + barWidth / 2
                        drawRoundRect(
                            color = ChartBarColors[i % ChartBarColors.size],
                            topLeft = Offset(x, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }
            }
        }
    }
}