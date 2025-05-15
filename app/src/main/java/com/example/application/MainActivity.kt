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

class MainActivity : ComponentActivity() {
    private val repository = PhotoRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 배경화면 끝까지 확장
        setContent {
            val photoList = remember { mutableStateListOf<PhotoItem>() }
            var isLoading by remember { mutableStateOf(true) }
            var isError by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

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

            PhotoListScreen(
                photoList = photoList,
                isLoading = isLoading,
                isError = isError
            )
        }
    }
}

@Composable
fun PhotoListScreen(
    photoList: List<PhotoItem>,
    isLoading: Boolean,
    isError: Boolean
) {
    val context = LocalContext.current
    when {
        isLoading -> Text("로딩 중...")
        isError -> Text("에러 발생! 서버 연결 또는 네트워크를 확인하세요.")
        photoList.isEmpty() -> Text("사진이 없습니다.")
        else -> {
            // 최신순 정렬 (uploadedAt 내림차순)
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
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}