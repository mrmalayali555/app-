package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.AlertEntity
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import com.example.service.AlertMonitorService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted: Boolean -> }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    setContent {
      MyApplicationTheme {
        MainScreen(
          onPickRingtone = { launcher ->
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
              putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
              putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            }
            launcher.launch(intent)
          }
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onPickRingtone: (androidx.activity.result.ActivityResultLauncher<Intent>) -> Unit) {
  val context = LocalContext.current
  val settingsRepo = remember { SettingsRepository(context) }
  val db = remember { AppDatabase.getDatabase(context) }
  val scope = rememberCoroutineScope()

  val topic by settingsRepo.topicNameFlow.collectAsState(initial = "")
  val isTtsEnabled by settingsRepo.ttsEnabledFlow.collectAsState(initial = true)
  val ringtoneUri by settingsRepo.ringtoneUriFlow.collectAsState(initial = null)
  val isMonitoring by settingsRepo.isMonitoringFlow.collectAsState(initial = false)
  val alerts by db.alertDao().getRecentAlerts().collectAsState(initial = emptyList())

  val ringtoneLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
      scope.launch { settingsRepo.saveRingtoneUri(uri?.toString()) }
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { 
            Column {
                Text("Slot Sentinel", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(if (isMonitoring) "MONITORING ACTIVE" else "MONITORING PAUSED", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.5.sp
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onBackground
        )
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      
      OutlinedTextField(
        value = topic,
        onValueChange = { scope.launch { settingsRepo.saveTopicName(it) } },
        label = { Text("Secret Ntfy Topic Name") },
        supportingText = { Text("e.g. j_slot_abcd. Must match Python script.") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
      )

      Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
      ) {
         Box(modifier = Modifier
             .fillMaxWidth()
             .background(
                 brush = Brush.linearGradient(
                     colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                 )
             )
         ) {
             Column(modifier = Modifier.padding(20.dp)) {
                 Row(
                     modifier = Modifier.fillMaxWidth(),
                     horizontalArrangement = Arrangement.SpaceBetween,
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     Surface(
                         color = MaterialTheme.colorScheme.primaryContainer,
                         shape = RoundedCornerShape(percent = 50)
                     ) {
                         Text(
                             "REAL-TIME HUB", 
                             modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                             style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                             color = MaterialTheme.colorScheme.onPrimaryContainer
                         )
                     }
                     Text("v1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                 }
                 
                 Spacer(modifier = Modifier.height(16.dp))
                 
                 Text("Alert Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                 Spacer(modifier = Modifier.height(8.dp))
                 
                 Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Read alerts aloud (TTS)", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                    Switch(
                      checked = isTtsEnabled,
                      onCheckedChange = { scope.launch { settingsRepo.saveTtsEnabled(it) } }
                    )
                 }
                 
                 Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.fillMaxWidth()
                 ) {
                    Text("Custom Alarm Sound", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                    TextButton(onClick = { onPickRingtone(ringtoneLauncher) }) {
                      Text(if (ringtoneUri != null) "Change" else "Pick Sound")
                    }
                 }
                 
                 Spacer(modifier = Modifier.height(16.dp))
                 
                 Button(
                    onClick = {
                      val intent = Intent(context, AlertMonitorService::class.java)
                      if (isMonitoring) {
                        intent.action = AlertMonitorService.ACTION_STOP
                        context.stopService(intent)
                      } else {
                        ContextCompat.startForegroundService(context, intent)
                      }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = if (isMonitoring) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                      contentColor = if (isMonitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
                    )
                 ) {
                    Icon(if (isMonitoring) Icons.Default.Settings else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isMonitoring) "Stop Monitoring" else "Start Monitoring", style = MaterialTheme.typography.titleMedium)
                 }
             }
         }
      }

      Text("Signal Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp))

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(alerts) { alert ->
          AlertItemCard(alert)
        }
        
        if (alerts.isEmpty()) {
          item {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
              Text("No signals received yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }
  }
}

@Composable
fun AlertItemCard(alert: AlertEntity) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("🔔", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            Text(alert.title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(alert.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
            Text(format.format(Date(alert.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
             Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
             Text("LIVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        }
    }
  }
}
