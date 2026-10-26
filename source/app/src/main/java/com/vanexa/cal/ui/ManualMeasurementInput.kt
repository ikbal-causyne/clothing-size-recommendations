package com.vanexa.cal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vanexa.supercalculator.domain.model.GarmentMeasurements
import com.vanexa.supercalculator.domain.model.GarmentType

/**
 * Simplified manual input wizard placeholder.
 * Provides a minimal UI so the module compiles and can be wired later.
 */
@Composable
fun ManualInputWizard(
    garmentTypeFromPreviousScan: GarmentType = GarmentType.TSHIRT,
    onComplete: (GarmentMeasurements) -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Manual Input",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Enter your measurements in the full wizard (placeholder).",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onComplete(
                            GarmentMeasurements(
                                sizeLabel = "N/A",
                                garmentType = garmentTypeFromPreviousScan,
                                chestCm = null,
                                waistCm = null,
                                hipCm = null,
                                shoulderCm = null,
                                sleeveCm = null,
                                lengthCm = null,
                                ocrConfidence = 1.0
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun ManualMeasurementInput(
    onComplete: (GarmentMeasurements) -> Unit,
    onCancel: () -> Unit
) {
    ManualInputWizard(
        garmentTypeFromPreviousScan = GarmentType.TSHIRT,
        onComplete = onComplete,
        onCancel = onCancel
    )
}


