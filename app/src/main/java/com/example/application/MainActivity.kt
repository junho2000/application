// UI는 jetpack compose로 만들어야 함
// 여기서 UI 다 만들면 됨? -> 파일 분할해서
// 틀을 만들어서 어떻게 스크린 넘어갈지 설정

// 궁금한 점
// 1. 백엔드 어디에 정의? com.example.application class 만들어서 정의
// 2. 프론트엔드 백엔드 어떻게 연결? viewmodel로 연결하기
// 3. viewmodel?
// 4. 스크린 하나에서 다른 스크린으로 어떻게 넘어감? 어디에 정의?
// 조건에 따라 어떤 composeable를 띄울지로 정의 navigation
// drawable  사진 넣는 곳 나머지 res 건들 x
// Gradle Scripts 앱 빌드할 때 (import 할 때) build.gradle.kts (module: app) 사용

// backend 만들고 기본적인 ui 만들어서 테스트하고 ui 디테일 들어가기?

package com.example.application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.application.ui.theme.ApplicationTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toFile
import java.io.FileOutputStream
import java.io.InputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import androidx.compose.foundation.layout.height
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    private val repository = PhotoRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 배경화면 끝까지 확장

        // setContent 블록에서 scope 선언
        setContent {
            var showWifiScreen by remember { mutableStateOf(false) }
            
            if (showWifiScreen) {
                WifiScreen(
                    onBack = { showWifiScreen = false }
                )
            } else {
                val photoList = remember { mutableStateListOf<PhotoItem>() }
                var isLoading by remember { mutableStateOf(true) }
                var isError by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                
                // 스토리지 정보 상태
                var storageText by remember { mutableStateOf("-") }
                suspend fun updateStorageInfo() {
                    try {
                        val info = repository.fetchStorageInfo()
                        if (info != null) {
                            val (used, total) = info
                            storageText = "${formatBytes(used)} / ${formatBytes(total)}"
                        } else {
                            storageText = "스토리지 정보 없음"
                        }
                    } catch (e: Exception) {
                        storageText = "스토리지 정보 없음"
                    }
                }
                
                // 선택 모드 관련 상태
                var isSelectionMode by remember { mutableStateOf(false) }
                val selectedPhotos = remember { mutableStateListOf<PhotoItem>() }
                
                // 삭제 확인 다이얼로그
                var showDeleteConfirmation by remember { mutableStateOf(false) }
                
                if (showDeleteConfirmation && selectedPhotos.isNotEmpty()) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmation = false },
                        title = { Text("사진 삭제") },
                        text = { Text("선택한 ${selectedPhotos.size}개의 사진을 삭제하시겠습니까?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        selectedPhotos.forEach { photo ->
                                            val success = repository.deletePhotoAndThumbnail(photo.filename)
                                            if (success) {
                                                photoList.removeIf { it.filename == photo.filename }
                                            }
                                        }
                                        selectedPhotos.clear()
                                        isSelectionMode = false
                                        isLoading = false
                                        updateStorageInfo()
                                    }
                                    showDeleteConfirmation = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("삭제")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showDeleteConfirmation = false }) {
                                Text("취소")
                            }
                        }
                    )
                }

                // pickImagesLauncher를 setContent 블록 안에서 선언
                val context = LocalContext.current
                val pickImagesLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetMultipleContents()
                ) { uris: List<Uri> ->
                    if (uris.isNotEmpty()) {
                        scope.launch {
                            isLoading = true
                            isError = false
                            try {
                                for (uri in uris) {
                                    val filePath = getFilePathFromUri(context, uri)
                                    if (filePath != null) {
                                        val success = repository.uploadPhotoAndThumbnail(filePath, context)
                                        if (success) {
                                            val fetchSuccess = repository.fetchPhotoListFromServer()
                                            if (fetchSuccess) {
                                                val newList = repository.getPhotoList()
                                                photoList.clear()
                                                photoList.addAll(newList)
                                            } else {
                                                throw Exception("목록 갱신 실패")
                                            }
                                        } else {
                                            throw Exception("업로드 실패")
                                        }
                                        updateStorageInfo()
                                    }
                                }
                            } catch (e: Exception) {
                                isError = true // 네트워크 실패 시 에러 화면으로 전환
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }

                // 앱 최초 실행 시 바로 리스트업(사진 목록 fetch)
                LaunchedEffect(Unit) {
                    isLoading = true
                    isError = false
                    try {
                        val success = repository.fetchPhotoListFromServer()
                        if (success) {
                            photoList.clear()
                            photoList.addAll(repository.getPhotoList())
                            isError = false
                        } else {
                            isError = true
                        }
                        updateStorageInfo()
                    } catch (e: Exception) {
                        isError = true
                    } finally {
                        isLoading = false
                    }
                }

                // 네트워크 복구 시 자동 새로고침
                ObserveServerState(
                    repository = repository,
                    onServerDisconnected = {
                        isError = true
                    },
                    onServerReconnected = {
                        scope.launch {
                            isLoading = true
                            isError = false
                            try {
                                val success = repository.fetchPhotoListFromServer()
                                if (success) {
                                    photoList.clear()
                                    photoList.addAll(repository.getPhotoList())
                                    isError = false
                                } else {
                                    isError = true
                                }
                                updateStorageInfo()
                            } catch (e: Exception) {
                                isError = true
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    checkIntervalMs = 1000L // 1초마다 체크
                )

                // 가운데 정렬을 위한 Column 사용
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "저장공간: $storageText",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 버튼들을 가로로 배치
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { pickImagesLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("사진 업로드")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = { showWifiScreen = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("WiFi 설정")
                        }
                    }

                    // 선택 모드 버튼을 별도의 Row로 분리
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                    ) {
                        if (isSelectionMode) {
                            Button(
                                onClick = {
                                    if (selectedPhotos.isNotEmpty()) {
                                        showDeleteConfirmation = true
                                    }
                                },
                                enabled = selectedPhotos.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("선택 삭제 (${selectedPhotos.size})")
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Button(
                                onClick = {
                                    isSelectionMode = false
                                    selectedPhotos.clear()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("선택 취소")
                            }
                        } else {
                            Button(
                                onClick = { isSelectionMode = true },
                                enabled = photoList.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("사진 선택")
                            }
                        }
                    }

                    // 리스트는 버튼 아래에 배치
                    PhotoListScreen(
                        photoList = photoList,
                        isLoading = isLoading,
                        isError = isError,
                        isSelectionMode = isSelectionMode,
                        selectedPhotos = selectedPhotos,
                        onPhotoSelect = { photo, isSelected ->
                            if (isSelected) {
                                selectedPhotos.add(photo)
                            } else {
                                selectedPhotos.remove(photo)
                            }
                        },
                        onRetry = {
                            scope.launch {
                                isLoading = true
                                isError = false
                                try {
                                    val success = repository.fetchPhotoListFromServer()
                                    if (success) {
                                        photoList.clear()
                                        photoList.addAll(repository.getPhotoList())
                                        isError = false
                                    } else {
                                        isError = true
                                    }
                                    updateStorageInfo()
                                } catch (e: Exception) {
                                    isError = true
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // context를 파라미터로 받도록 변경
    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        val returnCursor = context.contentResolver.query(uri, null, null, null, null)
        val nameIndex = returnCursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
        returnCursor?.moveToFirst()
        val name = if (nameIndex >= 0) returnCursor?.getString(nameIndex) else "temp_image"
        returnCursor?.close()

        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val file = File(context.cacheDir, name ?: "temp_image")
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        return file.absolutePath
    }
}

// ISO 시간 형식을 사람이 읽기 쉬운 형태로 변환하는 함수
fun formatTimestamp(isoTimestamp: String): String {
    try {
        // 마이크로초(6자리) → 밀리초(3자리)로 변환
        val fixed = isoTimestamp.replace(
            Regex("""(\\.\\d{3})\\d{3}"""), // .123456 → .123
            "$1"
        )
        val instant = Instant.parse(
            if (fixed.endsWith("Z")) fixed else fixed + "Z" // Z 없으면 붙이기
        )
        val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return dateTime.format(formatter)
    } catch (e: Exception) {
        Log.e("DateFormat", "Error parsing date: $isoTimestamp", e)
        return isoTimestamp
    }
}

// 바이트 단위 용량을 사람이 읽기 쉬운 단위로 변환
fun formatBytes(bytes: Long): String {
    val kb = 1024L
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format("%.1fGB", bytes.toDouble() / gb)
        bytes >= mb -> String.format("%.1fMB", bytes.toDouble() / mb)
        bytes >= kb -> String.format("%.1fKB", bytes.toDouble() / kb)
        else -> "$bytes B"
    }
}

@Composable
fun PhotoListScreen(
    photoList: List<PhotoItem>,
    isLoading: Boolean,
    isError: Boolean,
    isSelectionMode: Boolean,
    selectedPhotos: List<PhotoItem>,
    onPhotoSelect: (PhotoItem, Boolean) -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val context = LocalContext.current

    if (isError) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("에러 발생! 서버 연결 또는 네트워크를 확인하세요.")
            Spacer(modifier = Modifier.height(8.dp))
        }
        return
    }

    // 리스트는 항상 보여줌
    if (photoList.isEmpty()) {
        Text("사진이 없습니다.")
    } else {
        // 최신 업로드 사진이 맨 위에 오도록 정렬
        val sortedList = photoList.sortedByDescending { it.uploaded_at }
        LazyColumn {
            items(sortedList) { photo ->
                val isSelected = selectedPhotos.contains(photo)
                
                // 한 줄에 썸네일 + 파일명 + 시간 + 체크박스(선택 모드일 때)
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .clickable(enabled = isSelectionMode) {
                            if (isSelectionMode) {
                                onPhotoSelect(photo, !isSelected)
                            }
                        }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val baseUrl = "http://192.168.32.1:5000" // 실제 라즈베리파이 IP로 변경
                    AsyncImage(
                        model = if (photo.thumbnailUrl.isNotEmpty()) baseUrl + photo.thumbnailUrl else null,
                        contentDescription = photo.filename,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(64.dp),
                        error = painterResource(R.drawable.ic_launcher_foreground),
                        placeholder = painterResource(R.drawable.ic_launcher_foreground)
                    )
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        Text(
                            text = photo.filename,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = formatTimestamp(photo.uploaded_at),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked -> 
                                onPhotoSelect(photo, checked)
                            }
                        )
                    }
                }
            }
        }
    }

    // 로딩 중이면 Box로 인디케이터 중앙 배치
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

// 1. 썸네일 비율 유지 + 중앙 크롭 + 128x128
fun createThumbnail(filePath: String, context: Context): File? {
    // 1. EXIF 방향 보정
    val original = BitmapFactory.decodeFile(filePath) ?: return null
    val exif = ExifInterface(filePath)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        // 기타는 무시
    }
    val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    if (rotated != original) original.recycle()

    // 2. 중앙 크롭 (정사각형)
    val size = 128
    val minEdge = minOf(rotated.width, rotated.height)
    val cropX = (rotated.width - minEdge) / 2
    val cropY = (rotated.height - minEdge) / 2
    val cropped = Bitmap.createBitmap(rotated, cropX, cropY, minEdge, minEdge)
    if (cropped != rotated) rotated.recycle()

    // 3. 128x128로 리사이즈
    val thumbnail = Bitmap.createScaledBitmap(cropped, size, size, true)
    if (thumbnail != cropped) cropped.recycle()

    // 4. 임시 파일로 저장
    val thumbFile = File(context.cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
    thumbFile.outputStream().use { out ->
        thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    thumbnail.recycle()
    return thumbFile
}

@Composable
fun ObserveServerState(
    repository: PhotoRepository,
    onServerDisconnected: () -> Unit,
    onServerReconnected: () -> Unit,
    checkIntervalMs: Long = 3000L // 3초마다 체크
) {
    val scope = rememberCoroutineScope()
    var wasConnected by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            val connected = try {
                // 서버에 실제로 요청을 보내서 확인 (예: 사진 목록)
                repository.fetchPhotoListFromServer()
            } catch (e: Exception) {
                false
            }
            if (connected) {
                if (!wasConnected) {
                    wasConnected = true
                    onServerReconnected()
                }
            } else {
                if (wasConnected) {
                    wasConnected = false
                    onServerDisconnected()
                }
            }
            delay(checkIntervalMs)
        }
    }
}   