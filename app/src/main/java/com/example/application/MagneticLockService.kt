package com.example.application

import android.annotation.SuppressLint
import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MagneticLockService : Service(), SensorEventListener {
    private val TAG = "MagneticLockService"
    private lateinit var sensorManager: SensorManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private var lastMagnitude: Float = 0.0f
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    // 자력 값을 외부에서 접근할 수 있도록 StateFlow 추가
    private val _magneticFieldValue = MutableStateFlow(-1.0f)
    val magneticFieldValue: StateFlow<Float> = _magneticFieldValue

    companion object {
        private var instance: MagneticLockService? = null
        
        fun getInstance(): MagneticLockService? {
            return instance
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate - 서비스 시작됨")
        instance = this
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
        if (magneticSensor != null) {
            Log.d(TAG, "자력 센서 등록 성공 - 센서 이름: ${magneticSensor.name}")
            sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            Log.e(TAG, "자력 센서를 찾을 수 없음 - 센서 등록 실패")
        }

        createNotification()
        Log.d(TAG, "서비스 초기화 완료")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand - 서비스 시작 명령 수신 (startId: $startId)")
        // 서비스가 종료되면 자동으로 재시작
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Service onTaskRemoved - 앱이 종료됨, 서비스 재시작 시도")
        // 앱이 종료되어도 서비스 재시작
        val restartServiceIntent = Intent(applicationContext, MagneticLockService::class.java)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy - 서비스 종료됨, 재시작 시도")
        // 서비스가 종료되면 재시작
        val restartServiceIntent = Intent(applicationContext, MagneticLockService::class.java)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
    }

    @SuppressLint("ForegroundServiceType")
    private fun createNotification() {
        Log.d(TAG, "알림 채널 생성 시작")
        val channelId = "lock_service"
        val channel = NotificationChannel(channelId, "Magnetic Lock Service", NotificationManager.IMPORTANCE_LOW)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "알림 채널 생성 완료")

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("자력 감지 중")
            .setContentText("자력 잠금 서비스 실행 중")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)  // 사용자가 지우지 못하도록 설정
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        Log.d(TAG, "포그라운드 서비스 시작됨")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) return
        
        val magnitude = event.values.let {
            sqrt(it[0]*it[0] + it[1]*it[1] + it[2]*it[2])
        }

        // 자력 값을 StateFlow에 업데이트 (메인 스레드에서 실행)
        serviceScope.launch {
            _magneticFieldValue.value = magnitude
            Log.d(TAG, "자력 값: ${String.format("%.1f", magnitude)} μT (X: ${String.format("%.1f", event.values[0])}, Y: ${String.format("%.1f", event.values[1])}, Z: ${String.format("%.1f", event.values[2])})")
        }

        // 자력이 높으면 → 기기 잠금과 앱 잠금 분리
        if (magnitude > 220) {
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                Log.d(TAG, "자력 강함 (${String.format("%.1f", magnitude)} μT)")
                
                // 앱 잠금이 활성화되어 있는지 먼저 확인
                val isAppLockEnabled = SecuritySettings.isAppLockEnabled(this)
                Log.d(TAG, "앱 잠금 상태 확인: ${if (isAppLockEnabled) "활성화" else "비활성화"} (SharedPreferences에서 직접 확인)")
                
                // 앱 잠금이 활성화된 경우에만 앱 잠금 실행
                if (isAppLockEnabled) {
                    Log.d(TAG, "앱 잠금 실행 - 자력값: ${String.format("%.1f", magnitude)} μT")
                    lockApp()
                } else {
                    Log.d(TAG, "앱 잠금 비활성화 상태 - 앱 잠금 실행하지 않음")
                }
                
                // 기기 잠금은 기기 잠금 설정이 활성화되어 있을 때만 실행
                if (SecuritySettings.isDeviceLockEnabled(this)) {
                    Log.d(TAG, "기기 잠금 실행")
                    devicePolicyManager.lockNow()
                }
            } else {
                Log.w(TAG, "관리자 권한 없음 → 잠금 불가")
            }
        } else {
            Log.d(TAG, "자력 약함 (${String.format("%.1f", magnitude)} μT) → 잠금 유지 안 함")
        }

        // 자력 감소 시 화면 켜기
        if (lastMagnitude > 220 && magnitude <= 220) {
            Log.d(TAG, "자력 감소 감지 (${String.format("%.1f", lastMagnitude)} → ${String.format("%.1f", magnitude)} μT) → 화면 자동 켜기")
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "MagneticLockService::WakeLock"
            )
            wakeLock.acquire(3000) // 3초간 화면 켜기
        }

        lastMagnitude = magnitude
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "센서 정확도 변경: $accuracy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun lockApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("LOCK_APP", true)
        }
        startActivity(intent)
    }
}
