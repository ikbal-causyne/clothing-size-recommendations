package com.vanexa.supercalculator.presentation.size

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vanexa.supercalculator.domain.model.*
import com.vanexa.supercalculator.domain.usecase.*
import com.vanexa.supercalculator.domain.analytics.AnalyticsTracker
import com.vanexa.supercalculator.domain.manager.ModelManager
import com.vanexa.supercalculator.domain.manager.DownloadState
import com.vanexa.supercalculator.domain.service.SmartSizeParser
import com.vanexa.supercalculator.domain.service.ParseResult
import com.vanexa.supercalculator.data.billing.BillingManager
import com.vanexa.supercalculator.data.network.VanexaAiApiService
import com.vanexa.data.models.ManualSizeChart
import com.vanexa.data.models.SizeChartEntry as ManualSizeChartEntry
import com.vanexa.data.models.MeasurementUnit as ManualMeasurementUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeoutException
import com.vanexa.supercalculator.BuildConfig

/**
 * SizeViewModel - PRD v2.0 Compliant
 * Reconstructed for Size Intelligence Engine
 */
class SizeViewModel(
    private val application: android.app.Application,
    private val sizeChartParser: SizeChartParser,
    private val bodyEstimator: BodyEstimator,
    private val regionalNormalizer: RegionalFitNormalizer,
    private val sizeRecommendationEngine: SizeRecommendationEngine,
    private val inputValidator: InputValidator,
    private val sizeLabelNormalizer: SizeLabelNormalizer,
    private val imageTextExtractor: ImageTextExtractor,
    private val modelManager: ModelManager,
    private val smartSizeParser: SmartSizeParser,
    private val apiService: VanexaAiApiService,
    private val billingManager: BillingManager,
    private val analytics: AnalyticsTracker  // ✅ CONCERN #2: Analytics/Telemetry
) : ViewModel() {
    
    // ✅ BUG #54: Track jobs for proper cancellation
    private var parseJob: Job? = null
    private var ocrJob: Job? = null
    private var recommendJob: Job? = null
    
    // ✅ BUG #60: Cache parsed size charts to avoid redundant parsing
    private val parsedChartCache = mutableMapOf<String, List<SizeChartEntry>>()
    private val MAX_CACHE_SIZE = 5

    private val deviceHash: String by lazy {
        val androidId = android.provider.Settings.Secure.getString(
            application.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val input = androidId + com.vanexa.supercalculator.BuildConfig.APPLICATION_ID
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    
    override fun onCleared() {
        super.onCleared()
        // ✅ BUG #54: Cancel all running jobs
        parseJob?.cancel()
        ocrJob?.cancel()
        recommendJob?.cancel()

        // AI Engine cleanup
        smartSizeParser.cleanup()

        android.util.Log.d("SizeViewModel", "✅ [BUG #54] All coroutine jobs cancelled on ViewModel clear")
        android.util.Log.d("SizeViewModel", "✅ AI engine resources cleaned up")
    }
    
    // User Input State
    private val _userInput = MutableStateFlow<UserBodyInput?>(null)
    val userInput: StateFlow<UserBodyInput?> = _userInput.asStateFlow()
    
    private val _optionalMeasurements = MutableStateFlow<OptionalMeasurements?>(null)
    val optionalMeasurements: StateFlow<OptionalMeasurements?> = _optionalMeasurements.asStateFlow()

    private val _optionalMeasurementEvents = MutableSharedFlow<OptionalMeasurementEvent>(extraBufferCapacity = 1)
    val optionalMeasurementEvents = _optionalMeasurementEvents.asSharedFlow()
    
    // Size Chart State
    private val _sizeChart = MutableStateFlow<List<SizeChartEntry>>(emptyList())
    val sizeChart: StateFlow<List<SizeChartEntry>> = _sizeChart.asStateFlow()
    
    // Garment Type
    private val _garmentType = MutableStateFlow<GarmentType?>(null)
    val garmentType: StateFlow<GarmentType?> = _garmentType.asStateFlow()
    
    // Recommendation State
    private val _recommendation = MutableStateFlow<SizeRecommendationResult?>(null)
    val recommendation: StateFlow<SizeRecommendationResult?> = _recommendation.asStateFlow()
    
    // ✅ FIX #22: Structured Error States
    sealed class ErrorState {
        object None : ErrorState()
        data class Warning(val message: String) : ErrorState()  // Yellow card, dismissible
        data class Error(val message: String) : ErrorState()    // Red card, must fix
        data class Critical(val message: String) : ErrorState() // Red card, blocks action
    }
    
    sealed class OptionalMeasurementEvent {
        object ClearAll : OptionalMeasurementEvent()
    }

    private val _errorState = MutableStateFlow<ErrorState>(ErrorState.None)
    val errorState: StateFlow<ErrorState> = _errorState.asStateFlow()
    
    // Legacy error for backward compatibility (will be removed)
    val error: StateFlow<String?> = _errorState.map { 
        when (it) {
            is ErrorState.Warning -> it.message
            is ErrorState.Error -> it.message
            is ErrorState.Critical -> it.message
            ErrorState.None -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    // Method to set error (for UI to clear proactively)
    fun setError(message: String?) {
        if (message == null) {
            _errorState.value = ErrorState.None
        } else {
            // Determine severity based on message content
            _errorState.value = when {
                message.contains("Permission denied", ignoreCase = true) -> ErrorState.Critical(message)
                message.contains("Failed to", ignoreCase = true) -> ErrorState.Error(message)
                message.contains("Warning", ignoreCase = true) || message.contains("may", ignoreCase = true) -> ErrorState.Warning(message)
                else -> ErrorState.Error(message)
            }
        }
    }
    
    // Method to clear error
    fun clearError() {
        _errorState.value = ErrorState.None
    }

    // ============================================
    // AI ENGINE STATE MANAGEMENT
    // ============================================

    /**
     * Check AI model status and prepare AI engine automatically
     */
    private fun checkAndPrepareAi() {
        viewModelScope.launch {
            try {
                _aiState.value = AiState.Checking

                if (modelManager.isModelReady()) {
                    _aiState.value = AiState.Initializing
                    // Model exists, ready to use
                    _aiState.value = AiState.Ready
                    android.util.Log.d("SizeViewModel", "✅ AI model ready for use")
                } else {
                    // Models not copied yet, prepare bundled models
                    android.util.Log.d("SizeViewModel", "📦 AI models not copied yet, preparing from assets...")
                    prepareBundledAiModels()
                }
            } catch (e: Exception) {
                android.util.Log.e("SizeViewModel", "❌ AI check failed", e)
                _aiState.value = AiState.Error(
                    "Failed to check AI model: ${e.message}",
                    canRetry = true
                )
            }
        }
    }

    /**
     * Prepare bundled AI models (Cursor Rules: NO downloads!)
     */
    private suspend fun prepareBundledAiModels() {
        modelManager.prepareBundledModels { state ->
            when (state) {
                is DownloadState.CheckingStorage -> {
                    _aiState.value = AiState.Processing("Preparing AI models...")
                    android.util.Log.d("SizeViewModel", "📦 Preparing bundled AI models...")
                }
                is DownloadState.Success -> {
                    _aiState.value = AiState.Ready
                    android.util.Log.d("SizeViewModel", "✅ AI models ready - offline AI activated!")
                }
                is DownloadState.Error -> {
                    _aiState.value = AiState.Error(state.message, state.canRetry)
                    android.util.Log.e("SizeViewModel", "❌ AI setup failed: ${state.message}")
                }
                else -> {
                    // Handle other states if needed
                }
            }
        }.onFailure { error ->
            _aiState.value = AiState.Error(
                "AI setup failed: ${error.message}",
                canRetry = true
            )
            android.util.Log.e("SizeViewModel", "❌ AI setup failed", error)
        }
    }

    /**
     * Retry failed AI operations
     */
    fun retryAi() {
        android.util.Log.d("SizeViewModel", "🔄 Retrying AI operations...")
        checkAndPrepareAi()
    }
    
    // ✅ FIX #31: Progressive Loading States
    sealed class LoadingState {
        object Idle : LoadingState() {
            override fun toString() = "Idle"
        }
        object EstimatingBody : LoadingState() {
            override fun toString() = "EstimatingBody"
        }
        object NormalizingChart : LoadingState() {
            override fun toString() = "NormalizingChart"
        }
        object ScoringFit : LoadingState() {
            override fun toString() = "ScoringFit"
        }
        object GeneratingRecommendation : LoadingState() {
            override fun toString() = "GeneratingRecommendation"
        }
    }
    
    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()
    
    // Loading State for quick operations (parse, OCR) - separate from progressive loading
    private val _isLoading = MutableStateFlow(false)
    
    // Legacy isLoading for backward compatibility - combines both loading states
    val isLoading: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        _loadingState,
        _isLoading
    ) { loadingState, isLoading -> 
        loadingState != LoadingState.Idle || isLoading
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    // Estimated Body Measurements
    private val _estimatedBody = MutableStateFlow<EstimatedBodyMeasurements?>(null)
    val estimatedBody: StateFlow<EstimatedBodyMeasurements?> = _estimatedBody.asStateFlow()
    
    // General Mode: true when recommendation is based on standard sizing (no brand chart)
    private val _isGeneralMode = MutableStateFlow(false)
    val isGeneralMode: StateFlow<Boolean> = _isGeneralMode.asStateFlow()
    
    // ✅ FIX #30: Debug flag - only show debug info in debug builds
    private val isDebugMode = BuildConfig.DEBUG
    val showDebugInfo: StateFlow<Boolean> = MutableStateFlow(isDebugMode).asStateFlow()

    // AI Engine State Management
    private val _aiState = MutableStateFlow<AiState>(AiState.Checking)
    val aiState: StateFlow<AiState> = _aiState.asStateFlow()

    init {
        checkAndPrepareAi()
    }
    
    fun setHeight(height: Double) {
        android.util.Log.d("SizeViewModel", "📝 [User Input] setHeight called with: $height cm")
        val currentInput = _userInput.value
        _userInput.value = currentInput?.copy(height = height) 
            ?: UserBodyInput(height, 0.0, Gender.MALE, SizeRegion.US)
        android.util.Log.d("SizeViewModel", "✅ [User Input] Height updated to $height cm, current state: ${_userInput.value}")
        
        // ✅ FIX #24: Re-validate optional measurements when height changes
        _optionalMeasurements.value?.let { optional ->
            val validation = inputValidator.validateOptionalMeasurements(
                optional,
                height,
                _userInput.value?.weight ?: 0.0,
                _userInput.value?.gender ?: Gender.MALE
            )
            
            if (validation is ValidationResult.Error) {
                // Clear invalid optional measurements
                _optionalMeasurements.value = null
                _optionalMeasurementEvents.tryEmit(OptionalMeasurementEvent.ClearAll)
                setError("Optional measurements reset due to height change. Please re-enter if needed.")
                android.util.Log.w("SizeViewModel", "⚠️ [FIX #24] Optional measurements cleared: ${validation.message}")
            }
        }
    }
    
    fun setWeight(weight: Double) {
        android.util.Log.d("SizeViewModel", "📝 [User Input] setWeight called with: $weight kg")
        val currentInput = _userInput.value
        _userInput.value = currentInput?.copy(weight = weight)
            ?: UserBodyInput(0.0, weight, Gender.MALE, SizeRegion.US)
        android.util.Log.d("SizeViewModel", "✅ [User Input] Weight updated to $weight kg, current state: ${_userInput.value}")
        
        // ✅ FIX #24: Re-validate optional measurements when weight changes
        _optionalMeasurements.value?.let { optional ->
            val validation = inputValidator.validateOptionalMeasurements(
                optional,
                _userInput.value?.height ?: 0.0,
                weight,
                _userInput.value?.gender ?: Gender.MALE
            )
            
            if (validation is ValidationResult.Error) {
                // Clear invalid optional measurements
                _optionalMeasurements.value = null
                _optionalMeasurementEvents.tryEmit(OptionalMeasurementEvent.ClearAll)
                setError("Optional measurements reset due to weight change. Please re-enter if needed.")
                android.util.Log.w("SizeViewModel", "⚠️ [FIX #24] Optional measurements cleared: ${validation.message}")
            }
        }
    }
    
    fun setGender(gender: Gender) {
        val currentInput = _userInput.value
        _userInput.value = currentInput?.copy(gender = gender)
            ?: UserBodyInput(0.0, 0.0, gender, SizeRegion.US)
    }
    
    fun setRegion(region: SizeRegion) {
        val currentInput = _userInput.value
        _userInput.value = currentInput?.copy(region = region)
            ?: UserBodyInput(0.0, 0.0, Gender.MALE, region)
    }
    
    fun setOptionalMeasurements(measurements: OptionalMeasurements) {
        _optionalMeasurements.value = measurements
    }
    
    fun setGarmentType(type: GarmentType) {
        _garmentType.value = type
    }
    
    fun parseSizeChart(text: String) {
        // ✅ BUG #62: Sanitize input before processing
        val sanitized = sanitizeInput(text)
        android.util.Log.d("SizeViewModel", "🔒 [BUG #62] Input sanitized: ${text.length} → ${sanitized.length} chars")
        
        // ✅ BUG #54: Cancel previous job if still running
        parseJob?.cancel()
        
        parseJob = viewModelScope.launch {
            android.util.Log.d("SizeViewModel", "🔍 [parseSizeChart] Starting parse, text length: ${sanitized.length}")
            
            // ✅ BUG #60: Check cache first to avoid redundant parsing
            val cacheKey = sanitized.take(1000).hashCode().toString()  // Use hash of first 1000 chars as key
            val cached = parsedChartCache[cacheKey]
            
            if (cached != null) {
                android.util.Log.d("SizeViewModel", "✅ [BUG #60] Using cached size chart: ${cached.size} entries")
                _sizeChart.value = cached
                return@launch
            }
            
            _isLoading.value = true
            clearError()
            
            try {
                // ✅ BUG #54: Make parsing cancellable
                ensureActive()
                android.util.Log.d("SizeViewModel", "🔍 [parseSizeChart] Calling sizeChartParser.parse()")
                // ✅ CONCERN #3: Add timeout for parsing (30 seconds)
                val parsed = withTimeout(30_000L) {
                    withContext(Dispatchers.Default) {
                        ensureActive()
                        sizeChartParser.parse(sanitized)  // ✅ BUG #62: Use sanitized input
                    }
                }
                android.util.Log.d("SizeViewModel", "✅ [parseSizeChart] Parser returned ${parsed.size} entries")
                
                val normalized = parsed.map { entry ->
                    entry.copy(sizeLabel = sizeLabelNormalizer.normalize(entry.sizeLabel))
                }
                android.util.Log.d("SizeViewModel", "✅ [parseSizeChart] Normalized to ${normalized.size} entries")
                
                // ✅ CONCERN #2: Track parsing success
                analytics.trackEvent("parsing_success", mapOf(
                    "entries_count" to parsed.size,
                    "text_length" to sanitized.length
                ))
                
                android.util.Log.d("SizeViewModel", "🔍 [parseSizeChart] Validating size chart...")
                val validation = inputValidator.validateSizeChart(normalized)
                when (validation) {
                    is ValidationResult.Error -> {
                        android.util.Log.w("SizeViewModel", "⚠️ [parseSizeChart] Validation Error: ${validation.message}")
                        
                        // ✅ CONCERN #2: Track parsing validation error
                        analytics.trackEvent("parsing_validation_error", mapOf(
                            "error_message" to validation.message,
                            "entries_count" to normalized.size
                        ))
                        
                        setError(validation.message)
                        _sizeChart.value = emptyList()
                    }
                    is ValidationResult.Warning -> {
                        android.util.Log.w("SizeViewModel", "⚠️ [parseSizeChart] Validation Warning: ${validation.message}")
                        setError(validation.message)
                        _sizeChart.value = normalized
                        android.util.Log.d("SizeViewModel", "✅ [parseSizeChart] Size chart set with ${normalized.size} entries (with warning)")
                    }
                    ValidationResult.Success -> {
                        android.util.Log.d("SizeViewModel", "✅ [parseSizeChart] Validation Success")
                        _sizeChart.value = normalized
                        android.util.Log.d("SizeViewModel", "✅ [parseSizeChart] Size chart set with ${normalized.size} entries")
                        android.util.Log.d("SizeViewModel", "📊 [parseSizeChart] Entries: ${normalized.map { it.sizeLabel }.joinToString(", ")}")
                        
                        // ✅ BUG #60: Store in cache (limit cache size to MAX_CACHE_SIZE entries)
                        if (parsedChartCache.size >= MAX_CACHE_SIZE) {
                            val firstKey = parsedChartCache.keys.first()
                            parsedChartCache.remove(firstKey)
                            android.util.Log.d("SizeViewModel", "✅ [BUG #60] Cache full, removed oldest entry: $firstKey")
                        }
                        parsedChartCache[cacheKey] = normalized
                        android.util.Log.d("SizeViewModel", "✅ [BUG #60] Cached size chart: ${normalized.size} entries (cache size: ${parsedChartCache.size})")
                    }
                }
                
                // ✅ FIX #43: Auto-detect garment type if not already set
                if (_garmentType.value == null) {
                    val detectedType = sizeChartParser.detectGarmentType(sanitized)  // ✅ BUG #62: Use sanitized input
                    if (detectedType != null) {
                        _garmentType.value = detectedType
                        android.util.Log.d("SizeViewModel", "🎯 [FIX #43] Auto-detected garment type: $detectedType")
                    }
                }
                
                android.util.Log.d("SizeViewModel", "📊 [parseSizeChart] Final _sizeChart.value.size: ${_sizeChart.value.size}")
            } catch (e: TimeoutCancellationException) {
                android.util.Log.w("SizeViewModel", "⚠️ [CONCERN #3] Parsing timed out after 30 seconds")
                setError("Processing is taking too long. Please check your network connection and try again.")
                _sizeChart.value = emptyList()
            } catch (e: CancellationException) {
                android.util.Log.d("SizeViewModel", "✅ [BUG #54] Parsing cancelled by user")
                throw e  // Re-throw to properly cancel coroutine
            } catch (e: Exception) {
                android.util.Log.e("SizeViewModel", "❌ [parseSizeChart] Exception: ${e.message}", e)
                setError("Failed to parse size chart: ${e.message}")
                _sizeChart.value = emptyList()
            } finally {
                _isLoading.value = false
                parseJob = null
                android.util.Log.d("SizeViewModel", "✅ [parseSizeChart] Parse complete, isLoading=false")
            }
        }
    }

    fun setManualSizeChart(manual: ManualSizeChart) {
        // Cancel any ongoing parse
        parseJob?.cancel()
        viewModelScope.launch {
            _isLoading.value = true
            clearError()
            try {
                android.util.Log.d("SizeViewModel", "📝 [ManualChart] Applying manual size chart for ${manual.garmentType}, entries=${manual.entries.size}, unit=${manual.unit}")
                val domainEntries = manual.entries.mapNotNull { entry: ManualSizeChartEntry ->
                    val cm = entry.toCm(manual.unit)
                    fun range(key: String) = cm[key]?.takeIf { it > 0.0 }?.let {
                        MeasurementRange(min = it, max = it, unit = MeasurementUnit.CM)
                    }
                    SizeChartEntry(
                        sizeLabel = sizeLabelNormalizer.normalize(entry.sizeLabel),
                        chest = range("chest"),
                        waist = range("waist"),
                        hip = range("hip"),
                        shoulder = range("shoulder"),
                        length = range("length"),
                        inseam = range("inseam"),
                        sleeve = range("sleeve")
                    )
                }

                val validation = inputValidator.validateSizeChart(domainEntries)
                when (validation) {
                    is ValidationResult.Error -> {
                        android.util.Log.w("SizeViewModel", "⚠️ [ManualChart] Validation Error: ${validation.message}")
                        setError(validation.message)
                        _sizeChart.value = emptyList()
                    }
                    is ValidationResult.Warning -> {
                        android.util.Log.w("SizeViewModel", "⚠️ [ManualChart] Validation Warning: ${validation.message}")
                        setError(validation.message)
                        _sizeChart.value = domainEntries
                    }
                    ValidationResult.Success -> {
                        android.util.Log.d("SizeViewModel", "✅ [ManualChart] Validation Success")
                        _sizeChart.value = domainEntries
                        analytics.trackEvent("manual_chart_success", mapOf("entries_count" to domainEntries.size))
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("SizeViewModel", "❌ [ManualChart] Failed: ${e.message}", e)
                setError("Failed to apply manual chart: ${e.message}")
                _sizeChart.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Scan a selected size-chart bitmap locally and store the parsed sizes.
     * The recognizer and parser run in the same job so the bitmap remains valid
     * until processing finishes.
     */
    fun processOCRFromImage(bitmap: android.graphics.Bitmap) {
        processOCRLocally(bitmap)
    }
    
    /** Process OCR without the optional network-assisted parser. */
    private fun processOCRLocally(bitmap: android.graphics.Bitmap) {
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            _isLoading.value = true
            clearError()
            try {
                val userRegion = _userInput.value?.region ?: SizeRegion.US
                val userGender = _userInput.value?.gender ?: Gender.MALE
                val ocrEngine = OcrSizeIntelligenceEngine()
                try {
                    // No API service or credentials are passed: this path stays on device.
                    val result = withTimeout(60_000L) {
                        ocrEngine.processImage(bitmap, userRegion, userGender)
                    }
                    if (result.sizes.isNotEmpty()) {
                        _sizeChart.value = result.sizes
                        _aiState.value = AiState.Ready
                    } else {
                        val message = result.warnings.firstOrNull() ?: "No sizes detected in image"
                        setError(message)
                        _aiState.value = AiState.Ready
                    }
                } finally {
                    ocrEngine.close()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                setError("Processing timed out. Please try again with a clearer image.")
                _aiState.value = AiState.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SizeViewModel", "Local OCR processing failed", e)
                setError("OCR processing failed: ${e.message ?: "Unknown error"}")
                _aiState.value = AiState.Ready
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Legacy OCR processing (backward compatibility)
     * Original implementation using ImageTextExtractor + SizeChartParser
     */
    private fun processOCRLegacy(bitmap: android.graphics.Bitmap) {
        ocrJob?.cancel()
        
        ocrJob = viewModelScope.launch {
            _isLoading.value = true
            clearError()
            
            try {
                android.util.Log.d("SizeViewModel", "🔍 [OCR Legacy] Starting legacy OCR processing...")
                
                val ocrResult = withTimeout(30_000L) {
                    imageTextExtractor.extractText(bitmap)
                }
                
                ocrResult.fold(
                    onSuccess = { extractedText ->
                        if (extractedText.isBlank()) {
                            setError("No text found in image. Please ensure the image contains a size chart.")
                            _isLoading.value = false
                            if (!bitmap.isRecycled) bitmap.recycle()
                            return@fold
                        }
                        
                        // Validate OCR output
                        val ocrValidation = validateOCROutput(extractedText)
                        when (ocrValidation) {
                            is ValidationResult.Error -> {
                                setError("Invalid content detected in image. Please use a size chart image.")
                                _isLoading.value = false
                                if (!bitmap.isRecycled) bitmap.recycle()
                                return@fold
                            }
                            else -> { /* Continue */ }
                        }
                        
                        // Parse size chart
                        parseSizeChart(extractedText)
                        
                        // Auto-detect garment type
                        if (_garmentType.value == null) {
                            val detectedType = sizeChartParser.detectGarmentType(extractedText)
                            if (detectedType != null) {
                                _garmentType.value = detectedType
                            }
                        }
                        
                        if (_sizeChart.value.isEmpty()) {
                            setError("Could not extract sizing data from image. Please ensures measurements are legible.")
                        }
                        
                        if (!bitmap.isRecycled) bitmap.recycle()
                        _isLoading.value = false
                    },
                    onFailure = { exception ->
                        android.util.Log.e("SizeViewModel", "❌ [OCR Legacy] Failed: ${exception.message}", exception)
                        setError("OCR failed: ${exception.message ?: "Unknown error"}")
                        _isLoading.value = false
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("SizeViewModel", "❌ [OCR Legacy] Exception: ${e.message}", e)
                setError("Failed to process image: ${e.message ?: "Unknown error"}")
                _isLoading.value = false
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }
    
    fun calculateRecommendation() {
        // ✅ BUG #54: Cancel previous recommendation job if still running
        recommendJob?.cancel()
        
        recommendJob = viewModelScope.launch {
            clearError()
            _recommendation.value = null
            
            try {
                val input = _userInput.value
                val chart = _sizeChart.value
                val type = _garmentType.value
                
                // Validate inputs
                if (input == null) {
                    setError("Please enter your body measurements (height and weight)")
                    _loadingState.value = LoadingState.Idle
                    return@launch
                }
                
                val inputValidation = inputValidator.validateUserInput(input)
                if (inputValidation is ValidationResult.Error) {
                    setError(inputValidation.message)
                    _loadingState.value = LoadingState.Idle
                    return@launch
                }
                
                // Determine mode: general (no chart) or brand-specific (chart provided)
                val isGeneral = chart.isEmpty()
                _isGeneralMode.value = isGeneral
                
                // Default garment type to TSHIRT if not selected (allows general mode without garment selection)
                val effectiveType = type ?: GarmentType.TSHIRT
                
                // Get effective chart: use standard sizing tables as fallback when no brand chart
                val effectiveChart = if (isGeneral) {
                    GeneralSizeChartProvider.getStandardChart(
                        region = input.region,
                        garmentType = effectiveType,
                        gender = input.gender
                    )
                } else chart
                
                if (isGeneral) {
                    android.util.Log.d("SizeViewModel", "📊 [General Mode] Using standard ${input.region} chart for ${input.gender} ${effectiveType}, ${effectiveChart.size} sizes")
                }
                
                // ✅ FIX #42: Validate size chart BEFORE normalization (skip for general mode - charts are pre-validated)
                if (!isGeneral) {
                    android.util.Log.d("SizeViewModel", "🔍 [FIX #42] Validating size chart before normalization...")
                    val chartValidation = inputValidator.validateSizeChart(effectiveChart)
                    when (chartValidation) {
                        is ValidationResult.Error -> {
                            setError(chartValidation.message)
                            _loadingState.value = LoadingState.Idle
                            android.util.Log.e("SizeViewModel", "❌ [FIX #42] Size chart validation failed: ${chartValidation.message}")
                            return@launch
                        }
                        is ValidationResult.Warning -> {
                            android.util.Log.w("SizeViewModel", "⚠️ [FIX #42] Size chart warning: ${chartValidation.message}")
                            setError(chartValidation.message)
                        }
                        ValidationResult.Success -> {
                            android.util.Log.d("SizeViewModel", "✅ [FIX #42] Size chart validation passed")
                        }
                    }
                }
                
                // ✅ FIX #41: Validate optional measurements before passing to bodyEstimator
                val validatedOptional = _optionalMeasurements.value?.let { optional ->
                    val validation = inputValidator.validateOptionalMeasurements(
                        optional,
                        input.height,
                        input.weight,
                        input.gender
                    )
                    
                    when (validation) {
                        is ValidationResult.Error -> {
                            android.util.Log.w("SizeViewModel", "⚠️ [FIX #41] Optional measurements invalid: ${validation.message}")
                            null  // Ignore invalid measurements
                        }
                        else -> optional
                    }
                }
                
                // ✅ FIX #31: Step 1 - Estimating Body
                _loadingState.value = LoadingState.EstimatingBody
                kotlinx.coroutines.delay(50) // Let UI update
                android.util.Log.d("SizeViewModel", "🔍 [FIX #31] ${LoadingState.EstimatingBody}")
                android.util.Log.d("SizeViewModel", "🔍 [Size Engine Step 2] BodyEstimator: Estimating body measurements...")
                val estimated = bodyEstimator.estimateBodyMeasurements(
                    height = input.height,
                    weight = input.weight,
                    gender = input.gender,
                    region = input.region,
                    optionalMeasurements = validatedOptional,  // ✅ FIX #41: Only pass if valid
                    garmentType = effectiveType // ✅ FIX: Pass garment type for intelligent filtering
                )
                _estimatedBody.value = estimated
                android.util.Log.d("SizeViewModel", "✅ [Size Engine Step 2] BodyEstimator complete")
                android.util.Log.d("SizeViewModel", "📊 [Size Engine] Estimated: Chest=${estimated.chest}cm, Waist=${estimated.waist}cm, Hip=${estimated.hip}cm")
                
                // ✅ ENGINE VISION FIX: Regional normalization is now handled in SizeRecommendationEngine
                // No need to normalize here - SizeRecommendationEngine will normalize before scoring
                // This prevents double normalization
                android.util.Log.d("SizeViewModel", "🔍 [Engine Vision] Skipping regional normalization in ViewModel - will be done in SizeRecommendationEngine")
                val normalizedChart = effectiveChart  // Pass effective chart (brand or standard), let SizeRecommendationEngine normalize
                
                // ✅ FIX #31: Step 3 - Scoring Fit
                _loadingState.value = LoadingState.ScoringFit
                kotlinx.coroutines.delay(50) // Let UI update
                android.util.Log.d("SizeViewModel", "🔍 [FIX #31] ${LoadingState.ScoringFit}")
                android.util.Log.d("SizeViewModel", "🔍 [Size Engine Step 4] FitScoring: Calculating fit scores...")
                
                // ✅ FIX #31: Step 4 - Generating Recommendation
                _loadingState.value = LoadingState.GeneratingRecommendation
                kotlinx.coroutines.delay(50) // Let UI update
                android.util.Log.d("SizeViewModel", "🔍 [FIX #31] ${LoadingState.GeneratingRecommendation}")
                android.util.Log.d("SizeViewModel", "🔍 [Size Engine Step 5] SizeRecommendation: Ranking sizes...")
                val recommendation = try {
                    sizeRecommendationEngine.recommendSizes(
                        userBody = estimated,
                        sizeChart = normalizedChart,
                        garmentType = effectiveType,
                        fitPreference = FitPreference.REGULAR,
                        userRegion = input.region  // ✅ ENGINE VISION: Pass user region for regional normalization
                    )
                } catch (e: IllegalArgumentException) {
                    android.util.Log.e("SizeViewModel", "❌ [Size Engine] IllegalArgumentException: ${e.message}", e)
                    setError("Failed to calculate recommendation: ${e.message}")
                    _loadingState.value = LoadingState.Idle
                    return@launch
                } catch (e: NoSuchElementException) {
                    android.util.Log.e("SizeViewModel", "❌ [Size Engine] NoSuchElementException: ${e.message}", e)
                    setError("No valid sizes found in size chart")
                    _loadingState.value = LoadingState.Idle
                    return@launch
                }
                
                android.util.Log.d("SizeViewModel", "✅ [Size Engine Step 4-5] FitScoring + SizeRecommendation complete")
                android.util.Log.d("SizeViewModel", "📊 [Size Engine] Primary recommendation: ${recommendation.primaryRecommendation.sizeEntry.sizeLabel}")
                android.util.Log.d("SizeViewModel", "📊 [Size Engine] Fit score: ${recommendation.primaryRecommendation.fitScore.overall}")
                android.util.Log.d("SizeViewModel", "✅ [Size Engine] OCR/Manual entry fully integrated with Size Engine")
                
                _recommendation.value = recommendation
                
            } catch (e: CancellationException) {
                android.util.Log.d("SizeViewModel", "✅ [BUG #54] Recommendation calculation cancelled by user")
                throw e  // Re-throw to properly cancel coroutine
            } catch (e: Exception) {
                setError("Failed to calculate recommendation: ${e.message}")
            } finally {
                _loadingState.value = LoadingState.Idle
                recommendJob = null
            }
        }
    }
    
    /**
     * ✅ BUG #62: Sanitize user input to prevent injection and parsing issues
     * Removes HTML tags, control characters, and enforces length limit
     */
    private fun sanitizeInput(text: String): String {
        val MAX_INPUT_LENGTH = 10000  // Limit to 10KB to prevent memory issues
        
        return text
            .replace(Regex("[<>]"), "")           // Remove HTML brackets
            .replace(Regex("\\p{C}"), "")         // Remove control characters
            .replace(Regex("\\p{Cc}&&[^\r\n\t]"), "")  // Remove non-printable except newline/tab
            .take(MAX_INPUT_LENGTH)                // Enforce length limit
    }
    
    /**
     * ✅ BUG #63: Validate OCR output for malicious content
     * Checks for suspicious patterns that might indicate malicious content
     */
    private fun validateOCROutput(text: String): ValidationResult {
        // Check for suspicious patterns
        val suspiciousPatterns = listOf(
            Regex("<script", RegexOption.IGNORE_CASE),
            Regex("javascript:", RegexOption.IGNORE_CASE),
            Regex("data:text/html", RegexOption.IGNORE_CASE),
            Regex("\\\\x[0-9a-f]{2}", RegexOption.IGNORE_CASE),  // Hex escape codes
            Regex("onerror\\s*=", RegexOption.IGNORE_CASE),
            Regex("onload\\s*=", RegexOption.IGNORE_CASE),
        )
        
        for (pattern in suspiciousPatterns) {
            if (pattern.containsMatchIn(text)) {
                android.util.Log.w("SizeViewModel", "⚠️ [BUG #63] Suspicious pattern detected in OCR output: ${pattern.pattern}")
                return ValidationResult.Error("Suspicious content detected in OCR output")
            }
        }
        
        // Check for reasonable content - size charts should have numbers and/or size labels
        val hasNumbers = Regex("\\d+").containsMatchIn(text)
        val hasSizeLabels = Regex("[SMLX]", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val hasHeaders = Regex("SIZE|CHEST|WAIST|HIP|LENGTH|SLEEVE", RegexOption.IGNORE_CASE).containsMatchIn(text)
        
        if (!hasNumbers && !hasSizeLabels && !hasHeaders) {
            android.util.Log.w("SizeViewModel", "⚠️ [BUG #63] OCR output doesn't look like a size chart")
            // Silenced user-facing warning
            return ValidationResult.Success // Treat as success to avoid warning UI
        }
        
        return ValidationResult.Success
    }
}
