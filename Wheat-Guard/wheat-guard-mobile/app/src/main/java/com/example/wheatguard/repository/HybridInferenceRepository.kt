package com.example.wheatguard.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import com.example.wheatguard.classifier.TFLiteClassifier
import com.example.wheatguard.network.RetrofitClient
import com.example.wheatguard.network.ScanRequest
import com.example.wheatguard.network.ScanResponse
import com.example.wheatguard.network.SyncScanRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HybridInferenceRepository(private val context: Context) {
    private val classifier = TFLiteClassifier(context)

    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun syncOfflineScan(classification: String) {
        val request = SyncScanRequest(classification)
        RetrofitClient.apiService.syncScanResult(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                android.util.Log.d("HybridInference", "Successfully synced classification: $classification")
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                android.util.Log.e("HybridInference", "Failed to sync classification: $classification", t)
            }
        })
    }

    suspend fun analyzeLeaf(bitmap: Bitmap, onResult: (Result<ScanResponse>) -> Unit) {
        if (isOnline()) {
            // ONLINE MODE: Upload to Django Backend API
            try {
                val base64Image = bitmapToBase64(bitmap)
                val request = ScanRequest(image = base64Image, source = "ONLINE")
                
                // Execute sync API call on background thread
                val response = RetrofitClient.apiService.scanImage(request).execute()
                if (response.isSuccessful && response.body() != null) {
                    val scanResponse = response.body()!!
                    // Sync the result asynchronously to backend domain
                    syncOfflineScan(scanResponse.disease_name)
                    onResult(Result.success(scanResponse))
                } else {
                    onResult(Result.failure(Exception("Server returned error: ${response.code()}")))
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        } else {
            // OFFLINE MODE: Run on-device TFLite model
            try {
                val localResult = classifier.classifyImage(bitmap)
                val disease = localResult.diseaseName
                val confidence = localResult.confidence
                
                // Map the output to identical ScanResponse metadata (Offline Advisory rule engine)
                val offlineResponse = getOfflineAdvisory(disease, confidence)
                // Sync the result asynchronously to backend domain
                syncOfflineScan(disease)
                onResult(Result.success(offlineResponse))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Compress as JPEG to save network data
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun getOfflineAdvisory(disease: String, confidence: Float): ScanResponse {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

        return when (disease) {
            "Yellow Rust" -> ScanResponse(
                log_id = -1, // Indicates offline log
                disease_name = "Yellow Rust",
                disease_name_ur = "پیلا زنگ (Yellow Rust)",
                confidence = confidence * 100,
                pesticide_name = "Propiconazole (Tilt 250 EC)",
                pesticide_name_ur = "پروپیکونازول (ٹلٹ 250 ای سی)",
                dosage = "200ml per acre",
                dosage_ur = "200 ملی لیٹر فی ایکڑ",
                precautions = "Use protective gear. Avoid spraying in high wind. Ensure uniform coverage.",
                precautions_ur = "حفاظتی سامان استعمال کریں۔ تیز ہوا میں سپرے کرنے سے گریز کریں۔ یکساں چھڑکاو کو یقینی بنائیں۔",
                description = "Characterized by yellow, stripe-like pustules on the leaf surface.",
                description_ur = "پتے کی سطح پر پیلے رنگ کی دھاری دار نشانیاں بنتی ہیں۔",
                timestamp = timestamp
            )
            "Brown Rust" -> ScanResponse(
                log_id = -1,
                disease_name = "Brown Rust",
                disease_name_ur = "بھورا زنگ (Brown/Leaf Rust)",
                confidence = confidence * 100,
                pesticide_name = "Tebuconazole (Folicur 250 EW)",
                pesticide_name_ur = "ٹیبوکونازول (فولیکور 250 ای ڈبلیو)",
                dosage = "250ml per acre",
                dosage_ur = "250 ملی لیٹر فی ایکڑ",
                precautions = "Avoid contact with eyes and skin. Spray during cool hours of the day (morning/evening).",
                precautions_ur = "آنکھوں اور جلد کے ساتھ رابطے سے بچیں۔ دن کے ٹھنڈے اوقات (صبح یا شام) میں سپرے کریں۔",
                description = "Oval-shaped, orange-brown pustules scattered randomly across the leaf blade.",
                description_ur = "پتے پر بے ترتیب پھیلے ہوئے بیضوی شکل کے نارنجی بھورے رنگ کے پسٹول بنتے ہیں۔",
                timestamp = timestamp
            )
            "Black Rust" -> ScanResponse(
                log_id = -1,
                disease_name = "Black Rust",
                disease_name_ur = "کالا زنگ (Black/Stem Rust)",
                confidence = confidence * 100,
                pesticide_name = "Azoxystrobin + Difenoconazole (Amistar Top)",
                pesticide_name_ur = "ازوکسیسٹروبن + ڈائی فینوکونازول (امیسٹار ٹاپ)",
                dosage = "200ml per acre",
                dosage_ur = "200 ملی لیٹر فی ایکڑ",
                precautions = "Harmful to aquatic life. Wash hands and face thoroughly with soap after application.",
                precautions_ur = "آبی حیات کے لیے نقصان دہ ہے۔ سپرے کے بعد ہاتھ اور منہ صابن سے اچھی طرح دھوئیں۔",
                description = "Elongated, dark brown to black pustules on both leaves and stems.",
                description_ur = "پتے اور تنے دونوں پر لمبے، گہرے بھورے سے کالے رنگ کے نشانات بنتے ہیں۔",
                timestamp = timestamp
            )
            "Inconclusive" -> ScanResponse(
                log_id = -1,
                disease_name = "Inconclusive",
                disease_name_ur = "غیر حتمی / کوئی دوسری بیماری (Inconclusive)",
                confidence = confidence * 100,
                pesticide_name = "N/A",
                pesticide_name_ur = "لاگو نہیں ہوتا",
                dosage = "N/A",
                dosage_ur = "لاگو نہیں ہوتا",
                precautions = "Consult an expert for diagnosis of non-rust conditions.",
                precautions_ur = "زنگ کے علاوہ دیگر بیماریوں کے حل کے لیے ماہر سے رجوع کریں۔",
                description = "Inconclusive / Other Condition Detected",
                description_ur = "غیر حتمی / کوئی دوسری بیماری پائی گئی ہے۔",
                timestamp = timestamp
            )
            else -> ScanResponse(
                log_id = -1,
                disease_name = "Healthy",
                disease_name_ur = "تندرست پتا (Healthy)",
                confidence = confidence * 100,
                pesticide_name = "No Fungicide Needed",
                pesticide_name_ur = "کسی فنگسائڈ کی ضرورت نہیں ہے",
                dosage = "N/A",
                dosage_ur = "لاگو نہیں ہوتا",
                precautions = "Maintain regular crop monitoring. Follow standard irrigation schedules.",
                precautions_ur = "فصل کی باقاعدگی سے نگرانی جاری رکھیں۔ پانی اور کھاد کے معیاری شیڈول پر عمل کریں۔",
                description = "The leaf is clean, vibrant green, and free from any rust pustules or signs of infection.",
                description_ur = "پتا بالکل صاف، چمکدار سبز اور زنگ کے نشانات یا انفیکشن کی علامات سے پاک ہے۔",
                timestamp = timestamp
            )
        }
    }
}
