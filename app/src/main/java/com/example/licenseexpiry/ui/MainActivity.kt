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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.licenseexpiry.data.AuthResult
import com.example.licenseexpiry.data.FitnessEntry
import com.example.licenseexpiry.data.LicenseEntry
import com.example.licenseexpiry.data.Vehicle
import com.example.licenseexpiry.notifications.NotificationHelper
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private val viewModel: LicenseViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)

        requestNotificationPermissionIfNeeded()
        requestExactAlarmPermissionIfNeeded()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2563EB),
                    background = Color(0xFFF8FAFC),
                    surface = Color.White
                )
            ) {
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

@Composable
fun AppRoot(viewModel: LicenseViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    var screen by remember { mutableStateOf<Screen>(if (isLoggedIn) Screen.Home else Screen.Login) }

    when (screen) {
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

// --- Helper Functions ---

private fun formatRelativeDays(expiryDateMillis: Long): String {
    val diffMillis = expiryDateMillis - System.currentTimeMillis()
    val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
    return when {
        days < 0 -> {
            val absDays = abs(days)
            if (absDays == 1L) "Expired 1 day ago" else "Expired $absDays days ago"
        }
        days == 0L -> "Expires today"
        days < 30 -> "In $days days"
        days < 365 -> {
            val months = days / 30
            if (months == 1L) "In 1 month" else "In $months months"
        }
        else -> {
            val years = days / 365
            if (years == 1L) "In 1 year" else "In $years years"
        }
    }
}

private fun relativeDaysColor(expiryDateMillis: Long): Color {
    val days = TimeUnit.MILLISECONDS.toDays(expiryDateMillis - System.currentTimeMillis())
    return when {
        days < 0 -> Color(0xFFDC2626) // Red
        days <= 30 -> Color(0xFFD97706) // Amber/Orange
        else -> Color(0xFF16A34A) // Green
    }
}

private fun getGreeting(userEmail: String?): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val period = when (hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val name = if (!userEmail.isNullOrBlank()) {
        userEmail.substringBefore('@').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    } else "Brandon"
    return "$period, $name!"
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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC)).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2563EB)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("LicenseTrack", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text("Drive Safe. Stay Compliant.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Log in to your account", style = MaterialTheme.typography.titleMedium, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(16.dp))

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

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    loading = true
                    viewModel.saveBackendUrl(backendUrl)
                    viewModel.login(email, password) { result ->
                        loading = false
                        when (result) {
                            is AuthResult.Success -> onLoggedIn()
                            is AuthResult.Failure -> error = result.message
                        }
                    }
                },
                enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (loading) "Logging in..." else "Log in")
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    viewModel.saveBackendUrl(backendUrl)
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
                "Local reminders still work - logging in unlocks cloud sync across devices.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B)
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
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC)).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Create an account", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
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

// --- Main Home Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseExpiryScreen(viewModel: LicenseViewModel, onOpenSettings: () -> Unit) {
    val vehicles by viewModel.vehicles.collectAsState()
    val licenses by viewModel.allLicenses.collectAsState()
    val fitnesses by viewModel.allFitness.collectAsState()
    var showAddVehicle by remember { mutableStateOf(false) }
    var showAddLicenseFor by remember { mutableStateOf<Vehicle?>(null) }
    var showAddFitnessFor by remember { mutableStateOf<Vehicle?>(null) }
    var editingLicense by remember { mutableStateOf<LicenseEntry?>(null) }
    var editingFitness by remember { mutableStateOf<FitnessEntry?>(null) }

    // Count expiring soon items (< 30 days)
    val now = System.currentTimeMillis()
    val thirtyDaysMillis = TimeUnit.DAYS.toMillis(30)
    val expiringSoonCount = (licenses.count { (it.expiryDateMillis - now) <= thirtyDaysMillis }
        + fitnesses.count { (it.expiryDateMillis - now) <= thirtyDaysMillis })

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddVehicle = true },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Vehicle", modifier = Modifier.size(28.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 1. Header with App Title & Settings Gear
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2563EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.DirectionsCar,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "LicenseTrack",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Drive Safe. Stay Compliant.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // 2. Greeting Section
            item {
                Text(
                    getGreeting(viewModel.appConfig.userEmail),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Here's an overview of your vehicles and upcoming license & fitness expirations.",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 3. Summary Stats Row (2 Cards)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Vehicles Summary Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFDCFCE7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Vehicles", fontSize = 13.sp, color = Color(0xFF475569))
                                    Text(
                                        "${vehicles.size}",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Expiring Soon Summary Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF3E8FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Build,
                                        contentDescription = null,
                                        tint = Color(0xFF9333EA),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Expiring Soon", fontSize = 13.sp, color = Color(0xFF475569))
                                    Text(
                                        "$expiringSoonCount",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 4. Section Title
            item {
                Text(
                    "My Vehicles",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 5. Vehicle Cards List or Empty State
            if (vehicles.isEmpty()) {
                item {
                    EmptyState()
                }
            } else {
                items(vehicles) { vehicle ->
                    VehicleCardItem(
                        vehicle = vehicle,
                        licenses = licenses.filter { it.vehicleId == vehicle.id },
                        fitnesses = fitnesses.filter { it.vehicleId == vehicle.id },
                        onAddLicense = { showAddLicenseFor = vehicle },
                        onEditLicense = { editingLicense = it },
                        onAddFitness = { showAddFitnessFor = vehicle },
                        onEditFitness = { editingFitness = it },
                        onDeleteVehicle = { viewModel.deleteVehicle(vehicle) }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }

    // --- Dialogs ---

    if (showAddVehicle) {
        AddVehicleDialog(
            onDismiss = { showAddVehicle = false },
            onConfirm = { brand, vehicleType, vehicleYear, plate ->
                viewModel.addVehicle(brand, vehicleType, vehicleYear, plate)
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
                viewModel.addLicense(vehicle.id, vehicle.brand, licenseType, expiryMillis, reminderDays)
                showAddLicenseFor = null
            }
        )
    }

    showAddFitnessFor?.let { vehicle ->
        FitnessDialog(
            vehicle = vehicle,
            existingEntry = null,
            onDismiss = { showAddFitnessFor = null },
            onConfirm = { expiryMillis, reminderDays ->
                viewModel.addFitness(vehicle.id, vehicle.brand, expiryMillis, reminderDays)
                showAddFitnessFor = null
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
                    viewModel.updateLicense(entry, vehicle.brand, licenseType, expiryMillis, reminderDays)
                    editingLicense = null
                }
            )
        }
    }

    editingFitness?.let { entry ->
        val vehicle = vehicles.firstOrNull { it.id == entry.vehicleId }
        if (vehicle != null) {
            FitnessDialog(
                vehicle = vehicle,
                existingEntry = entry,
                onDismiss = { editingFitness = null },
                onConfirm = { expiryMillis, reminderDays ->
                    viewModel.updateFitness(entry, vehicle.brand, expiryMillis, reminderDays)
                    editingFitness = null
                }
            )
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "No vehicles added yet",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Tap the + button to add your vehicle and track its license & fitness expirations.",
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

// --- Redesigned Vehicle Item Card ---

@Composable
fun VehicleCardItem(
    vehicle: Vehicle,
    licenses: List<LicenseEntry>,
    fitnesses: List<FitnessEntry>,
    onAddLicense: () -> Unit,
    onEditLicense: (LicenseEntry) -> Unit,
    onAddFitness: () -> Unit,
    onEditFitness: (FitnessEntry) -> Unit,
    onDeleteVehicle: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val latestLicense = licenses.firstOrNull()
    val latestFitness = fitnesses.firstOrNull()

    val now = System.currentTimeMillis()
    val thirtyDays = TimeUnit.DAYS.toMillis(30)
    val isExpiringSoon = (latestLicense != null && (latestLicense.expiryDateMillis - now) <= thirtyDays)
        || (latestFitness != null && (latestFitness.expiryDateMillis - now) <= thirtyDays)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Vehicle Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        vehicle.brand,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        vehicle.plateNumber,
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                }

                // Status Badge ("Active" vs "Expiring Soon")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isExpiringSoon) Color(0xFFFEF3C7) else Color(0xFFDCFCE7))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (isExpiringSoon) "Expiring Soon" else "Active",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isExpiringSoon) Color(0xFFD97706) else Color(0xFF15803D)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Content Row: Specs + License Box + Fitness Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Specs Column (Left)
                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.DirectionsCar,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(vehicle.brand, fontSize = 12.sp, color = Color(0xFF475569))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.TimeToLeave,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(vehicle.vehicleType, fontSize = 12.sp, color = Color(0xFF475569))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(vehicle.vehicleYear, fontSize = 12.sp, color = Color(0xFF475569))
                    }
                }

                // License Expiry Sub-Box (Middle)
                Card(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (latestLicense != null) onEditLicense(latestLicense) else onAddLicense()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F6FF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFDBEAFE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.AssignmentInd,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("License Expiry", fontSize = 11.sp, color = Color(0xFF64748B))
                        Text(
                            if (latestLicense != null) dateFormat.format(Date(latestLicense.expiryDateMillis)) else "+ Add",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        if (latestLicense != null) {
                            Text(
                                formatRelativeDays(latestLicense.expiryDateMillis),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = relativeDaysColor(latestLicense.expiryDateMillis)
                            )
                        }
                    }
                }

                // Fitness Expiry Sub-Box (Right)
                Card(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (latestFitness != null) onEditFitness(latestFitness) else onAddFitness()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F5FF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF3E8FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Build,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Fitness Expiry", fontSize = 11.sp, color = Color(0xFF64748B))
                        Text(
                            if (latestFitness != null) dateFormat.format(Date(latestFitness.expiryDateMillis)) else "+ Add",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        if (latestFitness != null) {
                            Text(
                                formatRelativeDays(latestFitness.expiryDateMillis),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = relativeDaysColor(latestFitness.expiryDateMillis)
                            )
                        }
                    }
                }

                // Chevron icon
                IconButton(
                    onClick = onDeleteVehicle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "Options",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

// --- Dialogs ---

@Composable
fun AddVehicleDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, String) -> Unit) {
    var brand by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("SUV") }
    var vehicleYear by remember { mutableStateOf("2024") }
    var plate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add vehicle") },
        text = {
            Column {
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Vehicle Make / Brand (e.g. Toyota)") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = vehicleType,
                    onValueChange = { vehicleType = it },
                    label = { Text("Vehicle Type (e.g. SUV, Sedan)") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = vehicleYear,
                    onValueChange = { vehicleYear = it },
                    label = { Text("Year (e.g. 2020)") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it },
                    label = { Text("Plate Number (e.g. JMD 1234)") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (brand.isNotBlank() && plate.isNotBlank()) onConfirm(brand, vehicleType, vehicleYear, plate) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

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
        title = { Text(if (isEditing) "Edit license" else "Add license for ${vehicle.brand}") },
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

@Composable
fun FitnessDialog(
    vehicle: Vehicle,
    existingEntry: FitnessEntry?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Int) -> Unit
) {
    val context = LocalContext.current
    var expiryMillis by remember { mutableStateOf(existingEntry?.expiryDateMillis) }
    var reminderDays by remember { mutableStateOf((existingEntry?.reminderDaysBefore ?: 7).toString()) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val isEditing = existingEntry != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit fitness" else "Add fitness for ${vehicle.brand}") },
        text = {
            Column {
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
                if (expiry != null) onConfirm(expiry, days)
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

            OutlinedButton(
                onClick = {
                    viewModel.logOut()
                    onLoggedOut()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log out")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Local vehicles and reminders stay on this device - logging out only stops backend sync.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B)
            )
        }
    }
}
