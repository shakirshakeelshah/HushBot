package com.example.hushbot

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.hushbot.ui.theme.HushbotTheme
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.*

private const val GEOFENCE_PREFS = "geofence_prefs"
private const val GEOFENCE_KEY = "geofence_list"


class MainActivity : ComponentActivity() {
   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      setContent {
         HushbotTheme {
            Surface(
               modifier = Modifier.fillMaxSize(),
               color = MaterialTheme.colorScheme.background
            ) {
               MainScreen()
            }
         }
      }
   }
}

fun saveGeofences(context: Context, geofences: List<GeofenceData>) {
   val prefs = context.getSharedPreferences(GEOFENCE_PREFS, Context.MODE_PRIVATE)
   val json = Gson().toJson(geofences)
   prefs.edit().putString(GEOFENCE_KEY, json).apply()
}

fun loadGeofences(context: Context): List<GeofenceData> {
   val prefs = context.getSharedPreferences(GEOFENCE_PREFS, Context.MODE_PRIVATE)
   val json = prefs.getString(GEOFENCE_KEY, null)
   val type = object : TypeToken<List<GeofenceData>>() {}.type
   return if (json != null) Gson().fromJson(json, type) else emptyList()
}

fun addGeofence(context: Context, client: GeofencingClient, data: GeofenceData) {
   if (!data.enabled) return

   val geofence = Geofence.Builder()
      .setRequestId(data.name)
      .setCircularRegion(data.latitude, data.longitude, data.radius)
      .setExpirationDuration(Geofence.NEVER_EXPIRE)
      .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
      .build()

   val request = GeofencingRequest.Builder()
      .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
      .addGeofence(geofence)
      .build()

   val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
   val pendingIntent = PendingIntent.getBroadcast(
      context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
   )

   if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      Toast.makeText(context, "Location permission not granted", Toast.LENGTH_SHORT).show()
      return
   }

   client.addGeofences(request, pendingIntent)
      .addOnSuccessListener {
         Toast.makeText(context, "Geofence '${data.name}' added", Toast.LENGTH_SHORT).show()
      }
      .addOnFailureListener {
         Toast.makeText(context, "Failed to add geofence: ${it.message}", Toast.LENGTH_SHORT).show()
      }
}

fun removeGeofence(context: Context, client: GeofencingClient, requestId: String) {
   client.removeGeofences(listOf(requestId))
      .addOnSuccessListener {
         Toast.makeText(context, "Geofence '$requestId' removed from system", Toast.LENGTH_SHORT).show()
      }
      .addOnFailureListener {
         Toast.makeText(context, "Failed to remove geofence: ${it.message}", Toast.LENGTH_SHORT).show()
      }
}

// Helper function to calculate distance between two points
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
   val earthRadius = 6371000f // Earth's radius in meters
   val dLat = Math.toRadians(lat2 - lat1)
   val dLon = Math.toRadians(lon2 - lon1)
   val a = sin(dLat / 2) * sin(dLat / 2) +
           cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
           sin(dLon / 2) * sin(dLon / 2)
   val c = 2 * atan2(sqrt(a), sqrt(1 - a))
   return (earthRadius * c).toFloat()
}

@Composable
fun MainScreen() {
   val context = LocalContext.current
   val geofencingClient = remember { LocationServices.getGeofencingClient(context) }
   val mockLocationHelper = remember { MockLocationHelper(context) }

   var currentLocation by remember { mutableStateOf<Location?>(null) }
   val geofences =
      remember { mutableStateListOf<GeofenceData>().apply { addAll(loadGeofences(context)) } }
   var showDialog by remember { mutableStateOf(false) }
   var mockLocation by remember { mutableStateOf<Location?>(null) }
   var showMockWarning by remember { mutableStateOf(false) }

   val permissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
   ) { permissions ->
      val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
      if (!granted) {
         Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
      }
   }

   val mockPermissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
   ) { /* We don't need to handle the result */ }

   LaunchedEffect(Unit) {
      if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
         ) == PackageManager.PERMISSION_GRANTED
      ) {
         val client = LocationServices.getFusedLocationProviderClient(context)
         client.lastLocation.addOnSuccessListener { location ->
            currentLocation = location
         }
      } else {
         permissionLauncher.launch(
            arrayOf(
               Manifest.permission.ACCESS_FINE_LOCATION,
               Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
         )
      }

      // Re-register all saved geofences
      geofences.forEach {
         if (it.enabled) {
            addGeofence(context, geofencingClient, it)
         }
      }

      // Check if mock location is enabled
      try {
         val isMockEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ALLOW_MOCK_LOCATION
         ) != 0

         if (!isMockEnabled) {
            showMockWarning = true
         }
      } catch (e: Settings.SettingNotFoundException) {
         showMockWarning = true
      }
   }

   Scaffold(
      floatingActionButton = {
         FloatingActionButton(
            onClick = {
               if (currentLocation != null) {
                  showDialog = true
               } else {
                  Toast.makeText(context, "Current location unavailable", Toast.LENGTH_SHORT).show()
               }
            },
            containerColor = MaterialTheme.colorScheme.primary
         ) {
            Icon(Icons.Default.Add, contentDescription = "Add Geofence")
         }
      }
   ) { padding ->
      Column(
         modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
      ) {
         // Mock location warning
         if (showMockWarning) {
            AlertDialog(
               onDismissRequest = { showMockWarning = false },
               title = { Text("Mock Location Required") },
               text = {
                  Text("Please enable mock locations in Developer Options to use testing features")
               },
               confirmButton = {
                  TextButton(
                     onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        mockPermissionLauncher.launch(intent)
                        showMockWarning = false
                     }
                  ) {
                     Text("Open Settings")
                  }
               },
               dismissButton = {
                  TextButton(
                     onClick = { showMockWarning = false }
                  ) {
                     Text("Cancel")
                  }
               }
            )
         }

         // Current Location Card
         Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
               containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
         ) {
            Column(
               modifier = Modifier.padding(16.dp),
               horizontalAlignment = Alignment.Start
            ) {
               Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth()
               ) {
                  Icon(
                     Icons.Default.LocationOn,
                     contentDescription = "Location",
                     tint = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.padding(end = 8.dp)
                  )
                  Text(
                     "Current Location",
                     fontWeight = FontWeight.Bold,
                     style = MaterialTheme.typography.titleMedium,
                     color = MaterialTheme.colorScheme.onPrimaryContainer
                  )
               }
               Spacer(modifier = Modifier.height(8.dp))

               val displayLocation = mockLocation ?: currentLocation
               Text(
                  "Lat: ${displayLocation?.latitude?.let { "%.6f".format(it) } ?: "Unknown"}",
                  color = MaterialTheme.colorScheme.onPrimaryContainer
               )
               Text(
                  "Lng: ${displayLocation?.longitude?.let { "%.6f".format(it) } ?: "Unknown"}",
                  color = MaterialTheme.colorScheme.onPrimaryContainer
               )

               if (mockLocation != null) {
                  Text(
                     "⚠️ Using Mock Location",
                     color = MaterialTheme.colorScheme.error,
                     style = MaterialTheme.typography.bodySmall,
                     fontWeight = FontWeight.Bold
                  )
               }
            }
         }

         Spacer(modifier = Modifier.height(16.dp))

         // Geofence Status Card
         if (geofences.isNotEmpty() && (currentLocation != null || mockLocation != null)) {
            Card(
               modifier = Modifier.fillMaxWidth(),
               colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer
               ),
               elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
               Column(modifier = Modifier.padding(16.dp)) {
                  Text(
                     "Geofence Status",
                     fontWeight = FontWeight.Bold,
                     style = MaterialTheme.typography.titleMedium,
                     color = MaterialTheme.colorScheme.onSecondaryContainer
                  )
                  Spacer(modifier = Modifier.height(8.dp))

                  val location = mockLocation ?: currentLocation
                  geofences.forEach { geofence ->
                     val distance = if (location != null) {
                        calculateDistance(
                           location.latitude, location.longitude,
                           geofence.latitude, geofence.longitude
                        )
                     } else null

                     val isInside = distance != null && distance <= geofence.radius

                     Row(
                        modifier = Modifier
                           .fillMaxWidth()
                           .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                     ) {
                        Text(
                           geofence.name,
                           color = MaterialTheme.colorScheme.onSecondaryContainer,
                           modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                           if (distance != null) {
                              Text(
                                 "${distance.toInt()}m",
                                 color = MaterialTheme.colorScheme.onSecondaryContainer,
                                 style = MaterialTheme.typography.bodySmall
                              )
                              Spacer(modifier = Modifier.width(8.dp))
                           }
                           Card(
                              colors = CardDefaults.cardColors(
                                 containerColor = if (!geofence.enabled) Color.Gray else if (isInside) Color(
                                    0xFF4CAF50
                                 ) else Color(0xFFFF5722)
                              )
                           ) {
                              Text(
                                 if (!geofence.enabled) "DISABLED" else if (isInside) "INSIDE" else "OUTSIDE",
                                 modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                 color = Color.White,
                                 style = MaterialTheme.typography.bodySmall,
                                 fontWeight = FontWeight.Bold
                              )
                           }
                        }
                     }
                  }
               }
            }
            Spacer(modifier = Modifier.height(16.dp))
         }

         // Testing Controls Section
         if (geofences.isNotEmpty()) {
            Card(
               modifier = Modifier.fillMaxWidth(),
               colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant
               ),
               elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
               Column(modifier = Modifier.padding(16.dp)) {
                  Text(
                     "Testing Controls",
                     fontWeight = FontWeight.Bold,
                     style = MaterialTheme.typography.titleMedium,
                     color = MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(modifier = Modifier.height(8.dp))

                  geofences.forEach { geofence ->
                     Row(
                        modifier = Modifier
                           .fillMaxWidth()
                           .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                     ) {
                        Text(
                           geofence.name,
                           modifier = Modifier.weight(1f),
                           color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                           modifier = Modifier.width(IntrinsicSize.Min),
                           horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                           Button(
                              onClick = {
                                 try {
                                    mockLocationHelper.enableMockLocation()
                                    mockLocationHelper.setMockLocation(
                                       geofence.latitude,
                                       geofence.longitude
                                    )
                                    mockLocation = Location("mock").apply {
                                       latitude = geofence.latitude
                                       longitude = geofence.longitude
                                    }
                                 } catch (e: SecurityException) {
                                    Toast.makeText(
                                       context,
                                       "Mock location permission denied",
                                       Toast.LENGTH_SHORT
                                    ).show()
                                 }
                              },
                              modifier = Modifier.weight(1f),
                              colors = ButtonDefaults.buttonColors(
                                 containerColor = MaterialTheme.colorScheme.primaryContainer
                              ),
                              enabled = geofence.enabled
                           ) {
                              Text("Inside", color = MaterialTheme.colorScheme.onPrimaryContainer)
                           }
                           Button(
                              onClick = {
                                 try {
                                    // Calculate point outside geofence (2x radius north)
                                    val distance = (geofence.radius * 2 + 10).toDouble()
                                    val delta = distance / 111000.0 // Approx meters per degree
                                    val outsideLat = geofence.latitude + delta

                                    mockLocationHelper.enableMockLocation()
                                    mockLocationHelper.setMockLocation(
                                       outsideLat,
                                       geofence.longitude
                                    )
                                    mockLocation = Location("mock").apply {
                                       latitude = outsideLat
                                       longitude = geofence.longitude
                                    }
                                 } catch (e: SecurityException) {
                                    Toast.makeText(
                                       context,
                                       "Mock location permission denied",
                                       Toast.LENGTH_SHORT
                                    ).show()
                                 }
                              },
                              modifier = Modifier.weight(1f),
                              colors = ButtonDefaults.buttonColors(
                                 containerColor = MaterialTheme.colorScheme.secondaryContainer
                              ),
                              enabled = geofence.enabled
                           ) {
                              Text(
                                 "Outside",
                                 color = MaterialTheme.colorScheme.onSecondaryContainer
                              )
                           }
                        }
                     }
                  }

                  Spacer(modifier = Modifier.height(8.dp))

                  Button(
                     onClick = {
                        mockLocationHelper.disableMockLocation()
                        mockLocation = null
                     },
                     modifier = Modifier.fillMaxWidth(),
                     colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                     )
                  ) {
                     Text("Clear Mock Location")
                  }
               }
            }
            Spacer(modifier = Modifier.height(16.dp))
         }

         Text(
            "Saved Geofences:",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
         )
         Spacer(modifier = Modifier.height(8.dp))

         if (geofences.isEmpty()) {
            Card(
               modifier = Modifier.fillMaxWidth(),
               colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant
               )
            ) {
               Text(
                  "No geofences created yet. Tap the + button to add one!",
                  modifier = Modifier.padding(16.dp),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.bodyMedium
               )
            }
         } else {
            geofences.forEach { data ->
               Card(
                  modifier = Modifier
                     .fillMaxWidth()
                     .padding(vertical = 4.dp),
                  colors = CardDefaults.cardColors(
                     containerColor = MaterialTheme.colorScheme.surface
                  ),
                  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
               ) {
                  Row(
                     modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                     verticalAlignment = Alignment.Top, // Changed to Top alignment
                     horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                     Column(modifier = Modifier.weight(1f)) {
                        Text(
                           text = data.name,
                           fontWeight = FontWeight.Bold,
                           style = MaterialTheme.typography.titleMedium,
                           color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                           text = "Lat: ${"%.6f".format(data.latitude)}, Lng: ${"%.6f".format(data.longitude)}",
                           style = MaterialTheme.typography.bodyMedium,
                           color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                           text = "Radius: ${data.radius.toInt()}m",
                           style = MaterialTheme.typography.bodyMedium,
                           color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                     }

                     // Right-aligned column for switch and delete button
                     Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                     ) {
                        Switch(
                           checked = data.enabled,
                           onCheckedChange = { isEnabled ->
                              val index = geofences.indexOfFirst { it.name == data.name }
                              if (index != -1) {
                                 geofences[index] = data.copy(enabled = isEnabled)
                                 saveGeofences(context, geofences)

                                 if (isEnabled) {
                                    addGeofence(
                                       context,
                                       geofencingClient,
                                       data.copy(enabled = true)
                                    )
                                 } else {
                                    removeGeofence(context, geofencingClient, data.name)
                                 }
                              }
                           }
                        )

                        IconButton(
                           onClick = {
                              val index = geofences.indexOfFirst { it.name == data.name }
                              if (index != -1) {
                                 removeGeofence(context, geofencingClient, data.name)
                                 geofences.removeAt(index)
                                 saveGeofences(context, geofences)
                                 Toast.makeText(
                                    context,
                                    "Geofence '${data.name}' removed",
                                    Toast.LENGTH_SHORT
                                 ).show()
                              }
                           }
                        ) {
                           Icon(
                              Icons.Default.Delete,
                              contentDescription = "Delete",
                              tint = MaterialTheme.colorScheme.error
                           )
                        }
                     }
                  }
               }
            }
         }


         // Add Geofence Dialog
         if (showDialog) {
            var dialogName by remember { mutableStateOf("") }
            var dialogLat by remember {
               mutableStateOf(
                  currentLocation?.latitude?.toString() ?: ""
               )
            }
            var dialogLng by remember {
               mutableStateOf(
                  currentLocation?.longitude?.toString() ?: ""
               )
            }
            var dialogRadius by remember { mutableStateOf("50") }

            AlertDialog(
               onDismissRequest = { showDialog = false },
               confirmButton = {
                  TextButton(onClick = {
                     if (dialogName.isNotBlank() && dialogLat.isNotBlank() && dialogLng.isNotBlank()) {
                        val newGeofence = GeofenceData(
                           dialogName,
                           dialogLat.toDoubleOrNull() ?: 0.0,
                           dialogLng.toDoubleOrNull() ?: 0.0,
                           dialogRadius.toFloatOrNull() ?: 50f,
                           true
                        )
                        geofences.add(newGeofence)
                        saveGeofences(context, geofences)
                        addGeofence(context, geofencingClient, newGeofence)
                        showDialog = false
                     } else {
                        Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                     }
                  }) {
                     Text("Add")
                  }
               },
               dismissButton = {
                  TextButton(onClick = { showDialog = false }) {
                     Text("Cancel")
                  }
               },
               title = { Text("Add Geofence") },
               text = {
                  Column {
                     OutlinedTextField(
                        value = dialogName,
                        onValueChange = { dialogName = it },
                        label = { Text("Location Name") },
                        modifier = Modifier.fillMaxWidth()
                     )
                     Spacer(modifier = Modifier.height(8.dp))
                     OutlinedTextField(
                        value = dialogLat,
                        onValueChange = { dialogLat = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.fillMaxWidth()
                     )
                     Spacer(modifier = Modifier.height(8.dp))
                     OutlinedTextField(
                        value = dialogLng,
                        onValueChange = { dialogLng = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.fillMaxWidth()
                     )
                     Spacer(modifier = Modifier.height(8.dp))
                     OutlinedTextField(
                        value = dialogRadius,
                        onValueChange = { dialogRadius = it },
                        label = { Text("Radius (m)") },
                        modifier = Modifier.fillMaxWidth()
                     )
                  }
               }
            )
         }
      }
   }
}