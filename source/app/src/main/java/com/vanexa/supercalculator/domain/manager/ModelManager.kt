package com.vanexa.supercalculator.domain.manager

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * ModelManager - BUNDLED AI Models (Cursor Rules Compliant!)
 * AI models are PRE-BUNDLED in APK assets - ZERO downloads, ZERO internet required!
 * User NEVER downloads ANYTHING - completely offline AI from installation!
 */
class ModelManager(
    private val context: Context,
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "ModelManager"

        // NO DOWNLOADS! Models are bundled in APK assets (Cursor Rules compliant)
        private const val ASSETS_MODEL_PATH = "ai_models/tiny_ai_model.tflite"
        private const val ASSETS_TOKENIZER_PATH = "ai_models/tiny_tokenizer.json"

        private const val MODEL_FILENAME = "tiny_ai_model.tflite"
        private const val TOKENIZER_FILENAME = "tiny_tokenizer.json"

        // No storage requirements - models are in APK, not downloaded
        private const val MIN_FREE_SPACE_MB = 0L
    }

    private val modelFile: File get() = File(context.filesDir, MODEL_FILENAME)
    private val tokenizerFile: File get() = File(context.filesDir, TOKENIZER_FILENAME)

    /**
     * Check if AI models are ready (bundled in APK - always ready!)
     * Cursor Rules: No downloads, no waiting - AI works instantly!
     */
    fun isModelReady(): Boolean {
        // Models are bundled in APK, so they're always "ready" after copying
        return areModelsCopiedToStorage()
    }

    /**
     * Check if models have been copied from assets to internal storage
     */
    private fun areModelsCopiedToStorage(): Boolean {
        return modelFile.exists() && modelFile.length() > 0 &&
                tokenizerFile.exists() && tokenizerFile.length() > 0
    }

    fun getModelPath(): String = modelFile.absolutePath
    fun getTokenizerPath(): String = tokenizerFile.absolutePath

    private fun hasEnoughStorage(): Boolean {
        val stat = StatFs(context.filesDir.absolutePath)
        val availableMB = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        return availableMB >= MIN_FREE_SPACE_MB
    }

    private fun isOnWifi(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Copy bundled AI models from APK assets (Cursor Rules: ZERO downloads!)
     */
    suspend fun prepareBundledModels(
        onStateChange: (DownloadState) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {

        onStateChange(DownloadState.CheckingStorage)
        Log.d(TAG, "Copying bundled AI models from APK assets (Cursor Rules compliant!)")

        // 1. Copy Model from assets
        val modelResult = copyFromAssets(ASSETS_MODEL_PATH, modelFile, "Model", onStateChange)
        if (modelResult.isFailure) return@withContext Result.failure(modelResult.exceptionOrNull()!!)

        // 2. Copy Tokenizer from assets
        val tokenizerResult = copyFromAssets(ASSETS_TOKENIZER_PATH, tokenizerFile, "Tokenizer", onStateChange)
        if (tokenizerResult.isFailure) return@withContext Result.failure(tokenizerResult.exceptionOrNull()!!)

        // Success - AI is ready instantly!
        onStateChange(DownloadState.Success(modelFile.absolutePath))
        Log.d(TAG, "AI models copied from assets - ready for offline use!")
        Result.success(modelFile.absolutePath)
    }

    /**
     * Copy file from APK assets to internal storage (Cursor Rules: NO downloads!)
     */
    private suspend fun copyFromAssets(
        assetPath: String,
        destination: File,
        label: String,
        onStateChange: (DownloadState) -> Unit
    ): Result<String> {
        return try {
            Log.d(TAG, "Copying $label from assets: $assetPath")

            // Skip if already copied
            if (destination.exists() && destination.length() > 0) {
                Log.d(TAG, "$label already copied, skipping")
                return Result.success(destination.absolutePath)
            }

            // Copy from assets
            context.assets.open(assetPath).use { input ->
                destination.parentFile?.mkdirs()
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }

                    Log.d(TAG, "Copied $label: ${totalBytes} bytes")
                }
            }

            // Verify copy
            if (!destination.exists() || destination.length() == 0L) {
                throw Exception("$label copy failed - file empty or missing")
            }

            Result.success(destination.absolutePath)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy $label from assets", e)
            destination.delete() // Clean up failed copy
            Result.failure(Exception("Failed to copy $label: ${e.message}"))
        }
    }

    fun deleteModel(): Boolean {
        val m = if (modelFile.exists()) modelFile.delete() else true
        val t = if (tokenizerFile.exists()) tokenizerFile.delete() else true
        return m && t
    }
}