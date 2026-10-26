package com.vanexa.supercalculator.presentation.size

import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlin.math.abs
import com.vanexa.supercalculator.R

object SizeRecommendationBinder {
    fun bind(root: View, size: String, deviation: Float, message: String, deviationLabel: String, fitScore: Double? = null) {
        val sizeText = root.findViewById<TextView>(R.id.textSizeValue) ?: return
        val scoreIndicator = root.findViewById<CircularProgressIndicator>(R.id.scoreIndicator) ?: return
        val scoreText = root.findViewById<TextView>(R.id.textScore) ?: return
        val deviationLabelText = root.findViewById<TextView>(R.id.textDeviationLabel) ?: return
        val deviationValue = root.findViewById<TextView>(R.id.textDeviationValue) ?: return
        val deviationMarker = root.findViewById<View>(R.id.deviationMarker) ?: return
        val insightText = root.findViewById<TextView>(R.id.textInsight) ?: return

        sizeText.text = size

        // ✅ SYNCHRONIZE WITH ENGINE: Use actual fit score from engine instead of deviation calculation
        // ✅ FIX #2.3: Add isFinite() check to prevent NaN/Infinity crashes
        val probability = if (fitScore != null && fitScore.isFinite()) {
            // Convert fit score (0.0-1.0) to percentage (0-100)
            (fitScore * 100.0).coerceIn(1.0, 100.0)
        } else {
            // Fallback to deviation-based calculation if fit score not available or invalid
            val safeDeviation = if (deviation.isFinite()) deviation else 0f
            ((1f - (abs(safeDeviation).coerceAtMost(10f) / 10f)) * 100f)
                .coerceIn(1f, 100f).toDouble()
        }

        scoreIndicator.setProgress(probability.toInt(), false)
        scoreText.text = "${probability.toInt()}%"

        deviationLabelText.text = deviationLabel
        deviationValue.text = if (deviation >= 0f) "+%.1fcm".format(deviation) else "%.1fcm".format(deviation)

        val bias = ((deviation + 10f) / 20f).coerceIn(0f, 1f)
        val lp = deviationMarker.layoutParams as ConstraintLayout.LayoutParams
        lp.horizontalBias = bias
        deviationMarker.layoutParams = lp

        insightText.text = message
    }
}

