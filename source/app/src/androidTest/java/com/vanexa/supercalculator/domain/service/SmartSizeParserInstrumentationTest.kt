package com.vanexa.supercalculator.domain.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.vanexa.supercalculator.domain.model.MeasurementUnit

/**
 * Instrumented test for SmartSizeParser.
 * Verifies the AI Service functions correctly on an Android device (Real or Emulator).
 * Focuses on the "Hybrid Parsing Bridge" output accuracy.
 */
@RunWith(AndroidJUnit4::class)
class SmartSizeParserInstrumentationTest {

    @Test
    fun testSmartParsing_HighFidelityBridge() = runBlocking {
        // Setup - We purposefully don't provide a real model path to force the Simulation Fallback mechanism
        // This validates that the app REMAINS FUNCTIONAL even if model download fails or is pending.
        val parser = SmartSizeParser(
            modelPath = "/data/local/tmp/non_existent_model.onnx",
            tokenizerPath = "/data/local/tmp/non_existent_tokenizer.json"
        )
        
        // 1. Test Standard Format (Label: Key Value)
        val standardInput = """
            Size S: Chest 90cm, Waist 70cm, Hip 96cm
            Size M: Chest 95cm, Waist 75cm, Hip 101cm
        """.trimIndent()
        
        val resultStandard = parser.parseSmart(standardInput)
        assertTrue("Should return Success", resultStandard is ParseResult.Success)
        val entriesStandard = (resultStandard as ParseResult.Success).entries
        assertEquals("Should extract 2 entries", 2, entriesStandard.size)
        
        val sizeS = entriesStandard.find { it.sizeLabel == "S" }
        assertNotNull("Size S should exist", sizeS)
        assertEquals(88.0, sizeS!!.chest!!.min, 0.1) // 90 - 2
        assertEquals(92.0, sizeS.chest!!.max, 0.1)   // 90 + 2
        
        // 2. Test Compact Format (Label Value Value Value)
        val compactInput = """
            L 100 80 106
            XL 105 85 111
        """.trimIndent()
        
        val resultCompact = parser.parseSmart(compactInput)
        assertTrue("Compact input should be parsed", resultCompact is ParseResult.Success)
        val entriesCompact = (resultCompact as ParseResult.Success).entries
        assertEquals("Should extract 2 entries", 2, entriesCompact.size)
        
        val sizeL = entriesCompact.find { it.sizeLabel == "L" }
        assertNotNull("Size L should exist", sizeL)
        // Order assumption: Chest, Waist, Hip
        assertEquals(98.0, sizeL!!.chest!!.min, 0.1) // 100 - 2
        
        // 3. Test Large Size Parsing (3XL)
        val largeSizeInput = """
            Size XS: Chest 86cm, Waist 66cm, Hip 92cm
            Size S: Chest 91cm, Waist 71cm, Hip 97cm
            Size M: Chest 96cm, Waist 76cm, Hip 102cm
            Size L: Chest 101cm, Waist 81cm, Hip 107cm
            Size XL: Chest 106cm, Waist 86cm, Hip 112cm
            Size XXL: Chest 111cm, Waist 91cm, Hip 117cm
            Size 3XL: Chest 116cm, Waist 96cm, Hip 122cm
        """.trimIndent()

        val resultLargeSize = parser.parseSmart(largeSizeInput)
        assertTrue("Large size input should be parsed", resultLargeSize is ParseResult.Success)
        val entriesLargeSize = (resultLargeSize as ParseResult.Success).entries
        assertTrue("Should parse multiple sizes", entriesLargeSize.size >= 3)

        // Check that 3XL is parsed
        val has3XL = entriesLargeSize.any { it.sizeLabel == "3XL" }
        assertTrue("Should parse 3XL size", has3XL)

        // Check that measurements are correct for 3XL
        val size3XL = entriesLargeSize.find { it.sizeLabel == "3XL" }
        assertNotNull("3XL should exist", size3XL)
        assertEquals(114.0, size3XL!!.chest!!.min, 2.0) // Allow some tolerance

        // 4. Test Garbage Input
        val garbageInput = "This is not a size chart just random text."
        val resultGarbage = parser.parseSmart(garbageInput)
        // Depending on implementation, might return Empty Success or Error
        // Current impl returns Error if empty
        assertTrue("Garbage should return Error or Empty",
            resultGarbage is ParseResult.Error || (resultGarbage is ParseResult.Success && resultGarbage.entries.isEmpty()))
    }
}
