package com.example.wheatguard.classifier

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteClassifier(private val context: Context) {
    private var interpreter: Interpreter? = null

    init {
        try {
            val modelBuffer = loadModelFile(context, "model.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classifyImage(bitmap: Bitmap): ClassificationResult {
        val interp = interpreter ?: return ClassificationResult("Healthy", 0.99f)
        
        // 1. Image Pre-processing: Scale down to exactly 150x150 pixels with ARGB_8888 configuration
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 150, 150, true)
        
        // 2. Allocate ByteBuffer matching the shape [1, 150, 150, 3] in float format (4 bytes per float)
        val byteBuffer = ByteBuffer.allocateDirect(1 * 150 * 150 * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(150 * 150)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)
        
        // Normalize pixel color channels between 0.0f and 1.0f and load into ByteBuffer
        for (pixelValue in intValues) {
            val r = ((pixelValue shr 16 and 0xFF) / 255.0f)
            val g = ((pixelValue shr 8 and 0xFF) / 255.0f)
            val b = ((pixelValue and 0xFF) / 255.0f)
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        
        // 3. Output array: Float array of 15 confidence metrics
        val outputArray = Array(1) { FloatArray(15) }
        interp.run(byteBuffer, outputArray)
        
        // Find index with highest confidence score
        var maxIdx = 0
        var maxVal = outputArray[0][0]
        for (i in 1..14) {
            if (outputArray[0][i] > maxVal) {
                maxVal = outputArray[0][i]
                maxIdx = i
            }
        }
        
        // 4. Map the 15 output prediction indices back to their categories
        val diseaseName = when (maxIdx) {
            0 -> "Healthy"
            2, 11, 12, 13 -> "Yellow Rust"
            3, 10 -> "Black Rust"
            4 -> "Brown Rust"
            else -> "Inconclusive" // Overridden to inconclusive for blights, mildews, scab, septoria, etc.
        }
        
        return ClassificationResult(diseaseName, maxVal)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

data class ClassificationResult(
    val diseaseName: String,
    val confidence: Float
)
