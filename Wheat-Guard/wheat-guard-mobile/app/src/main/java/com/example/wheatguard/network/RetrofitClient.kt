package com.example.wheatguard.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// --- API Request/Response Data Models ---

data class ProfileData(
    val role: String,
    val phone_number: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val profile: ProfileData
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val user_id: Int,
    val username: String,
    val role: String
)

data class ScanRequest(
    val image: String, // Base64 representation
    val source: String = "ONLINE"
)

data class ScanResponse(
    val log_id: Int,
    val disease_name: String,
    val disease_name_ur: String,
    val confidence: Float,
    val pesticide_name: String,
    val pesticide_name_ur: String,
    val dosage: String,
    val dosage_ur: String,
    val precautions: String,
    val precautions_ur: String,
    val description: String,
    val description_ur: String,
    val timestamp: String
)

data class LogEntry(
    val id: Int,
    val username: String,
    val image: String,
    val disease_name: String,
    val confidence: Float,
    val source: String,
    val timestamp: String
)

data class DiseaseEntry(
    val id: Int,
    val name: String,
    val name_ur: String,
    val pesticide_name: String,
    val pesticide_name_ur: String,
    val dosage: String,
    val dosage_ur: String,
    val precautions: String,
    val precautions_ur: String,
    val description: String,
    val description_ur: String
)

data class SyncScanRequest(
    val classification: String
)

// --- Retrofit API Service Interface ---

interface ApiService {
    @POST("api/auth/signup/")
    fun signUp(@Body request: RegisterRequest): Call<AuthResponse>

    @POST("api/auth/login/")
    fun login(@Body request: LoginRequest): Call<AuthResponse>

    @POST("api/scan/")
    fun scanImage(@Body request: ScanRequest): Call<ScanResponse>

    @GET("api/history/")
    fun getHistory(): Call<List<LogEntry>>

    @GET("api/knowledge-base/")
    fun getKnowledgeBase(): Call<List<DiseaseEntry>>

    @POST("api/sync-scan/")
    fun syncScanResult(@Body request: SyncScanRequest): Call<Void>
}

// --- Retrofit Client Configuration ---

object RetrofitClient {
    private const val EMULATOR_URL = "http://10.0.2.2:8000/"
    private const val PHONE_URL = "http://192.168.1.8:8000/"

    private val BASE_URL =
        if (android.os.Build.FINGERPRINT.contains("generic")) {
            EMULATOR_URL
        } else {
            PHONE_URL
        }// Deployed backend on PythonAnywhere
    private var token: String? = null

    fun setToken(authToken: String?) {
        token = authToken
    }

    fun getToken(): String? = token

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()
        
        // Add Authorization header if token is set
        token?.let {
            builder.addHeader("Authorization", "Token $it")
        }
        
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
