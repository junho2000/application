package com.example.application

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import android.app.ActivityManager
import android.content.Context
import android.util.Log

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isDeviceLockEnabled by remember { 
        mutableStateOf(SecuritySettings.isDeviceLockEnabled(context))
    }
    var isAppLockEnabled by remember { 
        mutableStateOf(SecuritySettings.isAppLockEnabled(context))
    }
    var magneticValue by remember { mutableStateOf(-1.0f) }
    val scope = rememberCoroutineScope()
    val isFirstLoad = remember { mutableStateOf(true) }
    
    // 자력 센서 설정
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
        
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
                    val magnitude = event.values.let {
                        sqrt(it[0]*it[0] + it[1]*it[1] + it[2]*it[2])
                    }
                    magneticValue = magnitude
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        if (magneticSensor != null) {
            sensorManager.registerListener(
                sensorListener,
                magneticSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        
        onDispose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back button and title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "설정",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // 자력 값 표시 (항상 표시)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "현재 자력 세기",
                    style = MaterialTheme.typography.titleSmall
                )
                if (magneticValue < 0) {
                    Text(
                        "센서 초기화 중...",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        String.format("%.1f μT", magneticValue),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (magneticValue > 220) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (magneticValue > 220) "잠금 상태" else "잠금 해제 상태",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (magneticValue > 220) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 자력 잠금 토글들
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("앱 잠금")
                    Switch(
                        checked = isAppLockEnabled,
                        onCheckedChange = { enabled ->
                            Log.d("SettingsScreen", "앱 잠금 상태 변경 시도: ${if (enabled) "활성화" else "비활성화"}")
                            SecuritySettings.setAppLockEnabled(context, enabled)
                            isAppLockEnabled = enabled
                            Log.d("SettingsScreen", "앱 잠금 상태 변경 완료: ${if (enabled) "활성화" else "비활성화"}")
                        }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("기기 잠금")
                    Switch(
                        checked = isDeviceLockEnabled,
                        onCheckedChange = { enabled ->
                            isDeviceLockEnabled = enabled
                            SecuritySettings.setDeviceLockEnabled(context, enabled)
                            val intent = Intent(context, MagneticLockService::class.java)
                            if (enabled) {
                                ContextCompat.startForegroundService(context, intent)
                            } else {
                                context.stopService(intent)
                            }
                        }
                    )
                }
            }
        }

        // Admin Permission Button
        AdminPermissionScreen(context)
    }
}
