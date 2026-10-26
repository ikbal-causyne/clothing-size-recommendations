package com.vanexa.cal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vanexa.supercalculator.domain.model.FitResult
import com.vanexa.supercalculator.domain.model.DimensionFit
import com.vanexa.supercalculator.domain.usecase.FitCategoryEngine
import com.vanexacal.storage.UserProfile
import java.io.Serializable
import java.util.Locale

data class GarmentInfo(
    val garmentType: String,
    val sizeLabel: String?,
    val measurements: Map<String, Double>
) : Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitResultScreen(
    result: FitResult,
    garmentInfo: GarmentInfo,
    userProfile: UserProfile,
    onTryAnotherSize: () -> Unit,
    onBack: () -> Unit
) {
    val saved = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fit Analysis Result") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* share stub */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Result")
                    }
                    Button(
                        onClick = { saved.value = true }
                    ) { Text(if (saved.value) "Saved" else "Save") }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                SummaryCard(result)
            }

            if (result.dimensionAnalysis.isNotBlank()) {
                item {
                    SectionCard(
                        title = "Dimension Analysis",
                        content = result.dimensionAnalysis
                    )
                }
            }

            if (result.nextSteps.isNotEmpty()) {
                item {
                    Text(
                        text = "Next Steps",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(result.nextSteps) { step ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.padding(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = onBack) { Text("Back to Home") }
                    Button(onClick = onTryAnotherSize) { Text("Try Another Size") }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(result: FitResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = result.overallFitCategory.displayName,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = result.recommendation,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Overall Score: %.1f%%".format(Locale.ROOT, result.overallScore * 100),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun PreviewFitResultScreen() {
    val dummyGarmentInfo = GarmentInfo(
        garmentType = "Shirt",
        sizeLabel = "M",
        measurements = mapOf("chest" to 100.0, "waist" to 80.0)
    )
    val dummyUserProfile = UserProfile(heightCm = 170.0, weightKg = 65.0, region = "US", gender = "Female")
    val dummyResult = FitResult(
        overallFitCategory = FitCategoryEngine.FitCategory.COMFORTABLE,
        overallScore = 0.85,
        dimensionFits = listOf(
            DimensionFit(
                dimension = "chest",
                bodyMeasurement = 90.0,
                garmentMeasurement = 100.0,
                recommendedEase = 8.0,
                actualEase = 10.0,
                fitScore = 0.9,
                assessment = "Perfect",
                isCritical = true,
                confidence = 0.9
            )
        ),
        recommendation = "Size M should work well for you.",
        dimensionAnalysis = "• Chest: Perfect (+10cm)\n• Waist: Relaxed (+6cm)",
        nextSteps = listOf("This size should work great for you!")
    )
    FitResultScreen(
        result = dummyResult,
        garmentInfo = dummyGarmentInfo,
        userProfile = dummyUserProfile,
        onTryAnotherSize = {},
        onBack = {}
    )
}