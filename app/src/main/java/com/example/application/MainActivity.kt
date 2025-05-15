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

class MainActivity : ComponentActivity() {
    private val repository = PhotoRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 배경화면 끝까지 확장

        // setContent 블록에서 scope 선언
        setContent {
            val photoList = remember { mutableStateListOf<PhotoItem>() }
            var isLoading by remember { mutableStateOf(true) }
            var isError by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            // pickImageLauncher를 setContent 블록 안에서 선언
            val context = LocalContext.current
            val pickImageLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let {
                    val filePath = getFilePathFromUri(context, it)
                    if (filePath != null) {
                        scope.launch {
                            val success = repository.uploadPhotoAndThumbnail(filePath)
                            if (success) {
                                isLoading = true
                                isError = false
                                val fetchSuccess = repository.fetchPhotoListFromServer()
                                if (fetchSuccess) {
                                    val newList = repository.getPhotoList()
                                    // 기존에 없는 파일만 추가
                                    val existingFilenames = photoList.map { it.filename }.toSet()
                                    val onlyNew = newList.filter { it.filename !in existingFilenames }
                                    photoList.addAll(0, onlyNew) // 최신순이므로 앞에 추가
                                    isError = false
                                } else {
                                    isError = true
                                }
                                isLoading = false
                            } else {
                                // 업로드 실패 처리
                            }
                        }
                    }
                }
            }

            // 가운데 정렬을 위한 Column 사용
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Button(onClick = { pickImageLauncher.launch("image/*") }) {
                    Text("사진 업로드")
                }

                // 리스트는 버튼 아래에 배치
                PhotoListScreen(
                    photoList = photoList,
                    isLoading = isLoading,
                    isError = isError
                )
            }

            LaunchedEffect(Unit) {
                scope.launch {
                    isLoading = true
                    isError = false
                    val success = repository.fetchPhotoListFromServer()
                    if (success) {
                        photoList.clear()
                        photoList.addAll(repository.getPhotoList())
                        isError = false
                    } else {
                        isError = true
                    }
                    isLoading = false
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

@Composable
fun PhotoListScreen(
    photoList: List<PhotoItem>,
    isLoading: Boolean,
    isError: Boolean
) {
    val context = LocalContext.current

    // 에러만 별도 처리
    if (isError) {
        Text("에러 발생! 서버 연결 또는 네트워크를 확인하세요.")
        return
    }

    // 리스트는 항상 보여줌
    if (photoList.isEmpty()) {
        Text("사진이 없습니다.")
    } else {
        val sortedList = photoList.sortedByDescending { it.uploadedAt }
        LazyColumn {
            items(sortedList) { photo ->
                // 한 줄에 썸네일 + 파일명
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                ) {
                    val baseUrl = "http://192.168.32.1:5000" // 실제 라즈베리파이 IP로 변경
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(baseUrl + photo.thumbnailUrl)
                            .crossfade(true)
                            .size(128)
                            .build(),
                        contentDescription = photo.filename,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(64.dp),
                        error = painterResource(R.drawable.ic_launcher_foreground),
                        onError = { error ->
                            Log.e("AsyncImage", "이미지 로딩 실패: ${baseUrl + photo.thumbnailUrl}, ${error.result.throwable}")
                        }
                    )
                    Text(
                        text = photo.filename,
                        modifier = Modifier
                            .alignByBaseline()
                    )
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