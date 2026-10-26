package com.vanexa.supercalculator.presentation.size

sealed class AiState {
    object Checking : AiState()
    object Initializing : AiState()
    object Ready : AiState()
    data class DownloadProgress(
        val progress: Int,
        val speedMbps: Float,
        val etaSeconds: Int
    ) : AiState()
    data class Processing(val statusMessage: String) : AiState()
    data class Error(val message: String, val canRetry: Boolean) : AiState()
}