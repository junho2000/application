package com.example.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.wifi.WifiNetworkSuggestion
import android.util.Log
import kotlinx.coroutines.delay
import android.os.Build
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.NetworkInfo
import android.os.Bundle

@Composable
fun WifiScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    var wifiNetworks by remember { mutableStateOf<List<WifiNetwork>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf<WifiNetwork?>(null) }
    var password by remember { mutableStateOf("") }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<ConnectionStatus?>(null) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // WiFi 연결 상태 모니터링
    val wifiReceiver = remember {
        WifiConnectionReceiver(
            onConnected = { ssid ->
                connectionStatus = ConnectionStatus.Connected(ssid)
            },
            onDisconnected = {
                connectionStatus = ConnectionStatus.Disconnected
            },
            onSuggestionConnectionStatus = { status ->
                when (status) {
                    0 -> {
                        Log.e("WifiScreen", "No network suggestions found")
                        showErrorDialog = "네트워크 연결에 실패했습니다."
                    }
                    else -> {
                        Log.d("WifiScreen", "Network suggestion status: $status")
                    }
                }
            }
        )
    }

    // BroadcastReceiver 등록
    LaunchedEffect(Unit) {
        val intentFilter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)
        }
        context.registerReceiver(wifiReceiver, intentFilter)
    }

    // 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // 모든 권한이 승인되면 WiFi 스캔 시작
            scanWifiNetworks(wifiManager) { networks ->
                wifiNetworks = networks
            }
        } else {
            // 권한이 거부되면 설정 화면으로 이동하도록 안내
            showPermissionDialog = true
        }
    }

    // WiFi 설정 화면으로 이동하는 런처
    val wifiSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                // 사용자가 네트워크를 저장함
                if (result.data != null && result.data!!.hasExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST)) {
                    val results = result.data!!.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST)
                    results?.forEach { code ->
                        when (code) {
                            Settings.ADD_WIFI_RESULT_SUCCESS -> {
                                Log.d("WifiScreen", "Network configuration saved successfully")
                            }
                            Settings.ADD_WIFI_RESULT_ADD_OR_UPDATE_FAILED -> {
                                Log.e("WifiScreen", "Failed to save network configuration")
                                showErrorDialog = "네트워크 설정 저장에 실패했습니다."
                            }
                            Settings.ADD_WIFI_RESULT_ALREADY_EXISTS -> {
                                Log.d("WifiScreen", "Network configuration already exists")
                            }
                        }
                    }
                }
            }
            Activity.RESULT_CANCELED -> {
                Log.d("WifiScreen", "User cancelled network configuration")
            }
        }
        // 설정 화면에서 돌아오면 다시 스캔
        scanWifiNetworks(wifiManager) { networks ->
            wifiNetworks = networks
        }
    }

    // 권한 확인 및 요청
    LaunchedEffect(Unit) {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        // Android 12 이상에서는 NEARBY_WIFI_DEVICES 권한 추가
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        val hasPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermissions) {
            // 권한이 있으면 WiFi 스캔 시작
            scanWifiNetworks(wifiManager) { networks ->
                wifiNetworks = networks
            }
        } else {
            // 권한이 없으면 요청
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    // WiFi 연결 시도 전 권한 확인 함수
    fun checkAndRequestPermissions(onSuccess: () -> Unit) {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onSuccess()
        } else {
            Log.e("WifiScreen", "Missing permissions: ${missingPermissions.joinToString()}")
            showErrorDialog = "WiFi 연결을 위해 필요한 권한이 없습니다. 설정에서 다음 권한을 허용해주세요:\n" +
                "- 위치 정보\n" +
                "- WiFi 상태 변경\n" +
                "- 근처 WiFi 기기 검색"
            showPermissionDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 상단 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "WiFi 네트워크",
                    style = MaterialTheme.typography.titleLarge
                )
                when (val status = connectionStatus) {
                    is ConnectionStatus.Connected -> {
                        Text(
                            text = "연결됨: ${status.ssid}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is ConnectionStatus.Connecting -> {
                        Text(
                            text = "연결 중...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    is ConnectionStatus.Disconnected -> {
                        Text(
                            text = "연결되지 않음",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    null -> {}
                }
            }
            IconButton(
                onClick = {
                    isScanning = true
                    scope.launch {
                        scanWifiNetworks(wifiManager) { networks ->
                            wifiNetworks = networks
                            isScanning = false
                        }
                    }
                },
                enabled = !isScanning
            ) {
                Text(if (isScanning) "..." else "↻")
            }
        }

        // WiFi 네트워크 목록
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(wifiNetworks) { network ->
                WifiNetworkItem(
                    network = network,
                    onClick = {
                        if (network.isSecured) {
                            showPasswordDialog = network
                        } else {
                            checkAndRequestPermissions {
                                scope.launch {
                                    try {
                                        // WiFi가 활성화되어 있는지 확인
                                        if (!wifiManager.isWifiEnabled) {
                                            Log.d("WifiScreen", "Enabling WiFi...")
                                            wifiManager.isWifiEnabled = true
                                            Thread.sleep(2000)
                                        }

                                        // 스캔 결과에서 네트워크 보안 정보 확인
                                        val scanResults = wifiManager.scanResults
                                        val targetNetwork = scanResults.find { it.SSID == network.ssid }
                                        
                                        if (targetNetwork == null) {
                                            throw Exception("네트워크를 찾을 수 없습니다.")
                                        }

                                        // 기존 제안 제거
                                        val suggestions = wifiManager.networkSuggestions
                                        if (suggestions.isNotEmpty()) {
                                            Log.d("WifiScreen", "Removing existing network suggestions")
                                            wifiManager.removeNetworkSuggestions(suggestions)
                                            Thread.sleep(1000)
                                        }

                                        // 네트워크 보안 유형 확인
                                        val capabilities = targetNetwork.capabilities
                                        val isWpa3Supported = capabilities.contains("SAE")
                                        val isWpa2Supported = capabilities.contains("WPA2-PSK") || capabilities.contains("WPA2")
                                        val isOpen = !capabilities.contains("WPA") && !capabilities.contains("WEP")

                                        // 새 네트워크 제안 추가
                                        val suggestion = when {
                                            isOpen -> {
                                                WifiNetworkSuggestion.Builder()
                                                    .setSsid(network.ssid)
                                                    .setIsAppInteractionRequired(true)
                                                    .setPriority(1)
                                                    .setIsMetered(false)
                                                    .setIsHiddenSsid(false)
                                                    .build()
                                            }
                                            else -> {
                                                throw Exception("보안된 네트워크입니다. 비밀번호를 입력해주세요.")
                                            }
                                        }

                                        Log.d("WifiScreen", "Adding new network suggestion for ${network.ssid}")
                                        val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
                                        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                                            throw Exception("네트워크 추가에 실패했습니다.")
                                        }

                                        // WiFi 스캔 시작
                                        wifiManager.startScan()

                                        // 연결 상태 업데이트
                                        connectionStatus = ConnectionStatus.Connecting

                                        // 연결 확인
                                        var attempts = 0
                                        while (attempts < 15) {
                                            val info = wifiManager.connectionInfo
                                            if (info != null && info.ssid == "\"${network.ssid}\"") {
                                                connectionStatus = ConnectionStatus.Connected(network.ssid)
                                                break
                                            }
                                            Thread.sleep(1000)
                                            attempts++
                                        }

                                        if (attempts >= 15) {
                                            throw Exception("연결 시간이 초과되었습니다.")
                                        }
                                    } catch (e: Exception) {
                                        showErrorDialog = "WiFi 연결 실패: ${e.message}"
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // 비밀번호 입력 다이얼로그
    showPasswordDialog?.let { network ->
        AlertDialog(
            onDismissRequest = { showPasswordDialog = null },
            title = { Text("WiFi 비밀번호") },
            text = {
                Column {
                    Text("${network.ssid}에 연결")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("비밀번호") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        checkAndRequestPermissions {
                            scope.launch {
                                try {
                                    // WiFi가 활성화되어 있는지 확인
                                    if (!wifiManager.isWifiEnabled) {
                                        Log.d("WifiScreen", "Enabling WiFi...")
                                        wifiManager.isWifiEnabled = true
                                        Thread.sleep(2000)
                                    }

                                    // 스캔 결과에서 네트워크 보안 정보 확인
                                    val scanResults = wifiManager.scanResults
                                    val targetNetwork = scanResults.find { it.SSID == network.ssid }
                                    
                                    if (targetNetwork == null) {
                                        throw Exception("네트워크를 찾을 수 없습니다.")
                                    }

                                    // 기존 제안 제거
                                    val suggestions = wifiManager.networkSuggestions
                                    if (suggestions.isNotEmpty()) {
                                        Log.d("WifiScreen", "Removing existing network suggestions")
                                        wifiManager.removeNetworkSuggestions(suggestions)
                                        Thread.sleep(1000)
                                    }

                                    // 네트워크 보안 유형 확인
                                    val capabilities = targetNetwork.capabilities
                                    val isWpa3Supported = capabilities.contains("SAE")
                                    val isWpa2Supported = capabilities.contains("WPA2-PSK") || capabilities.contains("WPA2")

                                    // 새 네트워크 제안 추가
                                    val suggestion = when {
                                        isWpa3Supported -> {
                                            WifiNetworkSuggestion.Builder()
                                                .setSsid(network.ssid)
                                                .setWpa3Passphrase(password)
                                                .setIsAppInteractionRequired(true)
                                                .setPriority(1)
                                                .setIsMetered(false)
                                                .setIsHiddenSsid(false)
                                                .build()
                                        }
                                        isWpa2Supported -> {
                                            WifiNetworkSuggestion.Builder()
                                                .setSsid(network.ssid)
                                                .setWpa2Passphrase(password)
                                                .setIsAppInteractionRequired(true)
                                                .setPriority(1)
                                                .setIsMetered(false)
                                                .setIsHiddenSsid(false)
                                                .build()
                                        }
                                        else -> {
                                            throw Exception("지원하지 않는 보안 유형입니다.")
                                        }
                                    }

                                    Log.d("WifiScreen", "Adding new network suggestion for ${network.ssid}")
                                    val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
                                    if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                                        throw Exception("네트워크 추가에 실패했습니다.")
                                    }

                                    // WiFi 스캔 시작
                                    wifiManager.startScan()

                                    // 연결 상태 업데이트
                                    connectionStatus = ConnectionStatus.Connecting

                                    // 연결 확인
                                    var attempts = 0
                                    while (attempts < 15) {
                                        val info = wifiManager.connectionInfo
                                        if (info != null && info.ssid == "\"${network.ssid}\"") {
                                            connectionStatus = ConnectionStatus.Connected(network.ssid)
                                            break
                                        }
                                        Thread.sleep(1000)
                                        attempts++
                                    }

                                    if (attempts >= 15) {
                                        throw Exception("연결 시간이 초과되었습니다.")
                                    }

                                    showPasswordDialog = null
                                    password = ""
                                } catch (e: Exception) {
                                    showErrorDialog = "WiFi 연결 실패: ${e.message}"
                                }
                            }
                        }
                    }
                ) {
                    Text("연결")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = null }) {
                    Text("취소")
                }
            }
        )
    }

    // 권한 거부 시 설정 화면으로 이동하도록 안내하는 다이얼로그
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("권한 필요") },
            text = { Text("WiFi 연결을 위해 필요한 권한이 없습니다. 설정에서 권한을 허용해주세요.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        wifiSettingsLauncher.launch(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                ) {
                    Text("설정으로 이동")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // 에러 다이얼로그
    showErrorDialog?.let { error ->
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text("연결 오류") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = { showErrorDialog = null }) {
                    Text("확인")
                }
            }
        )
    }
}

@Composable
fun WifiNetworkItem(
    network: WifiNetwork,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (network.isSecured) "보안됨" else "공개",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${network.signalStrength}%",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

data class WifiNetwork(
    val ssid: String,
    val isSecured: Boolean,
    val signalStrength: Int
)

private fun scanWifiNetworks(
    wifiManager: WifiManager,
    onResult: (List<WifiNetwork>) -> Unit
) {
    wifiManager.startScan()
    val results = wifiManager.scanResults
    val networks = results.map { result ->
        WifiNetwork(
            ssid = result.SSID,
            isSecured = result.capabilities.contains("WPA") || result.capabilities.contains("WEP"),
            signalStrength = WifiManager.calculateSignalLevel(result.level, 100)
        )
    }.distinctBy { it.ssid }
    onResult(networks)
}

sealed class ConnectionStatus {
    data class Connected(val ssid: String) : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Disconnected : ConnectionStatus()
}

private fun connectToWifi(
    context: Context,
    wifiManager: WifiManager,
    ssid: String,
    password: String?,
    onStatusUpdate: (ConnectionStatus) -> Unit
) {
    try {
        // 권한 확인
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.e("WifiScreen", "Missing permissions: ${missingPermissions.joinToString()}")
            throw Exception("WiFi 연결을 위해 필요한 권한이 없습니다.")
        }

        // 현재 연결된 네트워크 확인
        val currentConnection = wifiManager.connectionInfo
        if (currentConnection != null && currentConnection.ssid == "\"$ssid\"") {
            Log.d("WifiScreen", "Already connected to $ssid")
            onStatusUpdate(ConnectionStatus.Connected(ssid))
            return
        }

        // WiFi가 활성화되어 있는지 확인
        if (!wifiManager.isWifiEnabled) {
            Log.d("WifiScreen", "Enabling WiFi...")
            wifiManager.isWifiEnabled = true
            Thread.sleep(2000)
        }

        // 스캔 결과에서 네트워크 보안 정보 확인
        val scanResults = wifiManager.scanResults
        val targetNetwork = scanResults.find { it.SSID == ssid }
        
        if (targetNetwork == null) {
            Log.e("WifiScreen", "Target network not found in scan results")
            throw Exception("네트워크를 찾을 수 없습니다. 네트워크가 범위 내에 있는지 확인해주세요.")
        }

        // 기존 네트워크 설정 정리
        val configuredNetworks = wifiManager.configuredNetworks
        configuredNetworks?.forEach { network ->
            if (network.SSID == "\"$ssid\"") {
                Log.d("WifiScreen", "Removing existing network configuration for $ssid")
                wifiManager.removeNetwork(network.networkId)
                wifiManager.saveConfiguration()
            }
        }

        // 기존 제안 제거
        val suggestions = wifiManager.networkSuggestions
        if (suggestions.isNotEmpty()) {
            Log.d("WifiScreen", "Removing existing network suggestions")
            wifiManager.removeNetworkSuggestions(suggestions)
            Thread.sleep(1000)
        }

        // 네트워크 보안 유형 확인
        val capabilities = targetNetwork.capabilities
        val isWpa3Supported = capabilities.contains("SAE")
        val isWpa2Supported = capabilities.contains("WPA2-PSK") || capabilities.contains("WPA2")
        val isOpen = !capabilities.contains("WPA") && !capabilities.contains("WEP")

        // WifiNetworkSuggestion 생성
        val suggestion = when {
            password != null && password.isNotEmpty() -> {
                when {
                    isWpa3Supported -> {
                        WifiNetworkSuggestion.Builder()
                            .setSsid(ssid)
                            .setWpa3Passphrase(password)
                            .setIsAppInteractionRequired(true)
                            .setPriority(1)
                            .setIsMetered(false)
                            .setIsHiddenSsid(false)
                            .build()
                    }
                    isWpa2Supported -> {
                        WifiNetworkSuggestion.Builder()
                            .setSsid(ssid)
                            .setWpa2Passphrase(password)
                            .setIsAppInteractionRequired(true)
                            .setPriority(1)
                            .setIsMetered(false)
                            .setIsHiddenSsid(false)
                            .build()
                    }
                    else -> {
                        throw Exception("지원하지 않는 보안 유형입니다.")
                    }
                }
            }
            isOpen -> {
                WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .setIsAppInteractionRequired(true)
                    .setPriority(1)
                    .setIsMetered(false)
                    .setIsHiddenSsid(false)
                    .build()
            }
            else -> {
                throw Exception("비밀번호가 필요합니다.")
            }
        }

        // 새 네트워크 제안 추가
        Log.d("WifiScreen", "Adding new network suggestion for $ssid")
        val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            Log.e("WifiScreen", "Failed to add network suggestion")
            throw Exception("네트워크 추가에 실패했습니다. 권한을 확인해주세요.")
        }
        Log.d("WifiScreen", "Successfully added network suggestion")

        // WiFi 스캔 시작
        Log.d("WifiScreen", "Starting WiFi scan")
        wifiManager.startScan()
        Thread.sleep(1000)

        // 시스템이 자동으로 연결을 처리하도록 함
        Log.d("WifiScreen", "Letting system handle the connection")
        onStatusUpdate(ConnectionStatus.Connecting)

        // 연결 시도 후 일정 시간 동안 연결 상태 확인
        var attempts = 0
        while (attempts < 15) {
            val info = wifiManager.connectionInfo
            if (info != null && info.ssid == "\"$ssid\"") {
                Log.d("WifiScreen", "Successfully connected to $ssid")
                onStatusUpdate(ConnectionStatus.Connected(ssid))
                return
            }
            Thread.sleep(1000)
            attempts++
        }

        // 연결 시도 실패
        Log.e("WifiScreen", "Connection attempt timed out")
        onStatusUpdate(ConnectionStatus.Disconnected)
        throw Exception("네트워크 연결 시간이 초과되었습니다.")

    } catch (e: Exception) {
        Log.e("WifiScreen", "Error connecting to WiFi: ${e.message}")
        onStatusUpdate(ConnectionStatus.Disconnected)
        throw Exception("WiFi 연결에 실패했습니다: ${e.message}")
    }
}

// WiFi 연결 상태 모니터링을 위한 BroadcastReceiver
private class WifiConnectionReceiver(
    private val onConnected: (String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onSuggestionConnectionStatus: (Int) -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                val wifiInfo = intent.getParcelableExtra<WifiInfo>(WifiManager.EXTRA_WIFI_INFO)
                
                when (networkInfo?.state) {
                    NetworkInfo.State.CONNECTED -> {
                        wifiInfo?.ssid?.removeSurrounding("\"")?.let { ssid ->
                            onConnected(ssid)
                        }
                    }
                    NetworkInfo.State.DISCONNECTED -> {
                        onDisconnected()
                    }
                    else -> {}
                }
            }
            WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION -> {
                // 연결 후 처리
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val status = wifiManager.networkSuggestions?.size ?: 0
                onSuggestionConnectionStatus(status)
            }
        }
    }
} 