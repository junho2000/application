package com.example.application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import retrofit2.Response
import retrofit2.http.*
import android.content.Context

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

    @GET("/api/storage")
    suspend fun getStorageInfo(): Response<Map<String, Any>>
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

    suspend fun uploadPhotoAndThumbnail(filePath: String, context: Context): Boolean = withContext(Dispatchers.IO) {
        // 1. 원본 사진 업로드
        val photoUploadSuccess = uploadPhotoToServer(filePath)
        if (!photoUploadSuccess) return@withContext false
    
        // 2. 파일명 추출
        val file = File(filePath)
        val filename = file.name
    
        // 3. 썸네일 생성 및 업로드
        val thumbFile = createThumbnail(filePath, context)
        if (thumbFile == null) return@withContext false
        val thumbnailUploadSuccess = uploadThumbnailToServer(filename, thumbFile.absolutePath)
        thumbFile.delete()
        return@withContext thumbnailUploadSuccess
    }

    suspend fun deletePhotoAndThumbnail(filename: String): Boolean = withContext(Dispatchers.IO) {
        // 1. 썸네일 먼저 삭제 (실패해도 무시)
        deleteThumbnailFromServer(filename)
        // 2. 원본 사진 삭제 (이 결과만 반환)
        val photoDeleteSuccess = deletePhotoFromServer(filename)
        return@withContext photoDeleteSuccess
    }

    // 라즈베리파이 스토리지 정보 조회
    suspend fun fetchStorageInfo(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitInstance.api.getStorageInfo()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val used = (body["used_bytes"] as? Number)?.toLong() ?: return@withContext null
                val total = (body["total_bytes"] as? Number)?.toLong() ?: return@withContext null
                return@withContext Pair(used, total)
            }
        } catch (e: Exception) {
            // ignore
        }
        return@withContext null
    }
}
