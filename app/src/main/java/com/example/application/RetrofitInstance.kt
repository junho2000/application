package com.example.application

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    val api: PhotoApiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://192.168.32.1:5000/") // 실제 라즈베리파이 IP로 변경
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PhotoApiService::class.java)
    }
}