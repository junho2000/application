package com.example.application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import retrofit2.Response
import retrofit2.http.*

interface PhotoApiService {
    @GET("/api/photos")
    suspend fun getPhotos(): Response<List<PhotoItem>>

    @Multipart
    @POST("/api/photos")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>

    @DELETE("/api/photos/{filename}")
    suspend fun deletePhoto(
        @Path("filename") filename: String
    ): Response<DeleteResponse>

    @Multipart
    @POST("/api/photos/{filename}/thumbnail")
    suspend fun uploadThumbnail(
        @Path("filename") filename: String,
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>

    @DELETE("/api/photos/{filename}/thumbnail")
    suspend fun deleteThumbnail(
        @Path("filename") filename: String
    ): Response<DeleteResponse>
}

data class UploadResponse(val success: Boolean, val filename: String)
data class DeleteResponse(val success: Boolean)

class PhotoRepository {
    private val photoList = mutableListOf<PhotoItem>()

    fun getPhotoList(): List<PhotoItem> = photoList

    suspend fun fetchPhotoListFromServer(): Boolean = withContext(Dispatchers.IO) {
        val response = RetrofitInstance.api.getPhotos()
        if (response.isSuccessful && response.body() != null) {
            photoList.clear()
            photoList.addAll(response.body()!!)
            true
        } else {
            false
        }
    }

    suspend fun uploadPhotoToServer(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val response = RetrofitInstance.api.uploadPhoto(body)
        response.isSuccessful && response.body()?.success == true
    }

    suspend fun deletePhotoFromServer(filename: String): Boolean = withContext(Dispatchers.IO) {
        val response = RetrofitInstance.api.deletePhoto(filename)
        response.isSuccessful && response.body()?.success == true
    }

    suspend fun uploadThumbnailToServer(filename: String, filePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val response = RetrofitInstance.api.uploadThumbnail(filename, body)
        response.isSuccessful && response.body()?.success == true
    }

    suspend fun deleteThumbnailFromServer(filename: String): Boolean = withContext(Dispatchers.IO) {
        val response = RetrofitInstance.api.deleteThumbnail(filename)
        response.isSuccessful && response.body()?.success == true
    }

    suspend fun uploadPhotoAndThumbnail(filePath: String): Boolean = withContext(Dispatchers.IO) {
        // 1. 원본 사진 업로드
        val photoUploadSuccess = uploadPhotoToServer(filePath)
        if (!photoUploadSuccess) return@withContext false
    
        // 2. 파일명 추출 (서버에 업로드한 파일명과 동일해야 함)
        val file = File(filePath)
        val filename = file.name
    
        // 3. 썸네일 업로드
        val thumbnailUploadSuccess = uploadThumbnailToServer(filename, filePath)
        return@withContext thumbnailUploadSuccess
    }
}
