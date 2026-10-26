package com.vanexa.supercalculator.presentation.size

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.res.stringResource
import com.vanexa.supercalculator.R
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.InputStream
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.util.Log
import com.vanexa.supercalculator.domain.model.*
import com.vanexa.ui.screens.ManualSizeChartInputScreen
import org.koin.androidx.compose.koinViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ContextThemeWrapper

import com.vanexa.supercalculator.presentation.size.SizeRecommendationBinder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeScreen(
    viewModel: SizeViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Input mode state (manual form / OCR / none)
    var inputMode by remember { mutableStateOf("none") } // "manual_form", "ocr", or "none"
    
    // ✅ Allow keyboard untuk muncul di SizeScreen - disable MainActivity hide (same as HealthScreen)
    LaunchedEffect(inputMode) {
        Log.d("SizeScreen", "SizeScreen LaunchedEffect started - disabling MainActivity keyboard hide")
        // ✅ CRITICAL: Disable MainActivity's global keyboard hiding
        // ToolsActivity handles keyboard appropriately via windowSoftInputMode
        
        // IMPORTANT:
        // - Enter Size Chart (manual_form) must NOT use ADJUST_RESIZE, otherwise it stacks with Compose IME insets
        //   and creates a large gap + janky IME animation on some OEM devices.
        // - Keep existing behavior for the rest of SizeScreen.
        if (inputMode == "manual_form") {
            activity?.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
            Log.d("SizeScreen", "Soft input mode set to ADJUST_NOTHING for manual_form")
        } else {
            // ✅ FIX: Use SOFT_INPUT_ADJUST_PAN instead of deprecated SOFT_INPUT_ADJUST_RESIZE
            @Suppress("DEPRECATION")
            activity?.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )
            Log.d("SizeScreen", "Soft input mode set to ADJUST_PAN | STATE_VISIBLE")
        }
    }
    
    // ✅ Re-enable MainActivity keyboard hiding saat SizeScreen dispose
    DisposableEffect(Unit) {
        onDispose {
            Log.d("SizeScreen", "SizeScreen disposed - re-enabling MainActivity keyboard hide")
            // ToolsActivity handles keyboard appropriately via windowSoftInputMode
        }
    }
    
    // State from ViewModel
    val userInput by viewModel.userInput.collectAsState()
    val sizeChart by viewModel.sizeChart.collectAsState()
    val garmentType by viewModel.garmentType.collectAsState()

    // ✅ FIX #2.2: Explicit null handling for effectiveGarmentType
    // TEMPORARY: Default to TSHIRT if current garment type is disabled (bottom garment) or null
    val effectiveGarmentType = when {
        garmentType == null -> GarmentType.TSHIRT  // Default if not selected
        garmentType in getEnabledGarmentTypes() -> garmentType
        else -> GarmentType.TSHIRT  // Fallback for disabled types
    }
    val recommendation by viewModel.recommendation.collectAsState()
    val errorState by viewModel.errorState.collectAsState() // ✅ FIX #22: Use structured error state
    val error by viewModel.error.collectAsState() // Legacy for backward compatibility
    val isLoading by viewModel.isLoading.collectAsState()
    // ✅ FIX #31: Progressive loading state
    val loadingState by viewModel.loadingState.collectAsState()
    val estimatedBody by viewModel.estimatedBody.collectAsState()
    val isGeneralMode by viewModel.isGeneralMode.collectAsState()
    val aiState by viewModel.aiState.collectAsState()
    
    // ✅ FIX #23: State Synchronization - Use derivedStateOf for efficient two-way binding
    // Derived state automatically syncs with ViewModel, but we use buffer for user typing
    val errMustBeNumber = stringResource(R.string.err_must_be_number)
    val errTooShort = stringResource(R.string.err_too_short)
    val errTooTall = stringResource(R.string.err_too_tall)
    val errTooLight = stringResource(R.string.err_too_light)
    val errTooHeavy = stringResource(R.string.err_too_heavy)

    var heightTextBuffer by remember { mutableStateOf("") }
    var weightTextBuffer by remember { mutableStateOf("") }
    // Legacy text state no longer used for new manual UI; keep for future OCR/manual raw if needed
    var sizeChartText by remember { mutableStateOf("") }
    
    // ✅ BUG #59: Debouncing for text input - delay ViewModel updates
    val coroutineScope = rememberCoroutineScope()
    var heightDebounceJob by remember { mutableStateOf<Job?>(null) }
    var weightDebounceJob by remember { mutableStateOf<Job?>(null) }
    
    // ✅ FIX #1.2: Sync ViewModel → UI with consistent number formatting to avoid infinite sync loop
    // Use specific keys (height, weight) instead of entire userInput to reduce recomposition
    LaunchedEffect(userInput?.height) {
        val heightFromVM = userInput?.height
        if (heightFromVM != null) {
            // Format consistently: "170" instead of "170.0" for whole numbers
            val formatted = if (heightFromVM == heightFromVM.toLong().toDouble()) {
                heightFromVM.toLong().toString()
            } else {
                String.format("%.1f", heightFromVM)
            }
            // Only update if significantly different (avoid infinite loop)
            val bufferValue = heightTextBuffer.toDoubleOrNull()
            if (bufferValue == null || kotlin.math.abs(bufferValue - heightFromVM) > 0.01) {
                heightTextBuffer = formatted
            }
        }
    }
    
    LaunchedEffect(userInput?.weight) {
        val weightFromVM = userInput?.weight
        if (weightFromVM != null) {
            // Format consistently: "70" instead of "70.0" for whole numbers
            val formatted = if (weightFromVM == weightFromVM.toLong().toDouble()) {
                weightFromVM.toLong().toString()
            } else {
                String.format("%.1f", weightFromVM)
            }
            // Only update if significantly different (avoid infinite loop)
            val bufferValue = weightTextBuffer.toDoubleOrNull()
            if (bufferValue == null || kotlin.math.abs(bufferValue - weightFromVM) > 0.01) {
                weightTextBuffer = formatted
            }
        }
    }
    
    // Short-circuit when manual form is active to avoid nested scroll constraints
    if (inputMode == "manual_form") {
        ManualSizeChartInputScreen(
            garmentType = effectiveGarmentType?.displayName() ?: "Shirt",
            onComplete = { chart ->
                viewModel.setManualSizeChart(chart)
                inputMode = "ocr" // return to selector after submit
            },
            onBack = { inputMode = "none" }
        )
        return
    }
    
    // ✅ FIX #25: Permission dialog state for actionable error handling
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // ✅ FIX #26: Real-time validation state
    data class FieldValidation(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
    
    var heightValidation by remember { mutableStateOf<FieldValidation>(FieldValidation(true)) }
    var weightValidation by remember { mutableStateOf<FieldValidation>(FieldValidation(true)) }

    // OCR Image Picker Launcher - MUST be remembered outside when block
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        Log.d("SizeScreen", "📸 [OCR Launcher] Callback received, uri=${uri != null}")
        uri?.let {
            try {
                Log.d("SizeScreen", "📸 [OCR Launcher] Image selected: $it")
                
                // Load bitmap with proper error handling and resize for performance
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                if (inputStream == null) {
                    Log.e("SizeScreen", "❌ Failed to open input stream")
                    viewModel.setError("Failed to open image. Please try again.")
                    return@let
                }
                
                // Decode with options to handle large images
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()
                
                // ✅ FIX #4.2: Optimize sample size calculation - direct calculation instead of loop
                val maxDimension = 2048
                val fullWidth = options.outWidth
                val fullHeight = options.outHeight
                val maxDim = maxOf(fullWidth, fullHeight)
                val sampleSize = if (maxDim > maxDimension) {
                    // Use highestOneBit for O(1) calculation instead of O(log n) loop
                    val ratio = maxDim / maxDimension
                    Integer.highestOneBit(ratio).coerceAtLeast(1)
                } else {
                    1
                }
                Log.d("SizeScreen", "📐 [Bitmap] Original: ${fullWidth}x${fullHeight}, sampleSize: $sampleSize")

                // Decode actual bitmap with sample size and basic OOM handling
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888 // ✅ FIX: Use ARGB_8888 for better ML Kit compatibility
                }
                
                // ✅ FIX #1.1: Memory Leak Fix - Wrap bitmap processing in try-finally
                var bitmap: Bitmap? = null
                var processingStarted = false
                try {
                    val inputStream2: InputStream? = context.contentResolver.openInputStream(it)
                    bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
                    inputStream2?.close()
                    
                    if (bitmap != null) {
                        Log.d("SizeScreen", "✅ [OCR Launcher] Bitmap loaded: ${bitmap.width}x${bitmap.height}, sampleSize=$sampleSize")
                        Log.d("SizeScreen", "🔍 [OCR Launcher] Calling viewModel.processOCRFromImage()")
                        processingStarted = true
                        viewModel.processOCRFromImage(bitmap)
                        Log.d("SizeScreen", "✅ [OCR Launcher] processOCRFromImage() called successfully")
                    } else {
                        Log.e("SizeScreen", "❌ [OCR Launcher] Bitmap is null after decoding")
                        viewModel.setError("Failed to decode image. Please try a different image.")
                    }
                } catch (decodeException: Exception) {
                    Log.e("SizeScreen", "❌ [OCR Launcher] Decode exception: ${decodeException.message}", decodeException)
                    viewModel.setError("Error decoding image: ${decodeException.message ?: "Unknown error"}")
                } finally {
                    // ✅ FIX #1.1: Recycle bitmap if processing didn't start (error before processOCR)
                    // If processingStarted is true, ViewModel is responsible for recycling
                    if (!processingStarted && bitmap != null && !bitmap.isRecycled) {
                        bitmap.recycle()
                        Log.d("SizeScreen", "🧹 [OCR Launcher] Bitmap recycled after error")
                    }
                }
            } catch (e: SecurityException) {
                Log.e("SizeScreen", "❌ SecurityException: ${e.message}", e)
                // ✅ FIX #25: Show actionable permission dialog instead of just error message
                showPermissionDialog = true
            } catch (e: Exception) {
                Log.e("SizeScreen", "❌ Error loading image: ${e.message}", e)
                viewModel.setError("Error loading image: ${e.message ?: "Unknown error"}")
            }
        } ?: run {
            Log.d("SizeScreen", "📸 Image selection cancelled")
        }
    }

    // Permission logic removed per Google Play Policy (Using Photo Picker instead)
    
    // Optional measurements state - ✅ FIX #23: State Synchronization
    val optionalMeasurements by viewModel.optionalMeasurements.collectAsState()
    var showOptional by remember { mutableStateOf(false) }
    
    // Buffer pattern for optional measurements
    var chestTextBuffer by remember { mutableStateOf("") }
    var waistTextBuffer by remember { mutableStateOf("") }
    var hipTextBuffer by remember { mutableStateOf("") }
    var shoulderTextBuffer by remember { mutableStateOf("") }
    var inseamTextBuffer by remember { mutableStateOf("") }
    
    // ✅ FIX #4.1: Optimized sync with specific keys to reduce recomposition
    // Use individual keys instead of entire optionalMeasurements object
    LaunchedEffect(
        optionalMeasurements?.chestCircumference,
        optionalMeasurements?.waistCircumference,
        optionalMeasurements?.hipCircumference,
        optionalMeasurements?.shoulderWidth,
        optionalMeasurements?.inseamLength
    ) {
        // Helper function for consistent number formatting
        fun formatOptionalValue(value: Double?): String {
            if (value == null) return ""
            return if (value == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                String.format("%.1f", value)
            }
        }
        
        // Sync chest
        val chestFormatted = formatOptionalValue(optionalMeasurements?.chestCircumference)
        val chestBufferValue = chestTextBuffer.toDoubleOrNull()
        val chestVMValue = optionalMeasurements?.chestCircumference
        if (chestVMValue == null && chestTextBuffer.isNotBlank()) {
            chestTextBuffer = ""
        } else if (chestVMValue != null && (chestBufferValue == null || kotlin.math.abs(chestBufferValue - chestVMValue) > 0.01)) {
            chestTextBuffer = chestFormatted
        }
        
        // Sync waist
        val waistFormatted = formatOptionalValue(optionalMeasurements?.waistCircumference)
        val waistBufferValue = waistTextBuffer.toDoubleOrNull()
        val waistVMValue = optionalMeasurements?.waistCircumference
        if (waistVMValue == null && waistTextBuffer.isNotBlank()) {
            waistTextBuffer = ""
        } else if (waistVMValue != null && (waistBufferValue == null || kotlin.math.abs(waistBufferValue - waistVMValue) > 0.01)) {
            waistTextBuffer = waistFormatted
        }
        
        // Sync hip
        val hipFormatted = formatOptionalValue(optionalMeasurements?.hipCircumference)
        val hipBufferValue = hipTextBuffer.toDoubleOrNull()
        val hipVMValue = optionalMeasurements?.hipCircumference
        if (hipVMValue == null && hipTextBuffer.isNotBlank()) {
            hipTextBuffer = ""
        } else if (hipVMValue != null && (hipBufferValue == null || kotlin.math.abs(hipBufferValue - hipVMValue) > 0.01)) {
            hipTextBuffer = hipFormatted
        }
        
        // Sync shoulder
        val shoulderFormatted = formatOptionalValue(optionalMeasurements?.shoulderWidth)
        val shoulderBufferValue = shoulderTextBuffer.toDoubleOrNull()
        val shoulderVMValue = optionalMeasurements?.shoulderWidth
        if (shoulderVMValue == null && shoulderTextBuffer.isNotBlank()) {
            shoulderTextBuffer = ""
        } else if (shoulderVMValue != null && (shoulderBufferValue == null || kotlin.math.abs(shoulderBufferValue - shoulderVMValue) > 0.01)) {
            shoulderTextBuffer = shoulderFormatted
        }
        
        // Sync inseam
        val inseamFormatted = formatOptionalValue(optionalMeasurements?.inseamLength)
        val inseamBufferValue = inseamTextBuffer.toDoubleOrNull()
        val inseamVMValue = optionalMeasurements?.inseamLength
        if (inseamVMValue == null && inseamTextBuffer.isNotBlank()) {
            inseamTextBuffer = ""
        } else if (inseamVMValue != null && (inseamBufferValue == null || kotlin.math.abs(inseamBufferValue - inseamVMValue) > 0.01)) {
            inseamTextBuffer = inseamFormatted
        }
    }
    
    // ✅ FIX #3.2: Remember scroll state for auto-scroll to recommendation
    val scrollState = rememberScrollState()
    
    // ✅ FIX #3.2: Auto-scroll when recommendation appears
    LaunchedEffect(recommendation) {
        if (recommendation != null) {
            // Small delay to ensure layout is complete
            delay(100L)
            // Scroll to bottom where recommendation is shown
            scrollState.animateScrollTo(scrollState.maxValue)
            Log.d("SizeScreen", "📜 [Auto-Scroll] Scrolled to recommendation")
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Body Measurements Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.body_measurements), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Height - Mandatory with ✅ FIX #26: Real-time validation
                OutlinedTextField(
                    value = heightTextBuffer,
                    onValueChange = { text ->
                        heightTextBuffer = text
                        val height = text.toDoubleOrNull()
                        
                        // Real-time validation
                        val validation = when {
                            height == null && text.isNotBlank() -> 
                                FieldValidation(false, errMustBeNumber)
                            height != null && height < 130.0 -> 
                                FieldValidation(false, errTooShort)
                            height != null && height > 230.0 -> 
                                FieldValidation(false, errTooTall)
                            else -> FieldValidation(true)
                        }
                        
                        heightValidation = validation
                        
                        // ✅ BUG #59: Cancel previous debounce job
                        heightDebounceJob?.cancel()
                        
                        // ✅ BUG #59: Schedule debounced ViewModel update (500ms delay)
                        if (validation.isValid && height != null) {
                            heightDebounceJob = coroutineScope.launch {
                                delay(500L)
                                viewModel.setHeight(height)
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.height)) },
                    suffix = { Text(stringResource(R.string.unit_cm)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !heightValidation.isValid,
                    supportingText = heightValidation.errorMessage?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                showKeyboardSafely(activity)
                            }
                        },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Weight - Mandatory with ✅ FIX #26: Real-time validation
                OutlinedTextField(
                    value = weightTextBuffer,
                    onValueChange = { text ->
                        weightTextBuffer = text
                        val weight = text.toDoubleOrNull()
                        
                        // Real-time validation
                        // ✅ BUG #8: Fixed - max weight was 200kg but BodyEstimator allows 300kg
                        val validation = when {
                            weight == null && text.isNotBlank() -> 
                                FieldValidation(false, errMustBeNumber)
                            weight != null && weight < 30.0 -> 
                                FieldValidation(false, errTooLight)
                            weight != null && weight > 300.0 -> 
                                FieldValidation(false, errTooHeavy)
                            else -> FieldValidation(true)
                        }
                        
                        weightValidation = validation
                        
                        // ✅ BUG #59: Cancel previous debounce job
                        weightDebounceJob?.cancel()
                        
                        // ✅ BUG #59: Schedule debounced ViewModel update (500ms delay)
                        if (validation.isValid && weight != null) {
                            weightDebounceJob = coroutineScope.launch {
                                delay(500L)
                                viewModel.setWeight(weight)
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.weight)) },
                    suffix = { Text(stringResource(R.string.unit_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !weightValidation.isValid,
                    supportingText = weightValidation.errorMessage?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                showKeyboardSafely(activity)
                            }
                        },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Gender
                Text(stringResource(R.string.gender), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Gender.values().forEach { g ->
                        FilterChip(
                            selected = userInput?.gender == g,
                            onClick = { viewModel.setGender(g) },
                            label = { Text(g.name) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (g == Gender.MALE) Icons.Default.Male else Icons.Default.Female,
                                    contentDescription = g.name,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Region
                Text(stringResource(R.string.fit_region), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SizeRegion.values().size) { index ->
                        val region = SizeRegion.values()[index]
                        FilterChip(
                            selected = userInput?.region == region,
                            onClick = { viewModel.setRegion(region) },
                            label = { Text(region.name) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Optional Measurements - Collapsible (PRD Section 3.2 & 10.3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.optional_measurements),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.optional_boost_accuracy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Switch(
                        checked = showOptional,
                        onCheckedChange = { showOptional = it }
                    )
                }
                
                AnimatedVisibility(
                    visible = showOptional,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Info card explaining benefits (PRD Section 10.3)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.optional_info_text),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        // Optional Measurement Fields per PRD
                        OptionalMeasurementField(
                            label = stringResource(R.string.chest_circumference),
                            hint = stringResource(R.string.chest_hint),
                            accuracyBoost = "+12%",
                            icon = Icons.Default.Straighten,
                            value = chestTextBuffer,
                            onValueChange = { 
                                chestTextBuffer = it
                                updateOptionalMeasurements(viewModel, chestTextBuffer, waistTextBuffer, hipTextBuffer, shoulderTextBuffer, inseamTextBuffer)
                            },
                            activity = activity
                        )
                        
                        OptionalMeasurementField(
                            label = stringResource(R.string.waist_circumference),
                            hint = stringResource(R.string.waist_hint),
                            accuracyBoost = "+10%",
                            icon = Icons.Default.Straighten,
                            value = waistTextBuffer,
                            onValueChange = { 
                                waistTextBuffer = it
                                updateOptionalMeasurements(viewModel, chestTextBuffer, waistTextBuffer, hipTextBuffer, shoulderTextBuffer, inseamTextBuffer)
                            },
                            activity = activity
                        )
                        
                        OptionalMeasurementField(
                            label = stringResource(R.string.hip_circumference),
                            hint = stringResource(R.string.hip_hint),
                            accuracyBoost = "+8%",
                            icon = Icons.Default.Straighten,
                            value = hipTextBuffer,
                            onValueChange = { 
                                hipTextBuffer = it
                                updateOptionalMeasurements(viewModel, chestTextBuffer, waistTextBuffer, hipTextBuffer, shoulderTextBuffer, inseamTextBuffer)
                            },
                            activity = activity
                        )
                        
                        OptionalMeasurementField(
                            label = stringResource(R.string.shoulder_width),
                            hint = stringResource(R.string.shoulder_hint),
                            accuracyBoost = "+5%",
                            icon = Icons.Default.Height,
                            value = shoulderTextBuffer,
                            onValueChange = { 
                                shoulderTextBuffer = it
                                updateOptionalMeasurements(viewModel, chestTextBuffer, waistTextBuffer, hipTextBuffer, shoulderTextBuffer, inseamTextBuffer)
                            },
                            activity = activity
                        )
                        
                        OptionalMeasurementField(
                            label = stringResource(R.string.inseam_length),
                            hint = stringResource(R.string.inseam_hint),
                            accuracyBoost = "+3%",
                            icon = Icons.Default.Height,
                            value = inseamTextBuffer,
                            onValueChange = { 
                                inseamTextBuffer = it
                                updateOptionalMeasurements(viewModel, chestTextBuffer, waistTextBuffer, hipTextBuffer, shoulderTextBuffer, inseamTextBuffer)
                            },
                            activity = activity
                        )
                        
                        // Measurement guide button (PRD Section 10.3)
                        var showMeasurementGuide by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { showMeasurementGuide = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Help icon for measurement guide",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.how_to_measure))
                        }
                        
                        // Measurement Guide Dialog
                        if (showMeasurementGuide) {
                            MeasurementGuideDialog(
                                onDismiss = { showMeasurementGuide = false }
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Garment Type Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.garment_type), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                // ✅ TEMPORARY: Disable bottom garments for initial TOP-only release
                // ✅ TEMPORARY: Disable bottom garments for initial TOP-only release
                val enabledGarmentTypes = getEnabledGarmentTypes()
                
                // ✅ 1. State for robust auto-scroll
                val garmentScrollState = rememberScrollState()
                val garmentTabPositions = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Pair<Int, Int>>() }
                
                // ✅ 2. BoxWithConstraints for correct viewport measurement
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val viewportWidth = constraints.maxWidth

                    // ✅ Helper to perform scroll
                    fun scrollToGarment(index: Int) {
                        val layout = garmentTabPositions[index]
                        if (layout != null && viewportWidth > 0) {
                            val (tabX, tabWidth) = layout
                            val tabCenter = tabX + tabWidth / 2
                            val viewportCenter = viewportWidth / 2
                            val targetScroll = (tabCenter - viewportCenter).coerceAtLeast(0)
                            coroutineScope.launch {
                                garmentScrollState.animateScrollTo(targetScroll)
                            }
                        }
                    }

                    // ✅ Auto-scroll when selection changes
                    LaunchedEffect(effectiveGarmentType, garmentTabPositions.size) {
                        if (garmentTabPositions.size == enabledGarmentTypes.size) {
                            val index = enabledGarmentTypes.indexOf(effectiveGarmentType)
                            if (index >= 0) {
                                scrollToGarment(index)
                            }
                        }
                    }

                    // ✅ Initial load scroll
                    LaunchedEffect(garmentTabPositions.size) {
                        if (garmentTabPositions.size == enabledGarmentTypes.size) {
                            val index = enabledGarmentTypes.indexOf(effectiveGarmentType)
                            if (index >= 0) {
                                scrollToGarment(index)
                            }
                        }
                    }

                    // ✅ Scrollable Row
                    Row(
                        modifier = Modifier.horizontalScroll(garmentScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        enabledGarmentTypes.forEachIndexed { index, type ->
                            Box(
                                modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
                                    val xPos = layoutCoordinates.positionInParent().x.toInt()
                                    val width = layoutCoordinates.size.width
                                    val current = garmentTabPositions[index]
                                    if (current?.first != xPos || current?.second != width) {
                                        garmentTabPositions[index] = xPos to width
                                    }
                                }
                            ) {
                                FilterChip(
                                    selected = effectiveGarmentType == type,
                                    onClick = {
                                        if (type in enabledGarmentTypes) {
                                            viewModel.setGarmentType(type)
                                            // ✅ Immediate Scroll on Click
                                            val layout = garmentTabPositions[index]
                                            if (layout != null && viewportWidth > 0) {
                                                val (tabX, tabWidth) = layout
                                                val tabCenter = tabX + tabWidth / 2
                                                val viewportCenter = viewportWidth / 2
                                                val targetScroll = (tabCenter - viewportCenter).coerceAtLeast(0)
                                                coroutineScope.launch {
                                                    garmentScrollState.animateScrollTo(targetScroll)
                                                }
                                            }
                                        }
                                    },
                                    label = { Text(type.displayName()) },  // ✅ FIX #6: Use user-friendly displayName()
                                    leadingIcon = {
                                        Icon(
                                            imageVector = getGarmentIcon(type),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (false) {
            // Size Chart Input Card with OCR
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Size Chart", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Input Mode Selector
                    
                    // Clear error when switching modes
                    // ✅ FIX #32: Clear size chart text when switching to OCR mode
                    LaunchedEffect(inputMode) {
                        if (inputMode == "ocr") {
                            sizeChartText = ""  // Clear manual entry when switching to OCR
                            // Clear error when switching to OCR mode if no chart exists yet
                            // Error will show if OCR fails or if user tries to calculate without chart
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = inputMode == "manual_form",
                            onClick = { 
                                inputMode = "manual_form"
                                // ✅ FIX #34: Clear error when switching to manual mode
                                viewModel.clearError()
                            },
                            label = { Text("Manual Entry") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "Manual entry mode") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = inputMode == "ocr",
                            onClick = { 
                                inputMode = "ocr"
                                // ✅ FIX #34: Clear error when switching to OCR mode
                                viewModel.clearError()
                            },
                            label = { Text("Scan Chart") },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = "OCR scan mode") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    when (inputMode) {
                        "manual_form" -> {
                            ManualSizeChartInputScreen(
                                garmentType = effectiveGarmentType?.displayName() ?: "Shirt",
                                onComplete = { chart ->
                                    viewModel.setManualSizeChart(chart)
                                },
                                onBack = { inputMode = "none" }
                            )
                        }
                        "ocr" -> {
                            // Info card for OCR mode
                            if (sizeChart.isEmpty() && !isLoading && error?.contains("Size chart is empty") != true) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Size chart OCR scanning information",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Select an image of the size chart to extract measurements automatically",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    Log.d("SizeScreen", "📸 [OCR Button] Select Image button clicked")
                                    // Launch the Photo Picker (Permissionless, complies with Policy 16935362)
                                    imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Open gallery to select size chart image",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isLoading) "Processing..." else "Select Image from Gallery")
                            }
                            
                            if (isLoading) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("Processing size chart on this device...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        else -> {
                            Text(
                                "Select an input mode to start (Manual Entry or Scan Chart)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // ✅ FIX #3.1: Add visual indicator for current state
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            sizeChart.isNotEmpty() -> "✓ Size chart loaded (${sizeChart.size} sizes)"
                            inputMode == "ocr" -> "Select an image to scan"
                            inputMode == "manual_form" -> "Enter measurements manually"
                            else -> "Enter measurements below for an accurate estimate"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sizeChart.isNotEmpty()) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Calculate Button
        // ✅ FIX #4: Use consistent loading state for both enable and label (derived from loadingState)
        val isBusy = loadingState != SizeViewModel.LoadingState.Idle
        val buttonEnabled = userInput != null && !isBusy
        LaunchedEffect(userInput, sizeChart.size, effectiveGarmentType, loadingState) {
            Log.d("SizeScreen", "🔍 [Button State] userInput=${userInput != null}, sizeChart=${sizeChart.size}, garmentType=${effectiveGarmentType != null}, isBusy=$isBusy, enabled=$buttonEnabled")
        }
        Button(
            onClick = {
                Log.d("SizeScreen", "🔘 [Button Clicked] Get Recommendation button clicked")
                Log.d("SizeScreen", "📊 [DEBUG] Current userInput: height=${userInput?.height}cm, weight=${userInput?.weight}kg, gender=${userInput?.gender}, region=${userInput?.region}")
                Log.d("SizeScreen", "📊 [DEBUG] Current textBuffers: height='$heightTextBuffer', weight='$weightTextBuffer'")

                // ✅ FIX #2.1: Cancel any pending debounce jobs to prevent race condition
                heightDebounceJob?.cancel()
                weightDebounceJob?.cancel()
                Log.d("SizeScreen", "🛑 [SYNC] Cancelled pending debounce jobs")

                // Immediate sync: ensure latest typed values are applied to ViewModel
                val heightBuf = heightTextBuffer.trim()
                val weightBuf = weightTextBuffer.trim()
                val parsedHeight = heightBuf.toDoubleOrNull()
                val parsedWeight = weightBuf.toDoubleOrNull()

                if (parsedHeight != null) {
                    val currentHeight = userInput?.height
                    if (currentHeight == null || currentHeight != parsedHeight) {
                        Log.d("SizeScreen", "📌 [SYNC] Applying height from buffer: $parsedHeight")
                        viewModel.setHeight(parsedHeight)
                    }
                }

                if (parsedWeight != null) {
                    val currentWeight = userInput?.weight
                    if (currentWeight == null || currentWeight != parsedWeight) {
                        Log.d("SizeScreen", "📌 [SYNC] Applying weight from buffer: $parsedWeight")
                        viewModel.setWeight(parsedWeight)
                    }
                }

                viewModel.calculateRecommendation()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = buttonEnabled
        ) {
            // ✅ FIX #31: Progressive loading feedback (consistent with isBusy check)
            val buttonText = when (loadingState) {
                is SizeViewModel.LoadingState.EstimatingBody -> "Analyzing your body measurements..."
                is SizeViewModel.LoadingState.NormalizingChart -> "Adjusting for regional standards..."
                is SizeViewModel.LoadingState.ScoringFit -> "Calculating fit scores..."
                is SizeViewModel.LoadingState.GeneratingRecommendation -> "Finalizing recommendation..."
                is SizeViewModel.LoadingState.Idle -> "Get Your General Size"
            }
            Text(buttonText)
        }
        
        // ✅ FIX #22: Structured Error Display - Explicit error states, no complex conditionals
        when (val state = errorState) {
            is SizeViewModel.ErrorState.None -> { /* No error card */ }
            is SizeViewModel.ErrorState.Warning -> {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.06f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 3
                        )
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss warning",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            is SizeViewModel.ErrorState.Error -> {
                // ✅ FIX #3.3: Get actionable error with hint
                val (errorMsg, actionHint) = getActionableError(state.message)
                
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.06f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = "Error: $errorMsg",
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    errorMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss error",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        // ✅ FIX #3.3: Show actionable hint if available
                        if (actionHint != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = actionHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 30.dp) // Align with error text
                            )
                        }
                    }
                }
            }
            is SizeViewModel.ErrorState.Critical -> {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.06f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = "${stringResource(R.string.critical_error)}: ${state.message}",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.dismiss_error),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Recommendation Result - Enhanced per PRD Section 10.4
        recommendation?.let { result ->
            Spacer(modifier = Modifier.height(16.dp))
            
            // General mode info banner
            if (isGeneralMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "General size info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Your Estimated Size — based on international sizing standards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // ✅ GARMENT-AWARE DEVIATION: Top garments always use CHEST deviation
            val keyDimension = getGarmentAwareKeyDimension(
                garmentType = effectiveGarmentType,
                scores = result.primaryRecommendation.fitScore.dimensionScores
            )

            // ✅ SYNCHRONIZE DEVIATION: Get actual deviation from dimension scores
            val dimensionScores = result.primaryRecommendation.fitScore.dimensionScores

            // ✅ DEBUG: Log available dimension scores and key dimension
            Log.d("SizeScreen", "🎯 [DEVIATION DEBUG] keyDimension: $keyDimension")
            Log.d("SizeScreen", "🎯 [DEVIATION DEBUG] available dimensions: ${dimensionScores.keys}")
            dimensionScores.forEach { (dim, score) ->
                Log.d("SizeScreen", "🎯 [DEVIATION DEBUG] $dim -> difference: ${score.difference}cm")
            }

            // ✅ Get deviation for the selected key dimension (guaranteed to exist due to smart selection)
            val keyDeviationRaw = dimensionScores[keyDimension]?.difference ?: 0.0
            val keyDeviation = keyDeviationRaw.takeIf { it.isFinite() } ?: 0.0

            Log.d("SizeScreen", "🎯 [DEVIATION DEBUG] keyDeviation for $keyDimension: ${keyDeviation}cm")
            // ✅ GARMENT-AWARE LABEL: CHEST → "Chest deviation", fallback → "Fit deviation"
            val deviationLabel = when (keyDimension) {
                BodyDimension.CHEST -> stringResource(R.string.chest_deviation)
                else -> stringResource(R.string.fit_deviation)
            }

            val rawPrimaryMessage = result.betweenSizesInfo?.guidance ?: result.primaryRecommendation.fitScore.recommendation
            val primaryMessage = sanitizeFitMessage(
                raw = rawPrimaryMessage,
                garmentType = effectiveGarmentType,
                keyDimension = keyDimension
            )
            
            // Scientific dark-mode card via XML + binder
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    val themed = ContextThemeWrapper(
                        context,
                        com.google.android.material.R.style.Theme_Material3_Dark
                    )
                    LayoutInflater.from(themed).inflate(R.layout.item_size_recommendation, null)
                },
                update = { view ->
                    SizeRecommendationBinder.bind(
                        root = view,
                        size = result.primaryRecommendation.sizeEntry.sizeLabel,
                        deviation = keyDeviation.toFloat(),
                        message = primaryMessage,
                        deviationLabel = deviationLabel,
                        fitScore = result.primaryRecommendation.fitScore.overall
                    )
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
    
            // ✅ FIX #33: Alternative Sizes with Context
            if (result.alternativeRecommendations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.alternative_sizes),
                    style = MaterialTheme.typography.titleMedium
                )
                result.alternativeRecommendations.forEach { alt ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                alt.sizeEntry.sizeLabel,
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            // ✅ FIX #33: Add context explaining why this is an alternative
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                generateAlternativeContext(
                    primary = result.primaryRecommendation,
                    alternative = alt,
                    garmentType = effectiveGarmentType
                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Recommendation Text
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                val bubbleText =                 formatRecommendationBubbleText(
                    raw = result.primaryRecommendation.fitScore.recommendation,
                    isAlternativeContext = result.alternativeRecommendations.isNotEmpty(),
                    garmentType = effectiveGarmentType,
                    keyDimension = keyDimension
                )
                Text(
                    bubbleText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

        }
        
        // ✅ FIX #30: Estimated Body Measurements (only visible in debug builds)
        val showDebugInfo by viewModel.showDebugInfo.collectAsState()
        if (showDebugInfo) {
            estimatedBody?.let { body ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.estimated_body),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(stringResource(R.string.estimated_body), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Chest: ${String.format("%.1f", body.chest)} cm", style = MaterialTheme.typography.bodySmall)
                        Text("Waist: ${String.format("%.1f", body.waist)} cm", style = MaterialTheme.typography.bodySmall)
                        Text("Hip: ${String.format("%.1f", body.hip)} cm", style = MaterialTheme.typography.bodySmall)
                        Text("Shoulder: ${String.format("%.1f", body.shoulder)} cm", style = MaterialTheme.typography.bodySmall)
                        Text("Inseam: ${String.format("%.1f", body.inseam)} cm", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // AI Disclaimer
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ai_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }

    // ✅ FIX #25: Permission Dialog - Actionable error handling
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { 
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = stringResource(R.string.permission_storage_title),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(stringResource(R.string.permission_storage_title))
                }
            },
            text = { 
                Text(stringResource(R.string.permission_storage_desc))
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Open app settings
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", context.packageName, null)
                        context.startActivity(intent)
                        showPermissionDialog = false
                        Log.d("SizeScreen", "✅ [FIX #25] Opening app settings for permission")
                    }
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * UI-only formatter for the result bubble under "Alternative Sizes".
 * Requirements:
 * - Must clarify the bubble is explaining the alternative size context.
 * - Must not mention any percentages (e.g. "(1% fit probability)", "(35%)").
 * - Must not change business/scoring logic; only presentation.
 */
private fun formatRecommendationBubbleText(
    raw: String,
    isAlternativeContext: Boolean,
    garmentType: GarmentType?,
    keyDimension: BodyDimension
): String {
    val sanitized = sanitizeFitMessage(
        raw = raw,
        garmentType = garmentType,
        keyDimension = keyDimension
    )

    // Remove common percentage patterns while keeping the explanation text intact.
    val noPercent = sanitized
        // "(1% fit probability)" / "(99% fit probability)"
        .replace(Regex("\\(\\s*\\d+\\s*%\\s*fit probability\\s*\\)", RegexOption.IGNORE_CASE), "")
        // Any "(35%)" style fragments
        .replace(Regex("\\(\\s*\\d+\\s*%\\s*\\)"), "")
        // Any stray " 35% " tokens (rare but possible)
        .replace(Regex("\\b\\d+\\s*%\\b"), "")
        // Clean up doubled spaces around removals
        .replace(Regex("\\s{2,}"), " ")
        // Clean up punctuation spacing
        .replace(" -", " -")
        .trim()

    return if (isAlternativeContext) {
        "Alternative size note: $noPercent"
    } else {
        noPercent
    }
}

/**
 * ✅ TEMPORARY: Get only enabled garment types (TOP garments only for initial release)
 * TODO: Remove this function and enable all garment types when bottom garments are ready
 */
private fun getEnabledGarmentTypes(): List<GarmentType> {
    return listOf(
        GarmentType.TSHIRT,
        GarmentType.SHIRT,
        GarmentType.POLO_SHIRT,
        GarmentType.HOODIE,
        GarmentType.SWEATER,
        GarmentType.JACKET,
        GarmentType.BLAZER
    )
}

/**
 * ✅ GARMENT-AWARE DEVIATION: Smart mapping prioritizing CHEST for top garments when available.
 * Top garments prefer CHEST, but fall back to best available dimension if CHEST data missing.
 */
private fun getGarmentAwareKeyDimension(
    garmentType: GarmentType?,
    scores: Map<BodyDimension, DimensionScore>
): BodyDimension {
    val topGarments = setOf(
        GarmentType.TSHIRT,
        GarmentType.SHIRT,
        GarmentType.POLO_SHIRT,
        GarmentType.HOODIE,
        GarmentType.SWEATER,
        GarmentType.JACKET,
        GarmentType.BLAZER
    )

    // ✅ For top garments, prefer CHEST if CHEST data is available
    if (garmentType in topGarments && scores.containsKey(BodyDimension.CHEST)) {
        return BodyDimension.CHEST  // ✅ Use CHEST when data available
    }

    // ✅ For top garments without CHEST data, or non-top garments, use intelligent selection
    return selectKeyDimensionForRecommendation(garmentType, scores)
}

/**
 * Pick the single "key" dimension to summarize a recommendation in UI.
 * Goal: avoid misleading focus (e.g., talking about waist for tops).
 */
private fun selectKeyDimensionForRecommendation(
    garmentType: GarmentType?,
    scores: Map<BodyDimension, DimensionScore>
): BodyDimension {
    val preferredOrder = when (garmentType) {
        GarmentType.SHIRT, GarmentType.JACKET, GarmentType.BLAZER -> listOf(
            BodyDimension.CHEST,
            BodyDimension.SHOULDER,
            BodyDimension.SLEEVE,
            BodyDimension.LENGTH,
            BodyDimension.WAIST
        )
        GarmentType.TSHIRT, GarmentType.POLO_SHIRT, GarmentType.HOODIE, GarmentType.SWEATER -> listOf(
            BodyDimension.CHEST,
            BodyDimension.SHOULDER,
            BodyDimension.SLEEVE,
            BodyDimension.LENGTH,
            BodyDimension.WAIST
        )
        GarmentType.JEANS, GarmentType.CHINOS, GarmentType.TROUSERS, GarmentType.CARGO_PANTS, GarmentType.SHORTS, GarmentType.JOGGERS -> listOf(
            BodyDimension.WAIST,
            BodyDimension.HIP,
            BodyDimension.INSEAM
        )
        null -> listOf(
            BodyDimension.CHEST,
            BodyDimension.WAIST,
            BodyDimension.HIP,
            BodyDimension.SHOULDER,
            BodyDimension.SLEEVE,
            BodyDimension.LENGTH,
            BodyDimension.INSEAM
        )
    }

    // Prefer first available preferred dimension.
    preferredOrder.firstOrNull { scores.containsKey(it) }?.let { return it }

    // Fallback: choose dimension with the largest absolute difference.
    return scores.entries
        .maxByOrNull { kotlin.math.abs(it.value.difference) }
        ?.key
        ?: BodyDimension.CHEST
}

/**
 * UI-only sanitizer: remove dimension-specific sentences that are irrelevant for the garment category.
 * This prevents misleading copy like "waist" for tops.
 */
private fun sanitizeFitMessage(
    raw: String,
    garmentType: GarmentType?,
    keyDimension: BodyDimension
): String {
    val isTop = garmentType in setOf(
        GarmentType.TSHIRT, GarmentType.SHIRT, GarmentType.POLO_SHIRT,
        GarmentType.HOODIE, GarmentType.SWEATER, GarmentType.JACKET, GarmentType.BLAZER
    )
    val isBottom = garmentType in setOf(
        GarmentType.JEANS, GarmentType.TROUSERS, GarmentType.CHINOS,
        GarmentType.CARGO_PANTS, GarmentType.SHORTS, GarmentType.JOGGERS
    )

    val forbidden = when {
        // For tops, avoid bottom-only anatomy terms.
        isTop -> setOf("waist", "hip", "inseam", "outseam", "thigh", "rise", "opening")
        // For bottoms, avoid top-only anatomy terms.
        isBottom -> setOf("chest", "shoulder", "sleeve", "length")
        else -> emptySet()
    }.toMutableSet()

    // Always keep mentions of the chosen key dimension.
    forbidden.remove(keyDimension.displayName())

    if (forbidden.isEmpty()) return raw

    val sentences = raw.split(Regex("(?<=[.!?])\\s+"))
    val kept = sentences.filter { sentence ->
        val s = sentence.lowercase()
        forbidden.none { it in s }
    }.joinToString(" ").replace(Regex("\\s{2,}"), " ").trim()

    return kept.ifBlank { raw }
}

/**
 * ✅ FIX #3.3: Map error messages to actionable hints
 */
private fun getActionableError(error: String): Pair<String, String?> {
    return when {
        error.contains("Size chart is empty", ignoreCase = true) || 
        error.contains("size chart", ignoreCase = true) -> 
            error to "Provide more body measurements in the Body Measurements section"
        error.contains("body measurements", ignoreCase = true) ||
        error.contains("height", ignoreCase = true) ||
        error.contains("weight", ignoreCase = true) -> 
            error to "Enter your height and weight in the Body Measurements section"
        error.contains("garment type", ignoreCase = true) -> 
            error to "Select a garment type from the options above"
        else -> error to null
    }
}

/**
 * ✅ FIX #2.4: Validation data class for optional measurements
 */
private data class OptionalFieldValidation(
    val isValid: Boolean,
    val errorMessage: String? = null
)

/**
 * ✅ FIX #2.4: Validation ranges for optional measurements
 */
private fun validateOptionalMeasurement(label: String, value: Double?): OptionalFieldValidation {
    if (value == null) return OptionalFieldValidation(true) // Empty is valid (optional)
    
    return when {
        label.contains("Chest", ignoreCase = true) -> when {
            value < 60.0 -> OptionalFieldValidation(false, "Too small (min: 60cm)")
            value > 150.0 -> OptionalFieldValidation(false, "Too large (max: 150cm)")
            else -> OptionalFieldValidation(true)
        }
        label.contains("Waist", ignoreCase = true) -> when {
            value < 50.0 -> OptionalFieldValidation(false, "Too small (min: 50cm)")
            value > 140.0 -> OptionalFieldValidation(false, "Too large (max: 140cm)")
            else -> OptionalFieldValidation(true)
        }
        label.contains("Hip", ignoreCase = true) -> when {
            value < 60.0 -> OptionalFieldValidation(false, "Too small (min: 60cm)")
            value > 160.0 -> OptionalFieldValidation(false, "Too large (max: 160cm)")
            else -> OptionalFieldValidation(true)
        }
        label.contains("Shoulder", ignoreCase = true) -> when {
            value < 30.0 -> OptionalFieldValidation(false, "Too small (min: 30cm)")
            value > 60.0 -> OptionalFieldValidation(false, "Too large (max: 60cm)")
            else -> OptionalFieldValidation(true)
        }
        label.contains("Inseam", ignoreCase = true) -> when {
            value < 60.0 -> OptionalFieldValidation(false, "Too short (min: 60cm)")
            value > 100.0 -> OptionalFieldValidation(false, "Too long (max: 100cm)")
            else -> OptionalFieldValidation(true)
        }
        else -> OptionalFieldValidation(true)
    }
}

/**
 * Optional Measurement Field Component per PRD Section 10.3
 * ✅ FIX #2.4: Added validation similar to height/weight
 */
@Composable
private fun OptionalMeasurementField(
    label: String,
    hint: String,
    accuracyBoost: String,
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    activity: ComponentActivity?
) {
    // ✅ FIX #2.4: Add validation state
    var validation by remember { mutableStateOf(OptionalFieldValidation(true)) }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = accuracyBoost,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                // ✅ FIX #2.4: Validate on change
                val parsedValue = newValue.toDoubleOrNull()
                validation = if (newValue.isNotBlank() && parsedValue == null) {
                    OptionalFieldValidation(false, "Must be a number")
                } else {
                    validateOptionalMeasurement(label, parsedValue)
                }
                
                // Only propagate valid values
                if (validation.isValid) {
                    onValueChange(newValue)
                } else {
                    // Still update UI but don't propagate to ViewModel
                    onValueChange(newValue)
                }
            },
            label = { Text(hint) },
            suffix = { Text("cm") },
            leadingIcon = { 
                Icon(
                    imageVector = icon,
                    contentDescription = "Measurement icon for $hint",
                    modifier = Modifier.size(20.dp)
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !validation.isValid,
            supportingText = validation.errorMessage?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        showKeyboardSafely(activity)
                    }
                },
            singleLine = true
        )
    }
}

/**
 * Helper function to update optional measurements in ViewModel
 */
private fun updateOptionalMeasurements(
    viewModel: SizeViewModel,
    chestText: String,
    waistText: String,
    hipText: String,
    shoulderText: String,
    inseamText: String
) {
    val measurements = OptionalMeasurements(
        chestCircumference = chestText.toDoubleOrNull(),
        waistCircumference = waistText.toDoubleOrNull(),
        hipCircumference = hipText.toDoubleOrNull(),
        shoulderWidth = shoulderText.toDoubleOrNull(),
        inseamLength = inseamText.toDoubleOrNull()
    )
    viewModel.setOptionalMeasurements(measurements)
}

/**
 * ✅ FIX #21: Unified, robust keyboard handler
 * Uses ONLY WindowInsetsController (modern API, most reliable)
 * Removed: CoroutineScope, delay(), redundant IMM calls
 * ✅ FIX: Removed unused keyboardController parameter
 */
private fun showKeyboardSafely(
    activity: ComponentActivity?
) {
    activity?.let { act ->
        // Use ONLY WindowInsetsController (modern API, most reliable)
        val windowInsetsController = WindowCompat.getInsetsController(
            act.window, 
            act.window.decorView
        )
        windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.ime())
        
        Log.d("SizeScreen", "✅ Keyboard shown via WindowInsetsController")
    }
}

/**
 * Measurement Guide Dialog per PRD Section 10.3
 */
@Composable
private fun MeasurementGuideDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to Measure Your Body") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                item {
                    MeasurementInstruction(
                        title = "Chest / Bust Circumference",
                        steps = listOf(
                            "Stand straight with arms relaxed at sides",
                            "Wrap tape around fullest part of chest",
                            "Keep the tape level and parallel to the floor",
                            "Breathe normally; do not hold your breath"
                        ),
                        icon = ""
                    )
                }
                
                item {
                    MeasurementInstruction(
                        title = "Waist Circumference",
                        steps = listOf(
                            "Find your natural waistline (narrowest part)",
                            "Usually about 2-3 cm above the belly button",
                            "Wrap the tape snugly but not tight",
                            "Stand straight; do not suck in your stomach"
                        ),
                        icon = ""
                    )
                }
                
                item {
                    MeasurementInstruction(
                        title = "Hip Circumference",
                        steps = listOf(
                            "Measure around fullest part of hips/buttocks",
                            "Keep feet together",
                            "Tape should be level and parallel to the floor",
                            "Avoid pulling the tape too tight"
                        ),
                        icon = ""
                    )
                }
                
                item {
                    MeasurementInstruction(
                        title = "Shoulder Width",
                        steps = listOf(
                            "Best to have someone help you",
                            "Measure across back from shoulder bone to shoulder bone",
                            "Keep shoulders relaxed, not raised"
                        ),
                        icon = ""
                    )
                }
                
                item {
                    MeasurementInstruction(
                        title = "Inseam Length",
                        steps = listOf(
                            "Measure from crotch to bottom of ankle",
                            "Best done while wearing fitted pants",
                            "Stand straight, feet slightly apart"
                        ),
                        icon = ""
                    )
                }
                
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Pro Tips",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Use a flexible measuring tape.", style = MaterialTheme.typography.bodySmall)
                            Text("Measure over fitted clothing or bare skin.", style = MaterialTheme.typography.bodySmall)
                            Text("Get assistance for best accuracy.", style = MaterialTheme.typography.bodySmall)
                            Text("Measure at the same time of day.", style = MaterialTheme.typography.bodySmall)
                            Text("Take 2-3 measurements and average them.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Dimension Score Row Component per PRD Section 10.4
 */
@Composable
private fun DimensionScoreRow(
    dimension: String,
    score: DimensionScore
) {
    // ✅ BUG #65: Add semantic content for screen readers
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                // Combine all content for screen readers
                contentDescription = "${dimension.replaceFirstChar { it.uppercase() }} dimension score: ${(score.value * 100).toInt()} percent, status: ${score.status.name.lowercase().replace("_", " ")}"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            dimension.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = score.value.toFloat(),
                modifier = Modifier
                    .width(100.dp)
                    .height(8.dp)
                    .semantics { 
                        // Hide from screen reader (already in parent)
                        contentDescription = "" 
                    },
                color = when (score.status) {
                    FitStatus.EXCELLENT -> Color(0xFF2E7D32)
                    FitStatus.GOOD -> Color(0xFF66BB6A)
                    FitStatus.ACCEPTABLE -> Color(0xFFFDD835)
                    FitStatus.TIGHT_OR_LOOSE -> Color(0xFFFF9800)
                    FitStatus.NOT_RECOMMENDED -> Color(0xFFE53935)
                }
            )
            Text(
                "${(score.value * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    // ✅ BUG #65: Add accessibility for score notes
    if (score.note.isNotEmpty()) {
        Text(
            score.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 4.dp)
                .semantics {
                    contentDescription = "Note: ${score.note}"
                }
        )
    }
}

/**
 * ✅ FIX #29: Get relevant dimensions for garment type
 * Filters out irrelevant dimensions to avoid user confusion
 */
private fun getRelevantDimensions(garmentType: GarmentType?): Set<BodyDimension> {
    // ✅ TEMPORARY: For disabled garment types, return empty set or handle gracefully
    val enabledGarments = getEnabledGarmentTypes().toSet()
    if (garmentType != null && garmentType !in enabledGarments) {
        return emptySet() // No dimensions for disabled garments
    }
    return when (garmentType) {
        GarmentType.JEANS, GarmentType.CHINOS, GarmentType.TROUSERS, GarmentType.CARGO_PANTS, GarmentType.SHORTS, GarmentType.JOGGERS -> 
            setOf(BodyDimension.WAIST, BodyDimension.HIP, BodyDimension.INSEAM)
        GarmentType.SHIRT, GarmentType.BLAZER, GarmentType.JACKET -> 
            setOf(BodyDimension.CHEST, BodyDimension.SHOULDER, BodyDimension.SLEEVE, BodyDimension.LENGTH)
        GarmentType.TSHIRT, GarmentType.POLO_SHIRT, GarmentType.HOODIE, GarmentType.SWEATER -> 
            setOf(BodyDimension.CHEST, BodyDimension.SLEEVE, BodyDimension.LENGTH)
        null -> 
            setOf(BodyDimension.CHEST, BodyDimension.WAIST, BodyDimension.HIP, BodyDimension.SHOULDER, BodyDimension.INSEAM) // Show all if no type selected
    }
}

/**
 * ✅ FIX #33: Generate context for alternative sizes
 * Explains why an alternative size is shown and how it differs from primary
 */
private fun generateAlternativeContext(
    primary: ScoredSize,
    alternative: ScoredSize,
    garmentType: GarmentType?
): String {
    val primaryLabel = primary.sizeEntry.sizeLabel
    val altLabel = alternative.sizeEntry.sizeLabel
    
    // Size order for comparison
    val sizeOrder = listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL")
    val primaryIndex = sizeOrder.indexOf(primaryLabel.uppercase())
    val altIndex = sizeOrder.indexOf(altLabel.uppercase())
    
    // If sizes are not in standard order, try numeric comparison
    val primaryNum = primaryLabel.toIntOrNull()
    val altNum = altLabel.toIntOrNull()
    
    return when {
        // Standard size labels
        primaryIndex >= 0 && altIndex >= 0 -> {
            when {
                altIndex > primaryIndex -> {
                    val dimensionHint = when (garmentType) {
                        GarmentType.JEANS, GarmentType.CHINOS, GarmentType.TROUSERS, GarmentType.CARGO_PANTS, GarmentType.SHORTS, GarmentType.JOGGERS ->
                            "especially in waist and hip"
                        GarmentType.SHIRT, GarmentType.BLAZER, GarmentType.JACKET ->
                            "especially in chest and shoulder"
                        GarmentType.TSHIRT, GarmentType.POLO_SHIRT, GarmentType.HOODIE, GarmentType.SWEATER ->
                            "especially in chest"
                        else -> "especially in key measurements"
                    }
                    "Size up for looser fit, $dimensionHint"
                }
                altIndex < primaryIndex -> {
                    "Size down for tighter fit, more fitted silhouette"
                }
                else -> "Similar fit with minor differences"
            }
        }
        // Numeric sizes
        primaryNum != null && altNum != null -> {
            when {
                altNum > primaryNum -> "Larger size for looser fit"
                altNum < primaryNum -> "Smaller size for tighter fit"
                else -> "Similar fit with minor differences"
            }
        }
        // Fallback
        else -> {
            val fitDiff = (alternative.fitScore.overall - primary.fitScore.overall) * 100
            when {
                fitDiff > 5 -> "Good alternative with slightly better fit in some dimensions"
                fitDiff < -5 -> "Alternative option, may be slightly less optimal"
                else -> "Similar fit quality, consider based on your preference"
            }
        }
    }
}

/**
 * Measurement Instruction Component per PRD
 */
@Composable
private fun MeasurementInstruction(
    title: String,
    steps: List<String>,
    icon: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 24.sp)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        steps.forEachIndexed { index, step ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun getGarmentIcon(type: com.vanexa.supercalculator.domain.model.GarmentType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        com.vanexa.supercalculator.domain.model.GarmentType.TSHIRT -> GarmentIcons.Tshirt
        com.vanexa.supercalculator.domain.model.GarmentType.SHIRT -> GarmentIcons.Shirt
        com.vanexa.supercalculator.domain.model.GarmentType.POLO_SHIRT -> GarmentIcons.PoloShirt
        com.vanexa.supercalculator.domain.model.GarmentType.HOODIE -> GarmentIcons.Hoodie
        com.vanexa.supercalculator.domain.model.GarmentType.SWEATER -> GarmentIcons.Sweater

        com.vanexa.supercalculator.domain.model.GarmentType.JACKET,
        com.vanexa.supercalculator.domain.model.GarmentType.BLAZER -> GarmentIcons.Blazer

        com.vanexa.supercalculator.domain.model.GarmentType.JEANS,
        com.vanexa.supercalculator.domain.model.GarmentType.TROUSERS,
        com.vanexa.supercalculator.domain.model.GarmentType.CHINOS,
        com.vanexa.supercalculator.domain.model.GarmentType.CARGO_PANTS -> GarmentIcons.Pants

        com.vanexa.supercalculator.domain.model.GarmentType.SHORTS,
        com.vanexa.supercalculator.domain.model.GarmentType.JOGGERS -> GarmentIcons.Shorts
    }
}
