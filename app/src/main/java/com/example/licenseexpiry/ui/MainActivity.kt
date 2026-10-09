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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.licenseexpiry.data.AuthResult
import com.example.licenseexpiry.data.LicenseEntry
import com.example.licenseexpiry.data.Vehicle
import com.example.licenseexpiry.notifications.NotificationHelper
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val viewModel: LicenseViewModel by viewModels()

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
                AppRoot(viewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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

private sealed class Screen {
    data object Login : Screen()
    data object Register : Screen()
    data object Home : Screen()
    data object Settings : Screen()
}

/**
 * Login/Register gate access to Home, but the app is still usable offline
 * without ever logging in - Room + local alarms work regardless. Logging in
 * only unlocks backend sync (email reminders), which is why there's no
 * "skip" needed here: Home is reachable, syncing just stays inert until
 * the user does log in (see LicenseViewModel/SyncRepository).
 */
@Composable
fun AppRoot(viewModel: LicenseViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    var screen by remember { mutableStateOf<Screen>(if (isLoggedIn) Screen.Home else Screen.Login) }

    when (val current = screen) {
        is Screen.Login -> LoginScreen(
            viewModel = viewModel,
            onLoggedIn = { screen = Screen.Home },
            onGoToRegister = { screen = Screen.Register },
            onContinueOffline = { screen = Screen.Home }
        )
        is Screen.Register -> RegisterScreen(
            viewModel = viewModel,
            onRegistered = { screen = Screen.Home },
            onGoToLogin = { screen = Screen.Login }
        )
        is Screen.Home -> LicenseExpiryScreen(
            viewModel = viewModel,
            onOpenSettings = { screen = Screen.Settings }
        )
        is Screen.Settings -> SettingsScreen(
            viewModel = viewModel,
            onDone = { screen = Screen.Home },
            onLoggedOut = { screen = Screen.Login }
        )
    }
}

// --- Auth screens ---

@Composable
fun LoginScreen(
    viewModel: LicenseViewModel,
    onLoggedIn: () -> Unit,
    onGoToRegister: () -> Unit,
    onContinueOffline: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var backendUrl by remember { mutableStateOf(viewModel.appConfig.backendBaseUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("License Expiry", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Log in to sync reminders by email across devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            //Spacer(modifier = Modifier.height(8.dp))
            /*OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it; error = null },
                label = { Text("Server URL") },
                supportingText = { Text("Local emulator: http://10.0.2.2:3000/") },
                modifier = Modifier.fillMaxWidth()
            )*/

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    loading = true
                    viewModel.saveBackendUrl(backendUrl) // must be set before the request is built
                    viewModel.login(email, password) { result ->
                        loading = false
                        when (result) {
                            is AuthResult.Success -> onLoggedIn()
                            is AuthResult.Failure -> error = result.message
                        }
                    }
                },
                enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Logging in..." else "Log in")
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    viewModel.saveBackendUrl(backendUrl) // Register screen reuses the saved URL
                    onGoToRegister()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Don't have an account? Register")
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onContinueOffline, modifier = Modifier.fillMaxWidth()) {
                Text("Continue without an account")
            }
            Text(
                "Local reminders still work - you just won't get email alerts or sync across devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RegisterScreen(
    viewModel: LicenseViewModel,
    onRegistered: () -> Unit,
    onGoToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Create an account", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("Password (min 8 characters)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null },
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (password.length < 8) {
                        error = "Password must be at least 8 characters"
                        return@Button
                    }
                    if (password != confirmPassword) {
                        error = "Passwords don't match"
                        return@Button
                    }
                    loading = true
                    viewModel.register(email, password) { result ->
                        loading = false
                        when (result) {
                            is AuthResult.Success -> onRegistered()
                            is AuthResult.Failure -> error = result.message
                        }
                    }
                },
                enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Creating account..." else "Register")
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onGoToLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Already have an account? Log in")
            }
        }
    }
}

// --- Expiry status coloring ---

/** Red if overdue or due within 7 days, amber within 30, default color otherwise. */
@Composable
private fun expiryStatusColor(expiryDateMillis: Long): Color {
    val daysUntil = TimeUnit.MILLISECONDS.toDays(expiryDateMillis - System.currentTimeMillis())
    return when {
        daysUntil < 7 -> MaterialTheme.colorScheme.error
        daysUntil < 30 -> Color(0xFFB8860B) // amber - no direct Material3 role for "warning"
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseExpiryScreen(viewModel: LicenseViewModel, onOpenSettings: () -> Unit) {
    val vehicles by viewModel.vehicles.collectAsState()
    val licenses by viewModel.allLicenses.collectAsState()
    var showAddVehicle by remember { mutableStateOf(false) }
    var showAddLicenseFor by remember { mutableStateOf<Vehicle?>(null) }
    var editingLicense by remember { mutableStateOf<LicenseEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("License Expiry") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddVehicle = true }) { Text("+") }
        }
    ) { padding ->
        if (vehicles.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
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
                        onEditLicense = { editingLicense = it },
                        onDeleteVehicle = { viewModel.deleteVehicle(vehicle) },
                        onDeleteLicense = { viewModel.deleteLicense(it) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
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
        LicenseDialog(
            vehicle = vehicle,
            existingEntry = null,
            onDismiss = { showAddLicenseFor = null },
            onConfirm = { licenseType, expiryMillis, reminderDays ->
                viewModel.addLicense(vehicle.id, vehicle.nickname, licenseType, expiryMillis, reminderDays)
                showAddLicenseFor = null
            }
        )
    }

    editingLicense?.let { entry ->
        val vehicle = vehicles.firstOrNull { it.id == entry.vehicleId }
        if (vehicle != null) {
            LicenseDialog(
                vehicle = vehicle,
                existingEntry = entry,
                onDismiss = { editingLicense = null },
                onConfirm = { licenseType, expiryMillis, reminderDays ->
                    viewModel.updateLicense(entry, vehicle.nickname, licenseType, expiryMillis, reminderDays)
                    editingLicense = null
                }
            )
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No vehicles yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Tap + to add your first vehicle and track its license expiry.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    licenses: List<LicenseEntry>,
    onAddLicense: () -> Unit,
    onEditLicense: (LicenseEntry) -> Unit,
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

            if (licenses.isEmpty()) {
                Text(
                    "No licenses tracked yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            licenses.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${entry.licenseType}: ${dateFormat.format(Date(entry.expiryDateMillis))}",
                        color = expiryStatusColor(entry.expiryDateMillis),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onEditLicense(entry) }) { Text("Edit") }
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

/**
 * Shared dialog for both adding and editing a license.
 * existingEntry == null -> "add" mode; non-null -> "edit" mode, prefilled with its values.
 */
@Composable
fun LicenseDialog(
    vehicle: Vehicle,
    existingEntry: LicenseEntry?,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Int) -> Unit
) {
    val context = LocalContext.current
    var licenseType by remember { mutableStateOf(existingEntry?.licenseType ?: "Motor License") }
    var expiryMillis by remember { mutableStateOf(existingEntry?.expiryDateMillis) }
    var reminderDays by remember { mutableStateOf((existingEntry?.reminderDaysBefore ?: 7).toString()) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val isEditing = existingEntry != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit license" else "Add license for ${vehicle.nickname}") },
        text = {
            Column {
                OutlinedTextField(
                    value = licenseType,
                    onValueChange = { licenseType = it },
                    label = { Text("License type") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val calendar = Calendar.getInstance().apply {
                        expiryMillis?.let { timeInMillis = it }
                    }
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
            }) { Text(if (isEditing) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --- Settings screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: LicenseViewModel, onDone: () -> Unit, onLoggedOut: () -> Unit) {
    val appConfig = viewModel.appConfig
    var backendUrl by remember { mutableStateOf(appConfig.backendBaseUrl) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            appConfig.userEmail?.let { email ->
                Text("Logged in as $email", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                "Backend URL is only needed for email reminders. Local alarm " +
                    "reminders work regardless of this setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it; saved = false },
                label = { Text("Backend URL") },
                placeholder = { Text("https://your-backend.example.com/") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.saveBackendUrl(backendUrl)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
            if (saved) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = {
                    viewModel.logOut()
                    onLoggedOut()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log out")
            }
            Text(
                "Local vehicles and reminders stay on this device - logging out only stops backend sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
