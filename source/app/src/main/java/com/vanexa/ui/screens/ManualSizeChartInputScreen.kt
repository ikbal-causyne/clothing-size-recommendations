package com.vanexa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vanexa.data.models.ManualSizeChart
import com.vanexa.data.models.MeasurementUnit
import com.vanexa.data.models.SizeChartEntry
import com.vanexa.data.models.SizeLabels
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSizeChartInputScreen(
    garmentType: String,
    onComplete: (ManualSizeChart) -> Unit,
    onBack: () -> Unit
    ) {
    var selectedUnit by remember { mutableStateOf(MeasurementUnit.CM) }
    var selectedSizes by remember { mutableStateOf(setOf<String>()) }
    val sizeEntries = remember { mutableStateMapOf<String, SizeChartEntry>() }

    val availableSizes = remember(garmentType) {
        SizeLabels.forGarmentType(garmentType)
    }

    val horizontalPadding = 20.dp
    val contentTopPadding = 16.dp
    val contentBottomPadding = 16.dp // minimal, intentional bottom spacing (no phantom scroll)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Single IME inset source: keyboard shifts the whole screen once.
            // OEM fix (Realme/OPlus): IME insets may include IME-drawn nav bar + gesture area, causing a large gap.
            // Exclude those so the content hugs the actual keyboard height.
            .windowInsetsPadding(
                WindowInsets.ime
                    .exclude(WindowInsets.navigationBars)
                    .exclude(WindowInsets.systemGestures)
            )
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = { Text("Enter Size Chart - $garmentType") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedUnit.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { selectedUnit = selectedUnit.toggle() }) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Toggle unit")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 1.dp,
                    shadowElevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Button(
                        onClick = {
                            val chart = ManualSizeChart(
                                garmentType = garmentType,
                                unit = selectedUnit,
                                entries = selectedSizes.mapNotNull { sizeEntries[it] }
                            )
                            onComplete(chart)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        enabled = selectedSizes.isNotEmpty() &&
                            selectedSizes.all { sizeEntries[it]?.hasAnyMeasurement() == true }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Continue (${selectedSizes.size} sizes)")
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = contentTopPadding,
                    bottom = contentBottomPadding
                )
            ) {
                item {
                    Text(
                        text = "Select sizes to add:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    if (SizeLabels.isPantsType(garmentType) || SizeLabels.isShortsType(garmentType)) {
                        PantsSizeSelector(
                            garmentType = garmentType,
                            selectedSizes = selectedSizes,
                            onSizesChanged = { selectedSizes = it }
                        )
                    } else {
                        availableSizes.chunked(3).forEach { rowSizes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowSizes.forEach { size ->
                                    FilterChip(
                                        selected = size in selectedSizes,
                                        onClick = {
                                            selectedSizes = if (size in selectedSizes) {
                                                selectedSizes - size
                                            } else {
                                                selectedSizes + size
                                            }
                                        },
                                        label = { Text(size) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowSizes.size) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Divider()
                    Spacer(Modifier.height(16.dp))
                }

                if (selectedSizes.isNotEmpty()) {
                    items(selectedSizes.sorted()) { size ->
                        SizeEntryCard(
                            sizeLabel = size,
                            garmentType = garmentType,
                            unit = selectedUnit,
                            entry = sizeEntries[size] ?: SizeChartEntry(size),
                            onEntryChange = { sizeEntries[size] = it },
                            onRemove = {
                                selectedSizes = selectedSizes - size
                                sizeEntries.remove(size)
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SizeEntryCard(
    sizeLabel: String,
    garmentType: String,
    unit: MeasurementUnit,
    entry: SizeChartEntry,
    onEntryChange: (SizeChartEntry) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Size $sizeLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (entry.hasAnyMeasurement()) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Complete",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(20.dp)
                        )
                    }
                }

                Row {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))

                    val fields = getFieldsForGarmentType(garmentType)

                    fields.chunked(3).forEach { rowFields ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFields.forEach { field ->
                                MeasurementField(
                                    label = field,
                                    value = getFieldValue(entry, field, unit),
                                    unit = unit,
                                    onValueChange = { newValue ->
                                        onEntryChange(updateFieldValue(entry, field, newValue, unit))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowFields.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// Pants size selector and builder for waist x length and shorts/joggers
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantsSizeSelector(
    garmentType: String,
    selectedSizes: Set<String>,
    onSizesChanged: (Set<String>) -> Unit
) {
    var showSizeBuilder by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Quick Select:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        // Common sizes laid out in 3 columns
        val commonSizes = if (SizeLabels.isShortsType(garmentType)) {
            listOf("30", "32", "34", "36", "38", "40", "42", "44", "46")
        } else {
            listOf(
                "30x30", "30x32", "32x30", "32x32", "32x34",
                "34x30", "34x32", "34x34", "36x32", "36x34",
                "38x32", "38x34", "40x32", "40x34", "42x32"
            )
        }

        commonSizes.chunked(3).forEach { rowSizes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowSizes.forEach { size ->
                    FilterChip(
                        selected = size in selectedSizes,
                        onClick = {
                            onSizesChanged(
                                if (size in selectedSizes) selectedSizes - size
                                else selectedSizes + size
                            )
                        },
                        label = { Text(size, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowSizes.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (SizeLabels.isPantsType(garmentType)) {
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showSizeBuilder = !showSizeBuilder },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Custom Size (Waist x Length)")
            }

            if (showSizeBuilder) {
                Spacer(Modifier.height(8.dp))
                PantsSizeBuilderDialog(
                    onSizeSelected = { customSize ->
                        onSizesChanged(selectedSizes + customSize)
                        showSizeBuilder = false
                    },
                    onDismiss = { showSizeBuilder = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantsSizeBuilderDialog(
    onSizeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedWaist by remember { mutableStateOf("32") }
    var selectedLength by remember { mutableStateOf("32") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Build Custom Size",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Text("Waist (inches):", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SizeLabels.PANTS_WAIST.forEach { waist ->
                    FilterChip(
                        selected = waist == selectedWaist,
                        onClick = { selectedWaist = waist },
                        label = { Text(waist) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text("Length (inches):", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SizeLabels.PANTS_LENGTH.forEach { length ->
                    FilterChip(
                        selected = length == selectedLength,
                        onClick = { selectedLength = length },
                        label = { Text(length) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Size: ${selectedWaist}x${selectedLength}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSizeSelected("${selectedWaist}x${selectedLength}") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }
    }
}

@Composable
fun MeasurementField(
    label: String,
    value: String,
    unit: MeasurementUnit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,1}$"))) {
                onValueChange(newValue)
            }
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        suffix = { Text(unit.name, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

// Helper functions
fun getFieldsForGarmentType(type: String): List<String> {
    return when (type.uppercase()) {
        // Tops - unchanged
        "SHIRT", "T-SHIRT", "POLO SHIRT" -> listOf("Chest", "Shoulder", "Sleeve", "Length")
        "HOODIE" -> listOf("Chest", "Shoulder", "Sleeve", "Length")
        "JACKET", "COAT", "BLAZER" -> listOf("Chest", "Shoulder", "Sleeve", "Length")
        "DRESS" -> listOf("Chest", "Waist", "Hip", "Length")

        // Bottoms
        "JEANS" -> listOf("Waist", "Hip", "Inseam", "Rise", "Thigh", "Opening")
        "CHINOS" -> listOf("Waist", "Hip", "Inseam", "Rise", "Thigh", "Opening")
        "CARGO PANTS" -> listOf("Waist", "Hip", "Inseam", "Rise", "Thigh", "Opening")
        "TROUSERS" -> listOf("Waist", "Hip", "Inseam", "Rise", "Thigh", "Opening")
        "PANTS" -> listOf("Waist", "Hip", "Inseam", "Rise", "Thigh", "Opening")
        "SHORTS" -> listOf("Waist", "Hip", "Outseam", "Thigh", "Opening")
        "JOGGERS" -> listOf("Waist", "Hip", "Inseam", "Thigh", "Opening")

        else -> listOf("Chest", "Waist", "Hip")
    }
}

fun getFieldValue(entry: SizeChartEntry, field: String, unit: MeasurementUnit): String {
    val value = when (field.lowercase()) {
        "chest" -> if (unit == MeasurementUnit.CM) entry.chestCm else entry.chestInch
        "waist" -> if (unit == MeasurementUnit.CM) entry.waistCm else entry.waistInch
        "hip" -> if (unit == MeasurementUnit.CM) entry.hipCm else entry.hipInch
        "shoulder" -> if (unit == MeasurementUnit.CM) entry.shoulderCm else entry.shoulderInch
        "sleeve" -> if (unit == MeasurementUnit.CM) entry.sleeveCm else entry.sleeveInch
        "length" -> if (unit == MeasurementUnit.CM) entry.lengthCm else entry.lengthInch
        "inseam" -> if (unit == MeasurementUnit.CM) entry.inseamCm else entry.inseamInch
        "outseam" -> if (unit == MeasurementUnit.CM) entry.outseamCm else entry.outseamInch
        "rise" -> if (unit == MeasurementUnit.CM) entry.riseCm else entry.riseInch
        "thigh" -> if (unit == MeasurementUnit.CM) entry.thighCm else entry.thighInch
        "opening" -> if (unit == MeasurementUnit.CM) entry.openingCm else entry.openingInch
        else -> null
    }
    return value?.toString() ?: ""
}

fun updateFieldValue(entry: SizeChartEntry, field: String, value: String, unit: MeasurementUnit): SizeChartEntry {
    val numValue = value.toDoubleOrNull()
    return when (field.lowercase()) {
        "chest" -> if (unit == MeasurementUnit.CM) entry.copy(chestCm = numValue) else entry.copy(chestInch = numValue)
        "waist" -> if (unit == MeasurementUnit.CM) entry.copy(waistCm = numValue) else entry.copy(waistInch = numValue)
        "hip" -> if (unit == MeasurementUnit.CM) entry.copy(hipCm = numValue) else entry.copy(hipInch = numValue)
        "shoulder" -> if (unit == MeasurementUnit.CM) entry.copy(shoulderCm = numValue) else entry.copy(shoulderInch = numValue)
        "sleeve" -> if (unit == MeasurementUnit.CM) entry.copy(sleeveCm = numValue) else entry.copy(sleeveInch = numValue)
        "length" -> if (unit == MeasurementUnit.CM) entry.copy(lengthCm = numValue) else entry.copy(lengthInch = numValue)
        "inseam" -> if (unit == MeasurementUnit.CM) entry.copy(inseamCm = numValue) else entry.copy(inseamInch = numValue)
        "outseam" -> if (unit == MeasurementUnit.CM) entry.copy(outseamCm = numValue) else entry.copy(outseamInch = numValue)
        "rise" -> if (unit == MeasurementUnit.CM) entry.copy(riseCm = numValue) else entry.copy(riseInch = numValue)
        "thigh" -> if (unit == MeasurementUnit.CM) entry.copy(thighCm = numValue) else entry.copy(thighInch = numValue)
        "opening" -> if (unit == MeasurementUnit.CM) entry.copy(openingCm = numValue) else entry.copy(openingInch = numValue)
        else -> entry
    }
}

fun SizeChartEntry.hasAnyMeasurement(): Boolean {
    return chestCm != null || chestInch != null ||
            waistCm != null || waistInch != null ||
            hipCm != null || hipInch != null ||
            shoulderCm != null || shoulderInch != null ||
            sleeveCm != null || sleeveInch != null ||
            lengthCm != null || lengthInch != null ||
            inseamCm != null || inseamInch != null ||
            riseCm != null || riseInch != null ||
            thighCm != null || thighInch != null ||
            openingCm != null || openingInch != null ||
            outseamCm != null || outseamInch != null
}

