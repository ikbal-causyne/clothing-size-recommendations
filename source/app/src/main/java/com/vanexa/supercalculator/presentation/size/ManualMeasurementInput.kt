package com.vanexa.supercalculator.presentation.size

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vanexa.supercalculator.domain.model.GarmentType

/**
 * Manual Measurement Input UI - Two-Column Layout
 *
 * **Engine Vision Compliance**:
 * - Two-column layout: [Label] | [Value + Unit]
 * - Unit selection: CM (default) / INCH with automatic conversion
 * - Per-garment field selection (shirt has chest/waist, jeans have waist/inseam)
 * - Clear labels and input validation
 * - Mobile-friendly touch targets (48dp min height)
 *
 * **UX Pattern**:
 * 1. User selects unit (CM/INCH) at top
 * 2. For each measurement: Left column = label, Right column = value + unit
 * 3. Conversion happens transparently (INCH → CM internally)
 * 4. Validation: Only allow positive numbers, reasonable ranges
 */
@Composable
fun ManualMeasurementInput(
    measurements: Map<String, Double?>,
    onMeasurementsChanged: (Map<String, Double?>) -> Unit,
    modifier: Modifier = Modifier,
    garmentType: GarmentType = GarmentType.TSHIRT,
    unitPreference: UnitSystem = UnitSystem.CENTIMETER
) {
    var selectedUnit by remember { mutableStateOf(unitPreference) }
    val (measurableFields, setFields) = remember { mutableStateOf(measurements) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ====================================================================
        // SECTION 1: Unit Selector
        // ====================================================================
        Text(
            text = "Measurement Unit",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedUnit = UnitSystem.CENTIMETER },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedUnit == UnitSystem.CENTIMETER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text("Centimeters (CM)")
            }
            Button(
                onClick = { selectedUnit = UnitSystem.INCH },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedUnit == UnitSystem.INCH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text("Inches (IN)")
            }
        }
        
        Divider()
        
        // ====================================================================
        // SECTION 2: Measurement Input Rows (Two Columns)
        // ====================================================================
        Text(
            text = "Body Measurements",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        
        val fieldLabels = getFieldLabelsForGarment(garmentType)
        
        fieldLabels.forEach { (fieldKey, fieldLabel) ->
            MeasurementInputRow(
                label = fieldLabel,
                value = measurableFields[fieldKey]?.let { value ->
                    if (selectedUnit == UnitSystem.INCH) {
                        // Convert CM to INCH for display
                        String.format("%.2f", value / 2.54)
                    } else {
                        String.format("%.1f", value)
                    }
                } ?: "",
                unit = selectedUnit.displayName,
                onValueChanged = { newValue ->
                    val numericValue = newValue.toDoubleOrNull() ?: return@MeasurementInputRow
                    
                    // Validate reasonable ranges
                    if (numericValue < 0 || numericValue > 300) {
                        return@MeasurementInputRow  // Silently ignore invalid input
                    }
                    
                    val cmValue = if (selectedUnit == UnitSystem.INCH) {
                        numericValue * 2.54  // Convert INCH to CM internally
                    } else {
                        numericValue
                    }
                    
                    val updated = measurableFields.toMutableMap()
                    updated[fieldKey] = cmValue
                    setFields(updated)
                    onMeasurementsChanged(updated)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // ====================================================================
        // SECTION 3: Information Text
        // ====================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = "Tip: For most accurate fit recommendations, measure yourself or use size charts from the brand.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun MeasurementInputRow(
    label: String,
    value: String,
    unit: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ====================================================================
        // COLUMN 1: Label (Left, Static)
        // ====================================================================
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(0.4f)
                .padding(end = 12.dp)
        )
        
        // ====================================================================
        // COLUMN 2: Input Field + Unit (Right, Interactive)
        // ====================================================================
        Row(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            
            // Unit suffix
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .align(Alignment.CenterVertically)
            )
        }
    }
}

// ====================================================================
// SUPPORTING TYPES AND FUNCTIONS
// ====================================================================

enum class UnitSystem(val displayName: String, val conversionFactor: Double = 1.0) {
    CENTIMETER("CM", 1.0),
    INCH("IN", 2.54)  // 1 inch = 2.54 cm
}

/**
 * Get garment-specific measurement fields
 *
 * Different garment types require different measurements:
 * - Shirts: chest, waist, hip, shoulder
 * - Jeans: waist, hip, inseam
 * - Hoodies: chest, waist, hip, length
 *
 * Returns list of (fieldKey, displayLabel) pairs
 */
private fun getFieldLabelsForGarment(garmentType: GarmentType): List<Pair<String, String>> {
    return when (garmentType) {
        GarmentType.TSHIRT, GarmentType.POLO_SHIRT, GarmentType.SHIRT, GarmentType.BLAZER -> listOf(
            "chest" to "Chest",
            "waist" to "Waist",
            "hip" to "Hip",
            "shoulder" to "Shoulder (optional)"
        )
        
        GarmentType.JEANS, GarmentType.TROUSERS, GarmentType.CHINOS -> listOf(
            "waist" to "Waist",
            "hip" to "Hip",
            "inseam" to "Inseam (length)"
        )
        
        GarmentType.HOODIE, GarmentType.SWEATER -> listOf(
            "chest" to "Chest",
            "waist" to "Waist",
            "hip" to "Hip",
            "length" to "Length (optional)"
        )
        
        GarmentType.SHORTS -> listOf(
            "waist" to "Waist",
            "hip" to "Hip",
            "inseam" to "Inseam"
        )
        
        GarmentType.JOGGERS, GarmentType.CARGO_PANTS -> listOf(
            "waist" to "Waist",
            "hip" to "Hip",
            "inseam" to "Inseam (optional)"
        )
        
        else -> listOf(
            "chest" to "Chest",
            "waist" to "Waist",
            "hip" to "Hip"
        )
    }
}

// ====================================================================
// PREVIEW (for Compose Preview/Testing)
// ====================================================================

@Composable
fun ManualMeasurementInputPreview() {
    var measurements by remember {
        mutableStateOf(mapOf<String, Double?>(
            "chest" to null,
            "waist" to null,
            "hip" to null
        ))
    }
    
    MaterialTheme {
        ManualMeasurementInput(
            measurements = measurements,
            onMeasurementsChanged = { measurements = it },
            garmentType = GarmentType.TSHIRT
        )
    }
}
