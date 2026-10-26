package com.vanexa.supercalculator.domain.usecase

import android.util.Log
import com.vanexa.supercalculator.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * Size Chart Parser - PRD Section 4.1
 * Parses size charts from various formats into SizeChartEntry list
 */
class SizeChartParser {
    companion object {
        private const val TAG = "SizeChartParser"
        private const val MAX_INPUT_LENGTH = 10000
    }
    
    /**
     * Main parsing function - PRD Section 4.1
     * Returns List<SizeChartEntry> per PRD
     */
    suspend fun parse(text: String): List<SizeChartEntry> = withContext(Dispatchers.Default) {
        return@withContext try {
            Log.d(TAG, "🔍 Starting parse - Input length: ${text.length} characters")
            Log.d(TAG, "📝 Input preview: ${text.take(300)}...")
            
            if (text.isBlank()) {
                Log.w(TAG, "⚠️ Input text is blank")
                return@withContext emptyList()
            }
            if (text.length > MAX_INPUT_LENGTH) {
                Log.w(TAG, "⚠️ Input text too long: ${text.length} > $MAX_INPUT_LENGTH")
                return@withContext emptyList()
            }
            
            // ✅ FIX: Pre-process OCR text that lacks newlines/spaces
            // OCR often extracts text without proper line breaks: "SIZESMLXLXXLCHEST38..."
            val processedText = preprocessOcrText(text)
            if (processedText != text) {
                Log.d(TAG, "✅ [OCR Preprocessing] Added newlines to OCR text without line breaks")
                Log.d(TAG, "📝 Processed preview: ${processedText.take(300)}...")
            }
            
            // Detect format and parse
            val format = detectFormat(processedText)
            Log.d(TAG, "✅ Detected format: $format")
            
            val entries = when (format) {
                "TABLE" -> {
                    Log.d(TAG, "📊 Parsing as TABLE format")
                    parseTableSeparated(processedText)
                }
                "KEY_VALUE" -> {
                    Log.d(TAG, "🔑 Parsing as KEY_VALUE format")
                    parseKeyValue(processedText)
                }
                "NUMBERED" -> {
                    Log.d(TAG, "🔢 Parsing as NUMBERED format")
                    parseNumberedList(processedText)
                }
                "VERTICAL" -> {
                    Log.d(TAG, "📋 Parsing as VERTICAL format")
                    parseVerticalFormat(processedText)
                }
                else -> {
                    Log.d(TAG, "🔀 Parsing as FLEXIBLE format")
                    parseFlexibleFormat(processedText)
                }
            }
            
            Log.d(TAG, "📊 Parsed ${entries.size} entries")
            entries.forEachIndexed { index, entry ->
                Log.d(TAG, "  Entry $index: ${entry.sizeLabel} - chest=${entry.chest}, waist=${entry.waist}, hip=${entry.hip}")
            }
            
            // Normalize size labels - ensure all labels are normalized to standard format (S, M, L, XL)
            val normalizedLabels = entries.map { entry ->
                val normalizedLabel = SizeLabelNormalizer.normalize(entry.sizeLabel)
                Log.d(TAG, "🔄 Normalized sizeLabel: '${entry.sizeLabel}' -> '$normalizedLabel'")
                entry.copy(sizeLabel = normalizedLabel)
            }.toList()
            
            // Apply Engine Vision Module 2.5 & 2.6: Normalization and Outlier Detection
            android.util.Log.d(TAG, "🎯 [Engine Vision Module 2] Applying normalization and outlier detection")
            val normalized = normalizeSizeChart(normalizedLabels)
            val withOutliers = detectAndFixOutliers(normalized)
            
            Log.d(TAG, "✅ Parse complete: ${withOutliers.size} normalized entries (Engine Vision Module 2 applied)")
            withOutliers
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun detectFormat(text: String): String {
        val lines = text.trim().split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return "UNKNOWN"
        
        // Check for JSON
        if (text.trim().startsWith("{") || text.trim().startsWith("[")) {
            return "JSON"
        }
        
        // Check for numbered list
        if (lines.any { it.contains(Regex("No\\.\\s*\\d+", RegexOption.IGNORE_CASE)) }) {
            return "NUMBERED"
        }
        
        // Check for table separated
        val firstLine = lines.firstOrNull() ?: return "UNKNOWN"
        if (firstLine.contains("|") || firstLine.contains("\t")) {
            return "TABLE"
        }
        
        // Check for vertical format (size labels in one column, dimensions in others)
        val hasSizeLabels = lines.any { line ->
            val upper = line.uppercase().trim()
            upper.matches(Regex("S|M|L|XL|XXL|XXXL|3XL|4XL|5XL|6XL|XS|\\d+XL|\\d{1,2}"))
        }
        val hasDimensionHeaders = lines.any { line ->
            val upper = line.uppercase().trim()
            upper.contains("CHEST") || upper.contains("WAIST") || upper.contains("DADA") || 
            upper.contains("PINGGANG") || upper.contains("PANJANG") || upper.contains("LENGTH") ||
            upper.contains("INSEAM") || upper.contains("HIP")
        }
        if (hasSizeLabels && hasDimensionHeaders) {
            return "VERTICAL"
        }
        
        // Check for key-value
        if (lines.any { it.contains(":") && it.contains(Regex("chest|waist|hip|dada|pinggang", RegexOption.IGNORE_CASE)) }) {
            return "KEY_VALUE"
        }
        
        return "FLEXIBLE"
    }
    
    /**
     * ✅ FIX: Pre-process OCR text that lacks newlines/spaces
     * OCR often extracts text without proper line breaks: "SIZESMLXLXXLCHEST3841.5..."
     * This function adds newlines before headers and size labels to enable proper parsing
     */
    /**
     * ✅ FIX v5: INTELLIGENT OCR Preprocessing
     * Strategy: Use pattern-based reconstruction instead of breaking words
     * Preserves word boundaries while adding structure
     */
    private fun preprocessOcrText(text: String): String {
        // If text already has newlines, return as-is
        if (text.contains("\n")) {
            return text
        }
        
        // Check if this looks like OCR text without newlines
        val hasHeaders = Regex("SIZE|CHEST|WAIST|HIP|LENGTH|SLEEVE|DADA|PINGGANG|PINGGUL|PANJANG|LENGAN", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val hasSizeLabels = Regex("(?:^|[^A-Z])([X]*[SML]|XS|XXL|XXXL|\\d+XL)(?:[^A-Z]|$)", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
                           text.contains(Regex("SIZE.*[SML]", RegexOption.IGNORE_CASE))
        val hasNumbers = Regex("\\d+(\\.\\d+)?").containsMatchIn(text)
        
        if (!hasHeaders || !hasSizeLabels || !hasNumbers) {
            Log.d(TAG, "⚠️ [OCR Preprocessing] Doesn't match OCR pattern")
            return text
        }
        
        Log.d(TAG, "🔧 [OCR Preprocessing v5] Detected OCR text without newlines - intelligent reconstruction")
        Log.d(TAG, "📝 [OCR Preprocessing] Original text: ${text.take(200)}...")
        
        var processed = text
        
        // ✅ STEP 1: Preserve complete words - add newlines BEFORE headers (not breaking them)
        // Pattern: Add newline before complete header words
        val headerPatterns = listOf(
            "SIZE", "CHEST", "DADA", "BUST", "WAIST", "PINGGANG",
            "HIP", "PINGGUL", "LENGTH", "PANJANG", "SLEEVE", "LENGAN"
        )
        
        for (header in headerPatterns) {
            // Add newline before header if not already at start or after newline
            processed = processed.replace(Regex("([^\\n])($header)", RegexOption.IGNORE_CASE), "$1\n$2")
            // Add newline after header if followed by size label or number
            processed = processed.replace(Regex("($header)([SMLX\\d])", RegexOption.IGNORE_CASE), "$1\n$2")
        }
        
        // ✅ STEP 2: Separate size labels FIRST (before protecting other words)
        // Strategy: Handle connected size labels like "SMLXLXXL" → "S\nM\nL\nXL\nXXL"
        // This must happen BEFORE word protection to avoid conflicts
        
        // Pattern: SMLXLXXL → S|M|L|XL|XXL
        // Process longest patterns first to avoid conflicts
        processed = processed.replace(Regex("(XXXL)", RegexOption.IGNORE_CASE), "|XXXL|")
        processed = processed.replace(Regex("(XXL)", RegexOption.IGNORE_CASE), "|XXL|")
        processed = processed.replace(Regex("(XL)", RegexOption.IGNORE_CASE), "|XL|")
        processed = processed.replace(Regex("(XS)", RegexOption.IGNORE_CASE), "|XS|")
        
        // Separate single letters S, M, L (but not if part of XL/XXL/XXXL/XS)
        // Use lookahead/lookbehind to avoid breaking words
        processed = processed.replace(Regex("([SM])(?![LX])", RegexOption.IGNORE_CASE), "$1|")
        processed = processed.replace(Regex("([L])(?![XL])", RegexOption.IGNORE_CASE), "$1|")
        
        // Replace | with newlines
        processed = processed.replace("|", "\n")
        
        // ✅ STEP 2.5: Protect complete words (headers, numbers) AFTER size label separation
        val wordProtection = mutableMapOf<String, String>()
        var protectionIndex = 0
        
        // Find and protect complete words (headers, numbers, etc.) - but NOT size labels
        val wordPattern = Regex("\\b([A-Z]{3,}(?![SMLX])|\\d+(?:\\.\\d+)?)\\b", RegexOption.IGNORE_CASE)
        processed = wordPattern.replace(processed) { matchResult ->
            val word = matchResult.value
            // Don't protect if it's a size label
            if (word.uppercase().matches(Regex("^[X]*[SML]$|^XS$|^XXL$|^XXXL$|^\\d+XL$"))) {
                return@replace word
            }
            val placeholder = "___WORD${protectionIndex}___"
            wordProtection[placeholder] = word
            protectionIndex++
            placeholder
        }
        
        // Restore protected words after number processing
        // (Will be done after STEP 3)
        
        // ✅ STEP 3: Separate numbers intelligently
        // Strategy: Handle patterns like "3841.546.549" → "38\n41.5\n46.5\n49"
        // This requires multiple passes with different patterns
        
        // STEP 3.1: Separate decimal numbers that are connected
        // Pattern: "41.546.5" → "41.5\n46.5"
        // Match: X.Y followed by Z (where Z starts a new decimal number)
        var changed = true
        var loopCount = 0
        while (changed && loopCount < 25) {
            val before = processed
            // Match: digit.decimal followed by digit (which starts next decimal)
            // Example: "41.546.5" → "41.5" + "46.5"
            processed = processed.replace(Regex("(\\d{1,2}\\.\\d{1})(\\d{1,2}(?:\\.\\d{1})?)")) { matchResult ->
                val first = matchResult.groupValues[1]
                val second = matchResult.groupValues[2]
                val firstNum = first.toDoubleOrNull() ?: 0.0
                val secondNum = second.toDoubleOrNull() ?: 0.0
                // Validate both are reasonable measurements
                if (firstNum in 20.0..120.0 && secondNum in 20.0..120.0) {
                    "$first\n$second"
                } else {
                    "$first$second"
                }
            }
            changed = (processed != before)
            loopCount++
        }
        
        // STEP 3.2: Separate 2-digit integers from decimals
        // Pattern: "3841.5" → "38\n41.5"
        processed = processed.replace(Regex("(\\d{2})(\\d{1,2}\\.\\d{1,2})")) { matchResult ->
            val first = matchResult.groupValues[1]
            val second = matchResult.groupValues[2]
            val firstNum = first.toIntOrNull() ?: 0
            val secondNum = second.toDoubleOrNull() ?: 0.0
            if (firstNum in 20..100 && secondNum in 20.0..120.0) {
                "$first\n$second"
            } else {
                "$first$second"
            }
        }
        
        // STEP 3.3: Separate 2-digit integers (e.g., "3841" → "38\n41")
        // Only if both are in reasonable range (20-100)
        processed = processed.replace(Regex("(\\d{2})(\\d{2})(?![\\d.])")) { matchResult ->
            val first = matchResult.groupValues[1]
            val second = matchResult.groupValues[2]
            val firstNum = first.toIntOrNull() ?: 0
            val secondNum = second.toIntOrNull() ?: 0
            if (firstNum in 20..100 && secondNum in 20..100) {
                "$first\n$second"
            } else {
                "$first$second"
            }
        }
        
        // Restore protected words after number processing
        wordProtection.forEach { (placeholder, word) ->
            processed = processed.replace(placeholder, word)
        }
        
        // ✅ STEP 4: Clean up - remove excessive newlines and normalize
        processed = processed.replace(Regex("\n{3,}"), "\n\n")
        processed = processed.replace(Regex("^\n+|\n+$"), "") // Remove leading/trailing newlines
        
        // Remove empty lines
        processed = processed.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        
        Log.d(TAG, "✅ [OCR Preprocessing v5] Processed text preview: ${processed.take(300)}...")
        Log.d(TAG, "📊 [OCR Preprocessing] Line count: ${processed.split("\n").size}")
        
        return processed.trim()
    }
    
    private fun parseTableSeparated(text: String): List<SizeChartEntry> {
        val entries = mutableListOf<SizeChartEntry>()
        val lines = text.trim().split("\n").filter { it.isNotBlank() }
        
        var startIndex = 0
        if (lines.isEmpty()) return entries
        val firstLine = lines.first()
        if (firstLine.contains("SIZE", ignoreCase = true) || firstLine.contains("UKURAN", ignoreCase = true)) {
            startIndex = 1
        }
        
        val separator = when {
            firstLine.contains("|") -> "|"
            firstLine.contains("\t") -> "\t"
            else -> "\\s{2,}"
        }
        
        for (i in startIndex until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            
            val parts = if (separator == "\\s{2,}") {
                line.split(Regex(separator))
            } else {
                line.split(separator)
            }.map { it.trim() }.filter { it.isNotBlank() }
            
            if (parts.size < 2) continue
            
            // Validate sizeLabel is actually a size label, not a measurement
            val candidateSizeLabel = parts[0]
            val sizeLabel = if (candidateSizeLabel.uppercase().matches(Regex("^[X]*[SML]$|^\\d{1,2}$")) && 
                !candidateSizeLabel.contains("-") && !candidateSizeLabel.contains("~") && !candidateSizeLabel.contains("\"")) {
                candidateSizeLabel
            } else {
                // Generate size label if first column is not a valid size label
                val sizeLabels = listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL")
                if (entries.size < sizeLabels.size) {
                    sizeLabels[entries.size]
                } else {
                    "Size${entries.size + 1}"
                }
            }
            
            val chest = parts.getOrNull(1)?.let { parseRange(it) }
            val waist = parts.getOrNull(2)?.let { parseRange(it) }
            val hip = parts.getOrNull(3)?.let { parseRange(it) }
            
            if (chest != null || waist != null || hip != null) {
                entries.add(
                    SizeChartEntry(
                        sizeLabel = sizeLabel,
                        chest = chest,
                        waist = waist,
                        hip = hip
                    )
                )
                Log.d(TAG, "Table entry: $sizeLabel - chest=$chest, waist=$waist, hip=$hip")
            }
        }
        
        return entries
    }
    
    private fun parseKeyValue(text: String): List<SizeChartEntry> {
        val entries = mutableListOf<SizeChartEntry>()
        val lines = text.trim().split("\n").filter { it.isNotBlank() }
        
        val pattern = Pattern.compile(
            "([A-Z0-9]+)\\s*[:|]\\s*" +
            "(?:Chest|Dada)\\s*([0-9.]+)\\s*[-~–]\\s*([0-9.]+)\\s*(?:cm|inch)?\\s*" +
            "(?:,|\\|)?\\s*" +
            "(?:Waist|Pinggang)?\\s*([0-9.]+)?\\s*[-~–]\\s*([0-9.]+)?\\s*(?:cm|inch)?",
            Pattern.CASE_INSENSITIVE
        )
        
        for (line in lines) {
            val match = pattern.matcher(line)
            if (match.find()) {
                val sizeLabel = match.group(1) ?: continue
                val isInch = line.contains("\"") || line.contains("inch", ignoreCase = true)
                val factor = if (isInch) 2.54 else 1.0
                
                val chestStart = match.group(2)?.toDoubleOrNull()?.let { it * factor }
                val chestEnd = match.group(3)?.toDoubleOrNull()?.let { it * factor }
                val waistStart = match.group(4)?.toDoubleOrNull()?.let { it * factor }
                val waistEnd = match.group(5)?.toDoubleOrNull()?.let { it * factor }
                
                entries.add(
                    SizeChartEntry(
                        sizeLabel = sizeLabel,
                        chest = chestStart?.let { s -> chestEnd?.let { e -> MeasurementRange(s, e, if (isInch) MeasurementUnit.INCH else MeasurementUnit.CM, confidence = 0.90) } },
                        waist = waistStart?.let { s -> waistEnd?.let { e -> MeasurementRange(s, e, if (isInch) MeasurementUnit.INCH else MeasurementUnit.CM, confidence = 0.90) } }
                    )
                )
            }
        }
        
        return entries
    }
    
    private fun parseNumberedList(text: String): List<SizeChartEntry> {
        val entries = mutableListOf<SizeChartEntry>()
        val lines = text.trim().split("\n").filter { it.isNotBlank() }
        
        val pattern = Pattern.compile(
            "No\\.\\s*(\\d+)\\s*=\\s*" +
            "(?:lingkar\\s*)?(?:pinggang|waist)?\\s*" +
            "(\\d+)\\s*cm",
            Pattern.CASE_INSENSITIVE
        )
        
        for (line in lines) {
            val match = pattern.matcher(line)
            if (match.find()) {
                val sizeLabel = "No. ${match.group(1)}"
                val waist = match.group(2)?.toDoubleOrNull()
                
                if (waist != null) {
                    entries.add(
                        SizeChartEntry(
                            sizeLabel = sizeLabel,
                            waist = MeasurementRange(waist - 2.0, waist + 2.0, MeasurementUnit.CM, confidence = 0.85)
                        )
                    )
                }
            }
        }
        
        return entries
    }
    
    private fun parseVerticalFormat(text: String): List<SizeChartEntry> {
        val entries = mutableListOf<SizeChartEntry>()
        val lines = text.trim().split("\n").filter { it.isNotBlank() }
        
        if (lines.isEmpty()) {
            Log.w(TAG, "Empty input for vertical format")
            return entries
        }
        
        Log.d(TAG, "🔍 [parseVerticalFormat] Processing ${lines.size} lines")
        Log.d(TAG, "📝 [parseVerticalFormat] First 10 lines: ${lines.take(10).joinToString(" | ")}")
        
        // Check if this is OCR format (vertical headers followed by values)
        // Format: SIZE\nS\nM\nL\nCHEST\n38\n41.5\n...
        val isOcrFormat = lines.any { it.uppercase().trim() == "SIZE" } && 
                         lines.any { it.uppercase().trim().matches(Regex("^[X]*[SML]$")) } &&
                         lines.any { it.uppercase().trim().contains("CHEST") || it.uppercase().trim().contains("WAIST") }
        
        if (isOcrFormat) {
            Log.d(TAG, "📋 [parseVerticalFormat] Detected OCR vertical format - parsing as column-based")
            return parseOcrVerticalFormat(lines)
        }
        
        // Find header row to identify column positions (original horizontal header format)
        var headerIndex = -1
        val headerKeywords = listOf("SIZE", "UKURAN", "CHEST", "DADA", "WAIST", "PINGGANG", "HIP", "PINGGUL", "LENGTH", "PANJANG")
        
        for (i in lines.indices) {
            val upper = lines[i].uppercase()
            if (headerKeywords.any { upper.contains(it) }) {
                headerIndex = i
                break
            }
        }
        
        if (headerIndex == -1) {
            Log.w(TAG, "No header found, trying flexible parse")
            return parseFlexibleFormat(text)
        }
        
        // Parse header to find column positions
        val headerLine = lines[headerIndex]
        val headerParts = headerLine.split(Regex("\\s{2,}|\\t|\\|")).map { it.trim().uppercase() }
        
        val sizeCol = headerParts.indexOfFirst { it.contains("SIZE") || it.contains("UKURAN") || it.matches(Regex("S|M|L|XL|XXL")) }
        val chestCol = headerParts.indexOfFirst { it.contains("CHEST") || it.contains("DADA") || it.contains("BUST") }
        val waistCol = headerParts.indexOfFirst { it.contains("WAIST") || it.contains("PINGGANG") }
        val hipCol = headerParts.indexOfFirst { it.contains("HIP") || it.contains("PINGGUL") }
        val lengthCol = headerParts.indexOfFirst { it.contains("LENGTH") || it.contains("PANJANG") }
        val inseamCol = headerParts.indexOfFirst { it.contains("INSEAM") }
        
        Log.d(TAG, "Header columns: size=$sizeCol, chest=$chestCol, waist=$waistCol, hip=$hipCol")
        
        // Parse data rows
        for (i in (headerIndex + 1) until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            
            val parts = line.split(Regex("\\s{2,}|\\t|\\|")).map { it.trim() }.filter { it.isNotBlank() }
            
            if (parts.isEmpty()) continue
            
            // Get size label - MUST be a size label (S, M, L, XL), not a measurement
            val sizeLabel = when {
                sizeCol >= 0 && sizeCol < parts.size -> {
                    val candidate = parts[sizeCol]
                    // Validate it's actually a size label, not a measurement
                    val upper = candidate.uppercase().trim()
                    if (upper.matches(Regex("^[X]*[SML]$|^\\d+$")) && !upper.contains("-") && !upper.contains("~") && !upper.contains("\"")) {
                        candidate
            } else {
                        // Not a valid size label, try to infer from position or use default
                        null
                    }
                }
                parts[0].uppercase().matches(Regex("^[X]*[SML]$|^\\d{1,2}$")) && !parts[0].contains("-") && !parts[0].contains("~") && !parts[0].contains("\"") -> parts[0]
                else -> null
            }
            
            // If no valid size label found, try to infer from measurements or use index
            val finalSizeLabel = sizeLabel ?: run {
                // Try to find size label in other columns
                for (part in parts) {
                    val upper = part.uppercase().trim()
                    if (upper.matches(Regex("^[X]*[SML]$|^\\d{1,2}$")) && !upper.contains("-") && !upper.contains("~") && !upper.contains("\"")) {
                        return@run part
                    }
                }
                // ✅ BUG #55: Fix infinite loop risk - Generate unbounded size labels
                // If still no size label, generate from index (S, M, L, XL pattern)
                val baseSizeLabels = listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL")
                
                when {
                    entries.size < baseSizeLabels.size -> {
                        // Use standard labels first
                        baseSizeLabels[entries.size]
                    }
                    else -> {
                        // ✅ FIX: Generate extended labels: 4XL, 5XL, 6XL, ... (unbounded)
                        // Example: entry 0 → XS, entry 7 → 4XL, entry 10 → 7XL, entry 50 → 47XL
                        val extendedIndex = entries.size - baseSizeLabels.size + 4
                        "${extendedIndex}XL"
                    }
                }
            }
            
            // Parse measurements
            val chest = if (chestCol >= 0 && chestCol < parts.size) parseRange(parts[chestCol]) else null
            val waist = if (waistCol >= 0 && waistCol < parts.size) parseRange(parts[waistCol]) else null
            val hip = if (hipCol >= 0 && hipCol < parts.size) parseRange(parts[hipCol]) else null
            val length = if (lengthCol >= 0 && lengthCol < parts.size) parseRange(parts[lengthCol]) else null
            val inseam = if (inseamCol >= 0 && inseamCol < parts.size) parseRange(parts[inseamCol]) else null
            
            // If no measurements found, try to extract from line directly
            if (chest == null && waist == null && hip == null) {
                // Try flexible extraction from entire line
                val numbers = Regex("(\\d{2,}(?:\\.\\d+)?)").findAll(line).mapNotNull { it.value.toDoubleOrNull() }.toList()
                if (numbers.size >= 2) {
                    val isInch = line.contains("\"") || line.contains("inch", ignoreCase = true)
                    val factor = if (isInch) 2.54 else 1.0
                    
                    // Generate size label if not found
                    val generatedSizeLabel = finalSizeLabel
                    
                    entries.add(
                        SizeChartEntry(
                            sizeLabel = generatedSizeLabel,
                            chest = numbers.getOrNull(0)?.let { s ->
                                numbers.getOrNull(1)?.let { e ->
                                    MeasurementRange(s * factor, e * factor, if (isInch) MeasurementUnit.INCH else MeasurementUnit.CM, confidence = 0.80)
                                }
                            },
                            waist = numbers.getOrNull(2)?.let { s ->
                                numbers.getOrNull(3)?.let { e ->
                                    MeasurementRange(s * factor, e * factor, if (isInch) MeasurementUnit.INCH else MeasurementUnit.CM, confidence = 0.80)
                                }
                            }
                        )
                    )
                    Log.d(TAG, "Added entry with generated sizeLabel: $generatedSizeLabel")
                    continue
                }
            }
            
            // Add entry if we have at least one measurement
            if (chest != null || waist != null || hip != null || length != null || inseam != null) {
                entries.add(
                    SizeChartEntry(
                        sizeLabel = finalSizeLabel,
                        chest = chest,
                        waist = waist,
                        hip = hip,
                        length = length,
                        inseam = inseam
                    )
                )
                Log.d(TAG, "Parsed entry: $finalSizeLabel - chest=$chest, waist=$waist, hip=$hip")
            }
        }
        
        Log.d(TAG, "Vertical format parsed ${entries.size} entries")
        return entries
    }
    
    /**
     * Parse OCR vertical format where headers and values are in separate lines
     * ✅ BUG #56: Supports both forward (headers → values) and backward (values → headers) formats
     * Format 1 (Forward):
     * SIZE
     * S
     * M
     * L
     * CHEST
     * 38
     * 41.5
     * ...
     * 
     * Format 2 (Backward/Reversed):
     * S
     * M
     * L
     * SIZE
     * 38
     * 41.5
     * CHEST
     * ...
     */
    private fun parseOcrVerticalFormat(lines: List<String>): List<SizeChartEntry> {
        // ✅ BUG #56: Try forward parsing first (headers → values)
        val forwardResult = parseOcrForward(lines)
        if (forwardResult.isNotEmpty()) {
            Log.d(TAG, "✅ [BUG #56] Forward parsing successful: ${forwardResult.size} entries")
            return forwardResult
        }
        
        // ✅ BUG #56: Try backward parsing (values → headers)
        val backwardResult = parseOcrBackward(lines)
        if (backwardResult.isNotEmpty()) {
            Log.d(TAG, "✅ [BUG #56] Backward parsing successful: ${backwardResult.size} entries")
            return backwardResult
        }
        
        Log.w(TAG, "⚠️ [BUG #56] Both forward and backward parsing failed")
        return emptyList()
    }
    
    /**
     * ✅ BUG #56: Forward parsing (headers → values)
     */
    private fun parseOcrForward(lines: List<String>): List<SizeChartEntry> {
        val entries = mutableListOf<SizeChartEntry>()
        
        Log.d(TAG, "🔍 [BUG #56 Forward] Starting forward OCR vertical format parsing")
        
        // Find all header positions
        val sizeHeaderIndex = lines.indexOfFirst { it.uppercase().trim() == "SIZE" }
        val chestHeaderIndex = lines.indexOfFirst { it.uppercase().trim().contains("CHEST") || it.uppercase().trim().contains("DADA") || it.uppercase().trim().contains("BUST") }
        val waistHeaderIndex = lines.indexOfFirst { it.uppercase().trim().contains("WAIST") || it.uppercase().trim().contains("PINGGANG") }
        val hipHeaderIndex = lines.indexOfFirst { it.uppercase().trim().contains("HIP") || it.uppercase().trim().contains("PINGGUL") }
        val lengthHeaderIndex = lines.indexOfFirst { it.uppercase().trim().contains("LENGTH") || it.uppercase().trim().contains("PANJANG") }
        val sleeveHeaderIndex = lines.indexOfFirst { it.uppercase().trim().contains("SLEEVE") || it.uppercase().trim().contains("LENGAN") }
        
        Log.d(TAG, "📊 [parseOcrVerticalFormat] Header positions: size=$sizeHeaderIndex, chest=$chestHeaderIndex, waist=$waistHeaderIndex, hip=$hipHeaderIndex, length=$lengthHeaderIndex, sleeve=$sleeveHeaderIndex")
        
        if (sizeHeaderIndex == -1) {
            Log.w(TAG, "⚠️ [parseOcrVerticalFormat] No SIZE header found")
            return entries
        }
        
        // Extract size labels (lines after SIZE header until next header or end)
        val sizeLabels = mutableListOf<String>()
        val nextHeaderAfterSize = listOfNotNull(chestHeaderIndex, waistHeaderIndex, hipHeaderIndex, lengthHeaderIndex, sleeveHeaderIndex)
            .filter { it > sizeHeaderIndex }
            .minOrNull() ?: lines.size
        
        for (i in (sizeHeaderIndex + 1) until nextHeaderAfterSize) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            
            val upper = line.uppercase()
            // Skip header lines
            if (upper.matches(Regex("CHEST|WAIST|HIP|LENGTH|SLEEVE|DADA|PINGGANG|PINGGUL|PANJANG|LENGAN"))) {
                continue
            }
            
            // ✅ FIX: Split line if it contains multiple size labels (e.g., "MLXL" → ["M", "L", "XL"])
            // This handles cases where preprocessing didn't fully separate
            if (upper.contains(Regex("[SMLX]")) && upper.length > 2 && !upper.matches(Regex("^[X]*[SML]$|^XS$|^XXL$|^XXXL$|^\\d+XL$"))) {
                // Try to split connected size labels using explicit pattern matching
                val parts = mutableListOf<String>()
                var remaining = line
                
                // Extract all size labels from the line
                val sizePatterns = listOf("XXXL", "XXL", "XL", "XS", "S", "M", "L")
                for (pattern in sizePatterns) {
                    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                    while (regex.containsMatchIn(remaining)) {
                        val match = regex.find(remaining)
                        if (match != null) {
                            parts.add(match.value)
                            remaining = remaining.removeRange(match.range)
                        } else {
                            break
                        }
                    }
                }
                
                // Add all valid size label parts
                parts.forEach { part ->
                    val partUpper = part.uppercase().trim()
                    if (partUpper.matches(Regex("^[X]*[SML]$|^XS$|^XXL$|^XXXL$|^\\d+XL$"))) {
                        sizeLabels.add(part.trim())
                    }
                }
            } else {
                // Single size label or already separated - validate and add
                if (upper.matches(Regex("^[X]*[SML]$|^XS$|^XXL$|^XXXL$|^\\d+XL$"))) {
                    sizeLabels.add(line)
                }
            }
        }
        
        Log.d(TAG, "📊 [parseOcrVerticalFormat] Found ${sizeLabels.size} size labels: ${sizeLabels.joinToString(", ")}")
        
        if (sizeLabels.isEmpty()) {
            Log.w(TAG, "⚠️ [parseOcrVerticalFormat] No size labels found")
            return entries
        }
        
        // Extract measurements for each column per Engine Vision Module 2.4
        // Uses parseRange() for OCR error correction (O→0, I/l→1, B→8, etc.)
        fun extractMeasurements(headerIndex: Int, count: Int): List<MeasurementRange?> {
            if (headerIndex == -1) return List(count) { null }
            val measurements = mutableListOf<MeasurementRange?>()
            val nextHeader = listOfNotNull(chestHeaderIndex, waistHeaderIndex, hipHeaderIndex, lengthHeaderIndex, sleeveHeaderIndex, lines.size)
                .filter { it > headerIndex }
                .minOrNull() ?: lines.size
            
            for (i in (headerIndex + 1) until nextHeader) {
                val line = lines[i].trim()
                if (line.isNotBlank()) {
                    // Use parseRange() for OCR error correction per Engine Vision Module 2.4
                    val parsed = parseRange(line)
                    if (parsed != null) {
                        measurements.add(parsed)
                        android.util.Log.d(TAG, "🎯 [Engine Vision Module 2] Parsed measurement from OCR: '$line' → $parsed")
                    } else {
                        // Try direct number extraction as fallback
                        val cleaned = line.replace(Regex("[^0-9.\\-]"), "")
                        val value = cleaned.toDoubleOrNull()
                        if (value != null && value > 0) {
                            // Create range with ±1cm tolerance (low confidence for direct extraction)
                            measurements.add(MeasurementRange(value - 1.0, value + 1.0, MeasurementUnit.CM, confidence = 0.50))
                            android.util.Log.w(TAG, "⚠️ [Engine Vision Module 2] Direct extraction (low confidence): '$line' → $value")
                        } else {
                            measurements.add(null)
                        }
                    }
                } else {
                    measurements.add(null)
                }
            }
            
            // Pad to match size labels count
            while (measurements.size < count) {
                measurements.add(null)
            }
            
            return measurements.take(count)
        }
        
        val chestValues = extractMeasurements(chestHeaderIndex, sizeLabels.size)
        val waistValues = extractMeasurements(waistHeaderIndex, sizeLabels.size)
        val hipValues = extractMeasurements(hipHeaderIndex, sizeLabels.size)
        val lengthValues = extractMeasurements(lengthHeaderIndex, sizeLabels.size)
        val sleeveValues = extractMeasurements(sleeveHeaderIndex, sizeLabels.size)
        
        Log.d(TAG, "📊 [parseOcrVerticalFormat] Extracted measurements: chest=${chestValues.size}, waist=${waistValues.size}, hip=${hipValues.size}")
        
        // Create entries per Engine Vision Module 2.5
        val rawEntries = mutableListOf<SizeChartEntry>()
        for (i in sizeLabels.indices) {
            val sizeLabel = sizeLabels[i]
            val chest = chestValues.getOrNull(i)
            val waist = waistValues.getOrNull(i)
            val hip = hipValues.getOrNull(i)
            val length = lengthValues.getOrNull(i)
            val sleeve = sleeveValues.getOrNull(i)
            
            if (chest != null || waist != null || hip != null || length != null || sleeve != null) {
                rawEntries.add(
                    SizeChartEntry(
                        sizeLabel = sizeLabel,
                        chest = chest,
                        waist = waist,
                        hip = hip,
                        length = length,
                        sleeve = sleeve
                    )
                )
                Log.d(TAG, "✅ [parseOcrVerticalFormat] Created entry: $sizeLabel - chest=$chest, waist=$waist, hip=$hip")
            }
        }
        
        // Apply normalization and outlier detection per Engine Vision Module 2.5 & 2.6
        val normalized = normalizeSizeChart(rawEntries)
        val withOutliers = detectAndFixOutliers(normalized)
        
        Log.d(TAG, "✅ [BUG #56 Forward] Parsed ${withOutliers.size} entries from forward OCR format (normalized and validated)")
        return withOutliers
    }
    
    /**
     * ✅ BUG #56: Backward parsing (values → headers) - for reversed format
     */
    private fun parseOcrBackward(lines: List<String>): List<SizeChartEntry> {
        // Reverse lines array and parse as forward
        val reversed = lines.reversed()
        Log.d(TAG, "🔍 [BUG #56 Backward] Starting backward OCR parsing (reversed format)")
        val result = parseOcrForward(reversed)
        Log.d(TAG, "✅ [BUG #56 Backward] Backward parsing completed: ${result.size} entries")
        return result
    }
    
    /**
     * ✅ ULTRA-INTELLIGENT SIZE CHART PARSER
     * Engine Vision.md Compliant Implementation
     * 
     * Core Principles:
     * 1. OCR is unreliable → treat as noisy input, NOT truth
     * 2. Measurements > Labels → numeric ranges are ground truth
     * 3. Never assume intent → context decides meaning
     * 4. Everything has confidence → probabilistic output
     * 5. Never reject → always provide probability + reason + option
     * 
     * Handles:
     * - Multi-country formats (US, EU, UK, Asia, Indonesia)
     * - Multi-garment types (shirt, pants, hoodie, jeans, etc.)
     * - Messy/chaotic input (OCR errors, random formatting)
     * - Multi-line association (size labels separated from measurements)
     * - Noise rejection (phone numbers, prices, counts)
     */
    private fun parseFlexibleFormat(text: String): List<SizeChartEntry> {
        val entries = mutableListOf<SizeChartEntry>()
        
        // ✅ LAYER 1: Token Intelligence - Classify & Reject Noise
        Log.d(TAG, "🎯 [Engine Vision] Starting Ultra-Intelligent parsing for input: ${text.take(200)}...")
        val tokens = tokenizeAndClassify(text)
        Log.d(TAG, "🎯 [Engine Vision] Layer 1: Tokenized ${tokens.size} tokens (noise rejected)")
        tokens.forEach { token ->
            Log.d(TAG, "  📝 Token: '${token.text}' (${token.type}) at line ${token.lineIndex}, confidence=${token.confidence}")
        }
        
        // ✅ LAYER 2: Context Intelligence - Cluster & Associate
        val clusters = clusterTokensIntelligently(tokens)
        Log.d(TAG, "🎯 [Engine Vision] Layer 2: Created ${clusters.size} intelligent clusters")
        
        // ✅ LAYER 3: Semantic Intelligence - Infer Dimensions & Validate
        for (cluster in clusters) {
            val entry = clusterToEntryWithSemantics(cluster)
            if (entry != null) {
                entries.add(entry)
                Log.d(TAG, "✅ [Engine Vision] Layer 3: Created entry: ${entry.sizeLabel} - chest=${entry.chest}, waist=${entry.waist}, hip=${entry.hip}")
            }
        }
        
        return entries
    }
    
    // ============================================
    // LAYER 1: TOKEN INTELLIGENCE
    // ============================================
    
    /**
     * ✅ Engine Vision Section "OCR VISION" Compliance:
     * 1. Detects size labels (multi-country)
     * 2. Separates numeric ranges
     * 3. Rejects noise (phone, price, count)
     * 4. Assigns confidence per token
     */
    private data class IntelligentToken(
        val text: String,
        val type: TokenType,
        val position: Int,
        val lineIndex: Int,
        val confidence: Double,
        val regionHint: SizeRegion? = null,  // ✅ Engine Vision: Regional awareness
        val isNoise: Boolean = false  // ✅ Engine Vision: Noise rejection
    )
    
    private enum class TokenType {
        SIZE_LABEL,      // S, M, L, XL, 38, 42, etc. (multi-country)
        MEASUREMENT,     // 62-64, 66~68, 80/84, single numbers
        DIMENSION_HEADER,// CHEST, WAIST, HIP, DADA, PINGGANG, etc.
        GARMENT_HINT,    // SHIRT, PANTS, JEANS, etc. (✅ Engine Vision: Garment awareness)
        NOISE            // Phone, price, count, etc. (✅ Engine Vision: Reject noise)
    }
    
    private fun tokenizeAndClassify(text: String): List<IntelligentToken> {
        val tokens = mutableListOf<IntelligentToken>()
        val lines = text.trim().split("\n").filter { it.isNotBlank() }
        
        // ✅ Engine Vision: Multi-Country Size Label Patterns
        val sizeLabelPatterns = listOf(
            // Standard alphabetic (global)
            Pair(Regex("\\b(XS|S|M|L|XL|XXL|XXXL|XXXXL|XXXXXL|XXXXXXL)\\b", RegexOption.IGNORE_CASE), null as SizeRegion?),
            // Extended numeric sizes
            Pair(Regex("\\b(\\d+)XL\\b", RegexOption.IGNORE_CASE), null),
            // EU numeric (tops: 36-56, bottoms: 38-56)
            Pair(Regex("\\b(3[6-9]|[4-5][0-6])\\b(?![\\d.-])"), SizeRegion.EU),
            // UK numeric (tops: 6-20, bottoms: 28-42)
            Pair(Regex("\\b([6-9]|1[0-9]|20|2[8-9]|[3-4][0-2])\\b(?![\\d.-])"), SizeRegion.UK),
            // US numeric (tops: 2-18, bottoms: 26-42)
            Pair(Regex("\\b([2-9]|1[0-8]|2[6-9]|[3-4][0-2])\\b(?![\\d.-])"), SizeRegion.US),
            // Japanese (SS, S, M, L, XL)
            Pair(Regex("\\b(SS|ES|ESUSU)\\b", RegexOption.IGNORE_CASE), SizeRegion.ASIA),
            // Indonesian (EKSTRA KECIL, KECIL, SEDANG, BESAR, EKSTRA BESAR)
            Pair(Regex("\\b(EKSTRA KECIL|KECIL|SEDANG|BESAR|EKSTRA BESAR)\\b", RegexOption.IGNORE_CASE), SizeRegion.ID),
            // Regional formats (Spanish, French, German)
            Pair(Regex("\\b(PEQUEÑO|MEDIANO|GRANDE|PETIT|MOYEN|GRAND|KLEIN|MITTEL|GROSS)\\b", RegexOption.IGNORE_CASE), null),
            // Size prefix
            Pair(Regex("\\bSIZE[\\s-]?(\\d+)\\b", RegexOption.IGNORE_CASE), null),
        )
        
        // ✅ Engine Vision: Measurement Patterns (ranges, single values)
        // ✅ FIX: Improved patterns to handle quotes and various separators
        val measurementPatterns = listOf(
            Regex("(\\d{2,}(?:\\.\\d+)?)\\s*[-~–—]\\s*(\\d{2,}(?:\\.\\d+)?)\\s*[\"']?"),  // Range: 62-64 or 58-60"
            Regex("(\\d{2,}(?:\\.\\d+)?)\\s*/\\s*(\\d{2,}(?:\\.\\d+)?)\\s*[\"']?"),      // Range: 62/64
            Regex("(\\d{2,}(?:\\.\\d+)?)\\s+to\\s+(\\d{2,}(?:\\.\\d+)?)\\s*[\"']?", RegexOption.IGNORE_CASE),  // Range: 62 to 64
            Regex("\\b(\\d{2,}(?:\\.\\d+)?)\\b(?![XL])"),  // Single number (not part of XL)
        )
        
        // ✅ Engine Vision: Dimension Headers (multi-language)
        val dimensionHeaderPatterns = listOf(
            Regex("\\b(CHEST|BUST|DADA|DADAR|TORSO)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(WAIST|PINGGANG|CINTUR|TAILLE)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(HIP|HIPS|PINGGUL|CADERA|HANCHES)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(INSEAM|LENGTH|PANJANG|LONGUEUR)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(SLEEVE|LENGAN|MANCHE)\\b", RegexOption.IGNORE_CASE),
        )
        
        // ✅ Engine Vision: Garment Type Detection
        val garmentPatterns = listOf(
            Regex("\\b(SHIRT|TSHIRT|T-SHIRT|KEMEJA|CHEMISE)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(PANTS|TROUSERS|JEANS|CELANA|PANTALON)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(HOODIE|SWEATER|JACKET|JAKET|VESTE)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(SHORTS|SHORT|BERMUDA)\\b", RegexOption.IGNORE_CASE),
        )
        
        // ✅ Engine Vision: Noise Rejection Patterns
        val noisePatterns = listOf(
            Regex("\\b(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b"),  // Phone numbers
            Regex("\\$\\d+(\\.\\d{2})?|\\d+\\s*(USD|EUR|GBP|IDR|RP)\\b", RegexOption.IGNORE_CASE),  // Prices
            Regex("\\b(\\d+)\\s*(ITEMS?|PIECES?|PCS?)\\b", RegexOption.IGNORE_CASE),  // Counts
            Regex("\\b(EMAIL|@|WWW\\.|HTTP)\\b", RegexOption.IGNORE_CASE),  // URLs/emails
        )
        
        var charPosition = 0
        for (lineIndex in lines.indices) {
            val line = lines[lineIndex]
            
            // ✅ STEP 1: Reject noise first (Engine Vision: Noise rejection)
            val isNoise = noisePatterns.any { it.containsMatchIn(line) }
            if (isNoise) {
                Log.d(TAG, "🚫 [Engine Vision] Rejected noise: ${line.take(50)}")
                continue
            }
            
            // ✅ STEP 2: Detect garment type (Engine Vision: Garment awareness)
            for (pattern in garmentPatterns) {
                pattern.findAll(line).forEach { match ->
                    tokens.add(IntelligentToken(
                        text = match.value,
                        type = TokenType.GARMENT_HINT,
                        position = charPosition + match.range.first,
                        lineIndex = lineIndex,
                        confidence = 1.0,
                        isNoise = false
                    ))
                }
            }
            
            // ✅ STEP 3: Detect dimension headers (Engine Vision: Context awareness)
            for (pattern in dimensionHeaderPatterns) {
                pattern.findAll(line).forEach { match ->
                    tokens.add(IntelligentToken(
                        text = match.value,
                        type = TokenType.DIMENSION_HEADER,
                        position = charPosition + match.range.first,
                        lineIndex = lineIndex,
                        confidence = 1.0,
                        isNoise = false
                    ))
                }
            }
            
            // ✅ STEP 4: Detect size labels (Engine Vision: Multi-country)
            // ✅ FIX: Process extended sizes (4XL, 5XL, 6XL) FIRST before standard sizes to avoid conflicts
            val extendedSizePattern = Regex("\\b(\\d+)XL\\b", RegexOption.IGNORE_CASE)
            extendedSizePattern.findAll(line).forEach { match ->
                val matched = match.value
                // Check if this line also has measurements - if yes, it's likely a size label
                val hasMeasurements = measurementPatterns.any { it.containsMatchIn(line) }
                // Extended sizes (4XL+) are almost always size labels, not measurements
                if (matched.toIntOrNull()?.let { it >= 4 } == true || !hasMeasurements || matched.contains(Regex("[A-Za-z]"))) {
                    tokens.add(IntelligentToken(
                        text = matched,
                        type = TokenType.SIZE_LABEL,
                        position = charPosition + match.range.first,
                        lineIndex = lineIndex,
                        confidence = 0.95,  // Higher confidence for extended sizes
                        regionHint = null,
                        isNoise = false
                    ))
                    Log.d(TAG, "✅ [Engine Vision] Detected extended size label: '$matched' at line $lineIndex")
                }
            }
            
            // Then process other size label patterns
            for ((pattern, regionHint) in sizeLabelPatterns) {
                // Skip extended sizes pattern as we already processed it
                if (pattern.pattern.contains("\\d+XL")) continue
                
                pattern.findAll(line).forEach { match ->
                    val matched = match.value
                    // Skip if already detected as extended size
                    val alreadyDetected = tokens.any { 
                        it.lineIndex == lineIndex && 
                        it.type == TokenType.SIZE_LABEL && 
                        it.text.equals(matched, ignoreCase = true)
                    }
                    if (alreadyDetected) return@forEach
                    
                    // ✅ FIX: More strict validation - don't accept single letters (S, M, L) if line has measurements
                    val isMeasurement = measurementPatterns.any { it.containsMatchIn(line) }
                    val isSingleLetter = matched.length == 1 && matched.matches(Regex("[SML]", RegexOption.IGNORE_CASE))
                    
                    // If line has measurements and token is single letter, it's likely NOT a size label
                    if (isMeasurement && isSingleLetter) {
                        Log.d(TAG, "⚠️ [Engine Vision] Rejected single letter '$matched' as size label (line has measurements)")
                        return@forEach
                    }
                    
                    if (!isMeasurement || matched.length > 2 || matched.contains(Regex("[A-Za-z]"))) {
                        tokens.add(IntelligentToken(
                            text = matched,
                            type = TokenType.SIZE_LABEL,
                            position = charPosition + match.range.first,
                            lineIndex = lineIndex,
                            confidence = if (isSingleLetter) 0.7 else 0.9,  // Lower confidence for single letters
                            regionHint = regionHint,
                            isNoise = false
                        ))
                        Log.d(TAG, "✅ [Engine Vision] Detected size label: '$matched' at line $lineIndex")
                    }
                }
            }
            
            // ✅ STEP 5: Detect measurements (Engine Vision: Ground truth)
            // ✅ FIX: Improved measurement detection with validation to reject unreasonable values
            for (pattern in measurementPatterns) {
                pattern.findAll(line).forEach { match ->
                    val matched = match.value.trim()
                    // Skip if already detected as size label
                    val isSizeLabel = tokens.any { 
                        it.lineIndex == lineIndex && 
                        it.type == TokenType.SIZE_LABEL && 
                        matched.contains(it.text, ignoreCase = true)
                    }
                    
                    if (isSizeLabel) return@forEach
                    
                    // ✅ FIX: Validate measurement values are reasonable (Engine Vision: Reject bad data)
                    val numbers = Regex("\\d+(?:\\.\\d+)?").findAll(matched).mapNotNull { it.value.toDoubleOrNull() }.toList()
                    val hasUnreasonableValue = numbers.any { num ->
                        // Reject measurements outside reasonable range (15-250cm or 6-100 inches)
                        num > 250.0 || (num > 100.0 && !matched.contains("\"")) || num < 15.0
                    }
                    
                    if (hasUnreasonableValue) {
                        Log.d(TAG, "🚫 [Engine Vision] Rejected unreasonable measurement: '$matched' (values: $numbers)")
                        return@forEach
                    }
                    
                    // Validate it's not a size label (standalone numbers that could be sizes)
                    val couldBeSize = matched.toIntOrNull()?.let { num ->
                        num in 26..56  // Could be EU/UK/US size or pants waist
                    } ?: false
                    
                    val isInRange = matched.contains("-") || matched.contains("~") || 
                                   matched.contains("/") || matched.contains("to") ||
                                   matched.contains("\"")  // ✅ FIX: Handle inch symbol
                    
                    // ✅ FIX: More lenient - accept if it's a range OR if it's a reasonable measurement number
                    if (isInRange || (!couldBeSize && matched.length >= 2) || matched.length > 2) {
                        tokens.add(IntelligentToken(
                            text = matched,
                            type = TokenType.MEASUREMENT,
                            position = charPosition + match.range.first,
                            lineIndex = lineIndex,
                            confidence = if (isInRange) 0.95 else 0.75,
                            isNoise = false
                        ))
                        Log.d(TAG, "✅ [Engine Vision] Detected measurement: '$matched' at line $lineIndex")
                    }
                }
            }
            
            // ✅ FIX: Also detect standalone measurements that might have been missed
            // Pattern: numbers with quotes (e.g., "58-60"")
            val quotedRangePattern = Regex("""(\d{2,}(?:\.\d+)?)\s*[-~–—]\s*(\d{2,}(?:\.\d+)?)\s*[""]""")
            quotedRangePattern.findAll(line).forEach { match ->
                val matched = match.value.trim()
                val alreadyDetected = tokens.any { 
                    it.lineIndex == lineIndex && 
                    it.type == TokenType.MEASUREMENT && 
                    it.text.contains(match.groupValues[1]) && it.text.contains(match.groupValues[2])
                }
                if (!alreadyDetected) {
                    tokens.add(IntelligentToken(
                        text = matched,
                        type = TokenType.MEASUREMENT,
                        position = charPosition + match.range.first,
                        lineIndex = lineIndex,
                        confidence = 0.95,
                        isNoise = false
                    ))
                    Log.d(TAG, "✅ [Engine Vision] Detected quoted measurement: '$matched' at line $lineIndex")
                }
            }
            
            charPosition += line.length + 1
        }
        
        return tokens.sortedBy { it.position }
    }
    
    // ============================================
    // LAYER 2: CONTEXT INTELLIGENCE
    // ============================================
    
    /**
     * ✅ Engine Vision: Context-aware clustering
     * Associates size labels with measurements intelligently
     * Handles multi-line, messy, chaotic input
     */
    private data class IntelligentCluster(
        val sizeLabel: IntelligentToken,
        val measurements: List<IntelligentToken>,
        val dimensionHeaders: List<IntelligentToken> = emptyList(),
        val garmentHint: IntelligentToken? = null,
        val confidence: Double = 1.0  // ✅ Engine Vision: Probabilistic
    )
    
    private fun clusterTokensIntelligently(tokens: List<IntelligentToken>): List<IntelligentCluster> {
        val clusters = mutableListOf<IntelligentCluster>()
        val usedTokens = mutableSetOf<IntelligentToken>()
        
        Log.d(TAG, "🎯 [Engine Vision] Layer 2: Starting clustering with ${tokens.size} tokens")
        Log.d(TAG, "📊 [Engine Vision] Token breakdown: ${tokens.count { it.type == TokenType.SIZE_LABEL }} size labels, ${tokens.count { it.type == TokenType.MEASUREMENT }} measurements")
        
        // Group by line first
        val tokensByLine = tokens.groupBy { it.lineIndex }
        
        // Detect garment type from all tokens
        val garmentHint = tokens.firstOrNull { it.type == TokenType.GARMENT_HINT }
        
        // ✅ FIX: Collect all unused size labels and measurements first
        val allSizeLabels = tokens.filter { it.type == TokenType.SIZE_LABEL && it !in usedTokens }
        val allMeasurements = tokens.filter { it.type == TokenType.MEASUREMENT && it !in usedTokens }
        
        Log.d(TAG, "📊 [Engine Vision] Available: ${allSizeLabels.size} size labels, ${allMeasurements.size} measurements")
        
        // ✅ Strategy 1: Same line association (highest confidence)
        for ((lineIndex, lineTokens) in tokensByLine) {
            val sizeLabels = lineTokens.filter { it.type == TokenType.SIZE_LABEL && it !in usedTokens }
            val measurements = lineTokens.filter { it.type == TokenType.MEASUREMENT && it !in usedTokens }
            val headers = lineTokens.filter { it.type == TokenType.DIMENSION_HEADER && it !in usedTokens }
            
            if (sizeLabels.isNotEmpty() && measurements.isNotEmpty()) {
                for (sizeLabel in sizeLabels) {
                    clusters.add(IntelligentCluster(
                        sizeLabel = sizeLabel,
                        measurements = measurements,
                        dimensionHeaders = headers,
                        garmentHint = garmentHint,
                        confidence = 0.95  // High confidence for same-line association
                    ))
                    usedTokens.add(sizeLabel)
                    usedTokens.addAll(measurements)
                    usedTokens.addAll(headers)
                    Log.d(TAG, "✅ [Engine Vision] Strategy 1: Same-line cluster - ${sizeLabel.text} with ${measurements.size} measurements")
                }
            }
        }
        
        // ✅ Strategy 2: Next line association (medium confidence) - IMPROVED to handle multiple next lines
        // ✅ FIX: Prioritize extended sizes (4XL, 5XL, 6XL) first
        val extendedSizeLabels = allSizeLabels.filter { 
            it.text.matches(Regex("\\d+XL", RegexOption.IGNORE_CASE)) && 
            it.text.toIntOrNull()?.let { num -> num >= 4 } == true
        }
        val standardSizeLabels = allSizeLabels.filter { it !in extendedSizeLabels }
        
        // Process extended sizes first (higher priority)
        val prioritizedSizeLabels = extendedSizeLabels + standardSizeLabels
        
        for (sizeLabel in prioritizedSizeLabels) {
            if (sizeLabel in usedTokens) continue
            
            val lineIndex = sizeLabel.lineIndex
            val headers = tokensByLine[lineIndex]?.filter { 
                it.type == TokenType.DIMENSION_HEADER && it !in usedTokens 
            } ?: emptyList()
            
            // ✅ FIX: Check next 3 lines for measurements (more flexible)
            val nearbyMeasurements = mutableListOf<IntelligentToken>()
            for (offset in 1..3) {
                val nextLineTokens = tokensByLine[lineIndex + offset]?.filter { it !in usedTokens } ?: emptyList()
                val nextMeasurements = nextLineTokens.filter { it.type == TokenType.MEASUREMENT }
                nearbyMeasurements.addAll(nextMeasurements)
            }
            
            if (nearbyMeasurements.isNotEmpty()) {
                // ✅ FIX: Associate with closest measurement(s) - take first 1-2 measurements
                val associatedMeasurements = nearbyMeasurements.take(2)
                clusters.add(IntelligentCluster(
                    sizeLabel = sizeLabel,
                    measurements = associatedMeasurements,
                    dimensionHeaders = headers,
                    garmentHint = garmentHint,
                    confidence = 0.80  // Medium confidence for next-line association
                ))
                usedTokens.add(sizeLabel)
                usedTokens.addAll(associatedMeasurements)
                usedTokens.addAll(headers)
                Log.d(TAG, "✅ [Engine Vision] Strategy 2: Next-line cluster - ${sizeLabel.text} with ${associatedMeasurements.size} measurements")
            } else {
                // ✅ Strategy 3: Size label without measurements (low confidence, but still create entry)
                // ✅ Engine Vision: Never reject - return entry with just size label
                clusters.add(IntelligentCluster(
                    sizeLabel = sizeLabel,
                    measurements = emptyList(),
                    dimensionHeaders = headers,
                    garmentHint = garmentHint,
                    confidence = 0.60  // Low confidence - no measurements found
                ))
                usedTokens.add(sizeLabel)
                usedTokens.addAll(headers)
                Log.d(TAG, "⚠️ [Engine Vision] Strategy 3: Size label only - ${sizeLabel.text} (no measurements)")
            }
        }
        
        // ✅ Strategy 4: Measurements without size label - infer from position
        // ✅ FIX: Only infer if we have reasonable number of measurements (avoid false positives)
        val remainingMeasurements = allMeasurements.filter { it !in usedTokens }
        if (remainingMeasurements.isNotEmpty() && remainingMeasurements.size <= 10) {
            Log.d(TAG, "📊 [Engine Vision] Strategy 4: ${remainingMeasurements.size} orphaned measurements, inferring size labels")
            // ✅ FIX: Group measurements by line to infer size labels more intelligently
            val measurementsByLine = remainingMeasurements.groupBy { it.lineIndex }
            var inferredIndex = 0
            for ((lineIdx, lineMeasurements) in measurementsByLine) {
                // ✅ FIX: Use actual size progression (4XL, 5XL, 6XL) instead of arbitrary numbers
                val baseSizeLabels = listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL")
                val inferredSizeLabel = if (inferredIndex < baseSizeLabels.size) {
                    baseSizeLabels[inferredIndex]
                } else {
                    // Start from 4XL for extended sizes, cap at 6XL
                    val extendedIndex = (inferredIndex - baseSizeLabels.size + 4).coerceAtMost(6)
                    "${extendedIndex}XL"
                }
                
                clusters.add(IntelligentCluster(
                    sizeLabel = IntelligentToken(
                        text = inferredSizeLabel,
                        type = TokenType.SIZE_LABEL,
                        position = lineMeasurements.firstOrNull()?.position ?: lineIdx,
                        lineIndex = lineIdx,
                        confidence = 0.50,  // Lower confidence for inferred
                        isNoise = false
                    ),
                    measurements = lineMeasurements,
                    dimensionHeaders = emptyList(),
                    garmentHint = garmentHint,
                    confidence = 0.70  // Medium-low confidence for inferred size
                ))
                usedTokens.addAll(lineMeasurements)
                inferredIndex++
                Log.d(TAG, "✅ [Engine Vision] Strategy 4: Inferred cluster - $inferredSizeLabel with ${lineMeasurements.size} measurements")
            }
        } else if (remainingMeasurements.size > 10) {
            Log.w(TAG, "⚠️ [Engine Vision] Too many orphaned measurements (${remainingMeasurements.size}), skipping inference to avoid false positives")
        }
        
        Log.d(TAG, "✅ [Engine Vision] Layer 2: Created ${clusters.size} clusters total")
        return clusters
    }
    
    // ============================================
    // LAYER 3: SEMANTIC INTELLIGENCE
    // ============================================
    
    /**
     * ✅ Engine Vision: Semantic dimension inference
     * - Value range-based inference (chest: 80-120cm, waist: 60-110cm)
     * - Garment-type-aware (shirt = chest priority, pants = waist priority)
     * - Header-based assignment (if present)
     * - Position-based fallback
     */
    private fun clusterToEntryWithSemantics(cluster: IntelligentCluster): SizeChartEntry? {
        val normalizedSizeLabel = SizeLabelNormalizer.normalize(cluster.sizeLabel.text)
        
        if (cluster.measurements.isEmpty()) {
            // ✅ Engine Vision: Never reject - return entry with just size label
            return SizeChartEntry(
                sizeLabel = normalizedSizeLabel
            )
        }
        
        // Parse all measurements
        val parsedMeasurements = cluster.measurements.mapNotNull { token ->
            parseRange(token.text)?.let { range ->
                // ✅ FIX: Validate measurement range is reasonable (Engine Vision: Reject bad data)
                val midValue = (range.min + range.max) / 2.0
                val isReasonable = when (range.unit) {
                    MeasurementUnit.CM -> midValue in 15.0..250.0  // Reasonable range: 15-250cm
                    MeasurementUnit.INCH -> midValue in 6.0..100.0  // Reasonable range: 6-100 inches
                }
                
                if (!isReasonable) {
                    Log.w(TAG, "🚫 [Engine Vision] Rejected unreasonable measurement range: $range (midValue=$midValue ${range.unit})")
                    null
                } else {
                    Pair(range, token.confidence)  // Preserve token confidence
                }
            }
        }
        
        if (parsedMeasurements.isEmpty()) {
            // ✅ Engine Vision: Never reject - return entry with just size label if no valid measurements
            return SizeChartEntry(sizeLabel = normalizedSizeLabel)
        }
        
        // ✅ Engine Vision: Garment-aware dimension inference
        val garmentType = inferGarmentTypeFromCluster(cluster)
        
        val chest = mutableListOf<Pair<MeasurementRange, Double>>()
        val waist = mutableListOf<Pair<MeasurementRange, Double>>()
        val hip = mutableListOf<Pair<MeasurementRange, Double>>()
        val inseam = mutableListOf<Pair<MeasurementRange, Double>>()
        
        // ✅ Priority 1: Header-based assignment (highest confidence)
        val hasChestHeader = cluster.dimensionHeaders.any { 
            it.text.contains("CHEST", ignoreCase = true) || 
            it.text.contains("DADA", ignoreCase = true) ||
            it.text.contains("BUST", ignoreCase = true)
        }
        val hasWaistHeader = cluster.dimensionHeaders.any { 
            it.text.contains("WAIST", ignoreCase = true) || 
            it.text.contains("PINGGANG", ignoreCase = true)
        }
        val hasHipHeader = cluster.dimensionHeaders.any { 
            it.text.contains("HIP", ignoreCase = true) || 
            it.text.contains("PINGGUL", ignoreCase = true)
        }
        val hasInseamHeader = cluster.dimensionHeaders.any { 
            it.text.contains("INSEAM", ignoreCase = true) || 
            it.text.contains("LENGTH", ignoreCase = true)
        }
        
        // ✅ Priority 2: Value range-based inference (Engine Vision: Measurements > Labels)
        for ((index, measurementPair) in parsedMeasurements.withIndex()) {
            val (measurement, tokenConfidence) = measurementPair
            val midValue = (measurement.min + measurement.max) / 2.0
            val combinedConfidence = measurement.confidence * tokenConfidence * cluster.confidence
            
            when {
                // Header-based (highest priority)
                hasChestHeader && index == 0 -> chest.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                hasWaistHeader && index == 0 -> waist.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                hasHipHeader && index == 0 -> hip.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                hasInseamHeader && index == 0 -> inseam.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                
                // ✅ Engine Vision: Garment-type-aware inference
                // Upper body garments: chest priority
                midValue in 80.0..120.0 && garmentType in listOf(
                    GarmentType.SHIRT, GarmentType.TSHIRT, GarmentType.POLO_SHIRT,
                    GarmentType.HOODIE, GarmentType.SWEATER, GarmentType.JACKET, GarmentType.BLAZER
                ) -> {
                    if (chest.isEmpty()) {
                        chest.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                    } else if (waist.isEmpty()) {
                        waist.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                    } else {
                        hip.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                    }
                }
                
                // Lower body garments: waist priority
                midValue in 60.0..110.0 && garmentType in listOf(
                    GarmentType.JEANS, GarmentType.TROUSERS, GarmentType.CHINOS,
                    GarmentType.CARGO_PANTS, GarmentType.SHORTS, GarmentType.JOGGERS
                ) -> {
                    if (waist.isEmpty()) {
                        waist.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                    } else if (hip.isEmpty()) {
                        hip.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                    } else {
                        chest.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                    }
                }
                
                // ✅ Engine Vision: Value range inference (universal)
                midValue in 85.0..120.0 -> {
                    if (hip.isEmpty()) hip.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                    else if (waist.isEmpty()) waist.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                    else chest.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                }
                midValue in 50.0..100.0 && garmentType in listOf(
                    GarmentType.JEANS, GarmentType.TROUSERS, GarmentType.CHINOS,
                    GarmentType.CARGO_PANTS, GarmentType.SHORTS, GarmentType.JOGGERS
                ) -> {
                    inseam.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                }
                
                // Position-based fallback
                else -> {
                    when (index) {
                        0 -> {
                            when (garmentType) {
                                in listOf(GarmentType.SHIRT, GarmentType.TSHIRT, GarmentType.POLO_SHIRT,
                                         GarmentType.HOODIE, GarmentType.SWEATER, GarmentType.JACKET, GarmentType.BLAZER) -> 
                                    chest.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                                in listOf(GarmentType.JEANS, GarmentType.TROUSERS, GarmentType.CHINOS,
                                         GarmentType.CARGO_PANTS, GarmentType.SHORTS, GarmentType.JOGGERS) -> 
                                    waist.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                                else -> waist.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                            }
                        }
                        1 -> {
                            when (garmentType) {
                                in listOf(GarmentType.SHIRT, GarmentType.TSHIRT, GarmentType.POLO_SHIRT,
                                         GarmentType.HOODIE, GarmentType.SWEATER, GarmentType.JACKET, GarmentType.BLAZER) -> 
                                    waist.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                                in listOf(GarmentType.JEANS, GarmentType.TROUSERS, GarmentType.CHINOS,
                                         GarmentType.CARGO_PANTS, GarmentType.SHORTS, GarmentType.JOGGERS) -> 
                                    hip.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                                else -> hip.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                            }
                        }
                        2 -> hip.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                        else -> waist.add(Pair(measurement.copy(confidence = combinedConfidence), combinedConfidence))
                    }
                }
            }
        }
        
        // ✅ Engine Vision: Use best measurement per dimension (highest confidence)
        return SizeChartEntry(
            sizeLabel = normalizedSizeLabel,
            chest = chest.maxByOrNull { it.second }?.first,
            waist = waist.maxByOrNull { it.second }?.first,
            hip = hip.maxByOrNull { it.second }?.first,
            inseam = inseam.maxByOrNull { it.second }?.first
        )
    }
    
    /**
     * ✅ Helper: Infer garment type from cluster
     */
    private fun inferGarmentTypeFromCluster(cluster: IntelligentCluster): GarmentType? {
        val garmentText = cluster.garmentHint?.text?.uppercase() ?: return null
        return when {
            garmentText.contains("SHIRT", ignoreCase = true) || garmentText.contains("KEMEJA", ignoreCase = true) -> GarmentType.SHIRT
            garmentText.contains("JEANS", ignoreCase = true) || garmentText.contains("CELANA", ignoreCase = true) -> GarmentType.JEANS
            garmentText.contains("HOODIE", ignoreCase = true) -> GarmentType.HOODIE
            garmentText.contains("SHORT", ignoreCase = true) -> GarmentType.SHORTS
            else -> null
        }
    }
    
    /**
     * ✅ Helper: Infer size label from position
     */
    /**
     * ✅ Helper: Infer size label from position
     * ✅ FIX: Use realistic size progression (4XL, 5XL, 6XL max) instead of arbitrary numbers
     */
    private fun inferSizeLabelFromPosition(entryIndex: Int, @Suppress("UNUSED_PARAMETER") lineIndex: Int): String {
        val baseSizeLabels = listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL")
        return if (entryIndex < baseSizeLabels.size) {
            baseSizeLabels[entryIndex]
        } else {
            // ✅ FIX: Cap extended sizes at 6XL (realistic maximum) to avoid false positives like 15XL, 19XL, etc.
            val extendedIndex = (entryIndex - baseSizeLabels.size + 4).coerceAtMost(6)
            "${extendedIndex}XL"
        }
    }
    
    /**
     * Enhanced Range Parsing with OCR Error Correction per Engine Vision Module 2.4
     * Handles common OCR errors: O→0, I/l→1, B→8, S→5, G→6
     */
    private fun parseRange(text: String): MeasurementRange? {
        if (text.isBlank()) return null
        
        val originalText = text
        
        // Step 1: Clean and normalize per Engine Vision
        // ✅ CRITICAL FIX: Only apply OCR letter-to-digit substitutions on
        // segments that already contain digits. This prevents corrupting
        // text headers like "CHEST" -> "CHE5T" or "BUST" -> "8U5T".
        var cleaned = text
            .trim()
            .replace(Regex("\\s+"), "")           // Remove spaces
            .replace(Regex("[—~–]"), "-")        // Normalize separators
        
        // Only apply aggressive OCR correction if the text looks numeric
        // (contains at least one digit, suggesting it's a measurement, not a header)
        if (cleaned.any { it.isDigit() }) {
            cleaned = cleaned
                .replace(Regex("[Oo](?=\\d)|(?<=\\d)[Oo]"), "0")  // O → 0 only adjacent to digits
                .replace(Regex("[Il](?=\\d)|(?<=\\d)[Il]"), "1")  // I/l → 1 only adjacent to digits
        }
        
        if (cleaned != originalText) {
            android.util.Log.d(TAG, "🎯 [Engine Vision Module 2] OCR error correction: '$originalText' → '$cleaned'")
        }
        
        // ✅ ENGINE VISION FIX: Detect unit correctly - if input has " or inch, it's already in inch, don't convert
        val isInch = cleaned.contains("\"") || cleaned.contains("inch", ignoreCase = true)
        // ✅ FIX: Don't convert if already in correct unit - numbers should be used as-is
        // Numbers are used as-is, unit is preserved (INCH or CM)
        
        // Step 2: Try multiple parsing patterns per Engine Vision
        val patterns = listOf(
            Regex("^(\\d+(?:\\.\\d+)?)-(\\d+(?:\\.\\d+)?)$"),              // "80-84"
            Regex("^(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?)$"),             // "80/84"
            Regex("^(\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)$"),        // "80 - 84"
            Regex("^(\\d+(?:\\.\\d+)?)$")                                    // Single value: "82" → treat as ±2
        )
        
        for (pattern in patterns) {
            val match = pattern.find(cleaned)
            if (match != null) {
                if (match.groupValues.size == 3) {
                    // Range pattern - ✅ FIX: Use numbers as-is, don't multiply by factor
                    val min = match.groupValues[1].toDoubleOrNull()
                    val max = match.groupValues[2].toDoubleOrNull()
                    
                    // ✅ FIX: Validate range is reasonable based on unit
                    val maxRange = if (isInch) 20.0 else 20.0  // Max 20 units difference
                    if (min != null && max != null && max > min && max - min <= maxRange) {
                        // High confidence for well-parsed range
                        return MeasurementRange(
                            min, 
                            max, 
                            if (isInch) MeasurementUnit.INCH else MeasurementUnit.CM,
                            confidence = 0.95  // High confidence for clean range parsing
                        )
                    }
                } else if (match.groupValues.size == 2) {
                    // Single value - assume ±2cm or ±3inch tolerance per Engine Vision
                    val value = match.groupValues[1].toDoubleOrNull()
                    if (value != null) {
                        val tolerance = if (isInch) 3.0 else 2.0
                        // Medium confidence for single value (assumed range)
                        return MeasurementRange(
                            (value - tolerance).coerceAtLeast(0.0), 
                            value + tolerance, 
                            if (isInch) MeasurementUnit.INCH else MeasurementUnit.CM,
                            confidence = 0.80  // Medium confidence for single value assumption
                        )
                    }
                }
            }
        }
        
        // Step 3: Fuzzy recovery attempt per Engine Vision
        val numbers = Regex("\\d+(?:\\.\\d+)?").findAll(cleaned).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (numbers.size >= 2) {
            val sorted = numbers.sorted()
            // ✅ FIX: Use numbers as-is, don't multiply by factor
            val min = sorted.first()
            val max = sorted.last()
            val maxRange = if (isInch) 20.0 else 20.0  // Max 20 units difference
            if (max > min && max - min <= maxRange) {
                android.util.Log.d(TAG, "🎯 [Engine Vision Module 2] Fuzzy recovery: extracted range [$min, $max] from '$originalText' (unit: ${if (isInch) "INCH" else "CM"})")
                // Lower confidence for fuzzy recovery (OCR uncertainty)
                return MeasurementRange(
                    min,
                    max,
                    if (isInch) MeasurementUnit.INCH else MeasurementUnit.CM,
                    confidence = 0.60  // Lower confidence for fuzzy recovery
                )
            }
        }
        
        android.util.Log.w(TAG, "⚠️ [Engine Vision Module 2] Unparseable range: '$originalText'")
        return null  // Unparseable
    }
    
    /**
     * Size Chart Normalization per Engine Vision Module 2.5
     * Converts raw parsed entries to normalized entries with confidence and warnings
     */
    private fun normalizeSizeChart(rawEntries: List<SizeChartEntry>): List<SizeChartEntry> {
        android.util.Log.d(TAG, "🎯 [Engine Vision Module 2.5] Normalizing ${rawEntries.size} entries")
        
        // For now, return entries as-is since SizeChartEntry doesn't have confidence/warnings
        // Confidence and warnings are handled at parsing level via parseRange()
        // This function is a placeholder for future enhancement when we add confidence tracking
        
        val normalized = rawEntries.map { entry ->
            // Log confidence indicators
            val warnings = mutableListOf<String>()
            
            // Check each measurement for potential issues
            listOf(
                "chest" to entry.chest,
                "waist" to entry.waist,
                "hip" to entry.hip,
                "length" to entry.length,
                "sleeve" to entry.sleeve
            ).forEach { (name, range) ->
                if (range != null) {
                    // Check if range is suspiciously small or large
                    val rangeWidth = range.max - range.min
                    if (rangeWidth < 0.5 || rangeWidth > 10.0) {
                        warnings.add("Suspicious $name range: ${range.min}-${range.max}cm")
                    }
                }
            }
            
            if (warnings.isNotEmpty()) {
                android.util.Log.w(TAG, "⚠️ [Engine Vision Module 2.5] Entry ${entry.sizeLabel} warnings: ${warnings.joinToString(", ")}")
            }
            
            entry
        }
        
        android.util.Log.d(TAG, "✅ [Engine Vision Module 2.5] Normalized ${normalized.size} entries")
        return normalized
    }
    
    /**
     * Outlier Detection per Engine Vision Module 2.6
     * Checks for progression consistency: S < M < L < XL
     */
    private fun detectAndFixOutliers(entries: List<SizeChartEntry>): List<SizeChartEntry> {
        android.util.Log.d(TAG, "🎯 [Engine Vision Module 2.6] Detecting outliers in ${entries.size} entries")
        
        if (entries.size < 2) return entries
        
        // Sort by size label order (S, M, L, XL, XXL)
        val sizeOrder = mapOf("XS" to 0, "S" to 1, "M" to 2, "L" to 3, "XL" to 4, "XXL" to 5, "XXXL" to 6, "3XL" to 6, "4XL" to 7, "5XL" to 8, "6XL" to 9)
        val sorted = entries.sortedBy { sizeOrder[it.sizeLabel.uppercase()] ?: 999 }
        
        // Check chest progression consistency per Engine Vision
        val chestMidpoints = sorted.mapNotNull { entry ->
            entry.chest?.let { (it.min + it.max) / 2.0 }
        }
        
        if (chestMidpoints.size >= 2) {
            for (i in 1 until chestMidpoints.size) {
                val diff = chestMidpoints[i] - chestMidpoints[i - 1]
                
                // Expected increment: 4-8cm per size per Engine Vision
                if (diff < 2.0 || diff > 12.0) {
                    android.util.Log.w(TAG, "⚠️ [Engine Vision Module 2.6] Suspicious size progression: ${diff}cm difference between ${sorted[i-1].sizeLabel} and ${sorted[i].sizeLabel}")
                    // Note: We can't modify confidence/warnings in SizeChartEntry model yet
                    // This is logged for future enhancement
            } else {
                    android.util.Log.d(TAG, "✅ [Engine Vision Module 2.6] Normal progression: ${diff}cm between ${sorted[i-1].sizeLabel} and ${sorted[i].sizeLabel}")
                }
            }
        }
        
        android.util.Log.d(TAG, "✅ [Engine Vision Module 2.6] Outlier detection complete")
        return sorted
    }
    
    /**
     * ✅ FIX #43: Smart garment type detection
     * Detects garment type from size chart text
     */
    fun detectGarmentType(text: String): GarmentType? {
        val textLower = text.lowercase()
        
        return when {
            textLower.contains("jean") || textLower.contains("denim") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: JEANS")
                GarmentType.JEANS
            }
            textLower.contains("shirt") && !textLower.contains("t-shirt") && !textLower.contains("tee") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: SHIRT")
                GarmentType.SHIRT
            }
            textLower.contains("t-shirt") || textLower.contains("tee") || textLower.contains("tshirt") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: TSHIRT")
                GarmentType.TSHIRT
            }
            textLower.contains("hoodie") || textLower.contains("hoody") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: HOODIE")
                GarmentType.HOODIE
            }
            textLower.contains("blazer") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: BLAZER")
                GarmentType.BLAZER
            }
            textLower.contains("jacket") && !textLower.contains("blazer") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: JACKET")
                GarmentType.JACKET
            }
            textLower.contains("polo") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: POLO_SHIRT")
                GarmentType.POLO_SHIRT
            }
            textLower.contains("sweater") || textLower.contains("pullover") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: SWEATER")
                GarmentType.SWEATER
            }
            textLower.contains("chino") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: CHINOS")
                GarmentType.CHINOS
            }
            textLower.contains("trouser") || textLower.contains("pant") && !textLower.contains("short") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: TROUSERS")
                GarmentType.TROUSERS
            }
            textLower.contains("cargo") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: CARGO_PANTS")
                GarmentType.CARGO_PANTS
            }
            textLower.contains("short") && !textLower.contains("sleeve") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: SHORTS")
                GarmentType.SHORTS
            }
            textLower.contains("jogger") -> {
                Log.d(TAG, "🎯 [FIX #43] Detected garment type: JOGGERS")
                GarmentType.JOGGERS
            }
            else -> {
                Log.d(TAG, "🔍 [FIX #43] No garment type detected from text")
                null
            }
        }
    }
}
