package com.example.licenseexpiry.ui

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.licenseexpiry.data.LicenseEntry
import com.example.licenseexpiry.data.Vehicle
import com.example.licenseexpiry.notifications.NotificationHelper
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: LicenseViewModel by viewModels()

    // Handles the Android 13+ notification permission popup result.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* granted or denied - notifications simply won't show if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)

        requestNotificationPermissionIfNeeded()
        requestExactAlarmPermissionIfNeeded()

        setContent {
            MaterialTheme {
                LicenseExpiryScreen(viewModel)
            }
        }
    }

    /** Android 13+ requires POST_NOTIFICATIONS to be granted at runtime. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Android 12+ gates exact alarms behind a system settings screen rather than
     * a popup dialog. Without this, AlarmManager.setExactAndAllowWhileIdle silently
     * falls back to an inexact alarm on some OEMs.
     */
    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
        }
    }
}

@Composable
fun LicenseExpiryScreen(viewModel: LicenseViewModel) {
    val vehicles by viewModel.vehicles.collectAsState()
    val licenses by viewModel.allLicenses.collectAsState()
    var showAddVehicle by remember { mutableStateOf(false) }
    var showAddLicenseFor by remember { mutableStateOf<Vehicle?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddVehicle = true }) { Text("+") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            items(vehicles) { vehicle ->
                VehicleCard(
                    vehicle = vehicle,
                    licenses = licenses.filter { it.vehicleId == vehicle.id },
                    onAddLicense = { showAddLicenseFor = vehicle },
                    onDeleteVehicle = { viewModel.deleteVehicle(vehicle) },
                    onDeleteLicense = { viewModel.deleteLicense(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showAddVehicle) {
        AddVehicleDialog(
            onDismiss = { showAddVehicle = false },
            onConfirm = { nickname, plate ->
                viewModel.addVehicle(nickname, plate)
                showAddVehicle = false
            }
        )
    }

    showAddLicenseFor?.let { vehicle ->
        AddLicenseDialog(
            vehicle = vehicle,
            onDismiss = { showAddLicenseFor = null },
            onConfirm = { licenseType, expiryMillis, reminderDays ->
                viewModel.addLicense(vehicle.id, vehicle.nickname, licenseType, expiryMillis, reminderDays)
                showAddLicenseFor = null
            }
        )
    }
}

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    licenses: List<LicenseEntry>,
    onAddLicense: () -> Unit,
    onDeleteVehicle: () -> Unit,
    onDeleteLicense: (LicenseEntry) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${vehicle.nickname} (${vehicle.plateNumber})", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDeleteVehicle) { Text("Remove") }
            }

            licenses.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${entry.licenseType}: ${dateFormat.format(Date(entry.expiryDateMillis))}")
                    TextButton(onClick = { onDeleteLicense(entry) }) { Text("Delete") }
                }
            }

            TextButton(onClick = onAddLicense, modifier = Modifier.padding(top = 8.dp)) {
                Text("+ Add license")
            }
        }
    }
}

@Composable
fun AddVehicleDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var nickname by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add vehicle") },
        text = {
            Column {
                OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Nickname") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text("Plate number") })
            }
        },
        confirmButton = {
            TextButton(onClick = { if (nickname.isNotBlank()) onConfirm(nickname, plate) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddLicenseDialog(
    vehicle: Vehicle,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Int) -> Unit
) {
    val context = LocalContext.current
    var licenseType by remember { mutableStateOf("Motor License") }
    var expiryMillis by remember { mutableStateOf<Long?>(null) }
    var reminderDays by remember { mutableStateOf("7") }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add license for ${vehicle.nickname}") },
        text = {
            Column {
                OutlinedTextField(
                    value = licenseType,
                    onValueChange = { licenseType = it },
                    label = { Text("License type") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val calendar = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val picked = Calendar.getInstance().apply {
                                set(year, month, day, 0, 0, 0)
                            }
                            expiryMillis = picked.timeInMillis
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Text(expiryMillis?.let { "Expiry: ${dateFormat.format(Date(it))}" } ?: "Pick expiry date")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reminderDays,
                    onValueChange = { reminderDays = it.filter { c -> c.isDigit() } },
                    label = { Text("Remind me (days before)") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val expiry = expiryMillis
                val days = reminderDays.toIntOrNull() ?: 7
                if (expiry != null) onConfirm(licenseType, expiry, days)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
