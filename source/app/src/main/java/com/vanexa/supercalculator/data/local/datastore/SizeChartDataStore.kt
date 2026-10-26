package com.vanexa.supercalculator.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.vanexa.supercalculator.domain.model.SizeChart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sizeChartDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "size_charts"
)

/**
 * Professional DataStore for Size Charts
 * Optimized for performance with caching
 */
class SizeChartDataStore(
    private val context: Context,
    private val gson: Gson
) {
    companion object {
        private const val MAX_CACHED_CHARTS = 50 // Limit memory usage
        private const val CACHE_DURATION_MS = 7 * 24 * 3600000L // 7 days
    }
    
    /**
     * Save size chart with automatic cleanup
     */
    suspend fun saveSizeChart(chart: SizeChart) {
        val key = stringPreferencesKey("chart_${chart.id}")
        
        context.sizeChartDataStore.edit { preferences ->
            // Save new chart
            preferences[key] = gson.toJson(chart)
        }
    }
    
    /**
     * Get size chart by ID
     */
    suspend fun getSizeChart(id: String): SizeChart? {
        val key = stringPreferencesKey("chart_$id")
        val preferences = context.sizeChartDataStore.data.first()
        val json = preferences[key] ?: return null
        
        return try {
            gson.fromJson(json, SizeChart::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get all cached size charts
     */
    fun getAllSizeCharts() = context.sizeChartDataStore.data.map { preferences ->
        val charts = mutableListOf<SizeChart>()
        val prefsMap: Map<Preferences.Key<*>, Any?> = preferences.asMap()
        for ((key, value) in prefsMap) {
            if (key.name.startsWith("chart_")) {
                try {
                    val chart = gson.fromJson(value.toString(), SizeChart::class.java)
                    if (chart != null && isChartValid(chart)) {
                        charts.add(chart)
                    }
                } catch (e: Exception) {
                    // Skip invalid charts
                }
            }
        }
        charts.sortedByDescending { it.createdAt }
    }
    
    /**
     * Cleanup old charts - can be called separately for maintenance
     */
    suspend fun cleanupOldCharts() {
        val now = System.currentTimeMillis()
        context.sizeChartDataStore.edit { preferences ->
            val prefsMap: Map<Preferences.Key<*>, Any?> = preferences.asMap()
            val keysToRemove = mutableListOf<Preferences.Key<*>>()
            
            for ((key, value) in prefsMap) {
                if (key.name.startsWith("chart_")) {
                    try {
                        val chart = gson.fromJson(value.toString(), SizeChart::class.java)
                        if (chart != null) {
                            val age = now - chart.createdAt
                            if (age > CACHE_DURATION_MS) {
                                keysToRemove.add(key)
                            }
                        }
                    } catch (e: Exception) {
                        keysToRemove.add(key) // Remove invalid charts
                    }
                }
            }
            
            keysToRemove.forEach { key ->
                preferences.remove(key)
            }
        }
    }
    
    /**
     * Check if chart is still valid
     */
    private fun isChartValid(chart: SizeChart): Boolean {
        val age = System.currentTimeMillis() - chart.createdAt
        return age < CACHE_DURATION_MS && chart.sizes.isNotEmpty()
    }
}

