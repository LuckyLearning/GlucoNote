package com.gluconote.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseInputTest {

    @Test
    fun append_firstZero_isSingleZero() {
        assertEquals("0", GlucoseInput.append("", "0"))
    }

    @Test
    fun append_leadingZero_isReplaced() {
        assertEquals("5", GlucoseInput.append("0", "5"))
        assertEquals("0", GlucoseInput.append("0", "0"))
    }

    @Test
    fun append_dotOnEmpty_becomesZeroDot() {
        assertEquals("0.", GlucoseInput.append("", "."))
        assertEquals("6.", GlucoseInput.append("6", "."))
    }

    @Test
    fun append_secondDot_isRejected() {
        assertNull(GlucoseInput.append("6.2", "."))
    }

    @Test
    fun append_onlyOneDecimal_isAccepted() {
        assertEquals("6.2", GlucoseInput.append("6.", "2"))
        assertNull(GlucoseInput.append("6.2", "3"))
    }

    @Test
    fun append_intPart_isCappedAtThreeDigits() {
        assertEquals("123", GlucoseInput.append("12", "3"))
        assertNull(GlucoseInput.append("123", "4"))
    }

    @Test
    fun append_rejectsNonDigit() {
        assertNull(GlucoseInput.append("6", "时"))
    }

    @Test
    fun backspace_handlesEmptyAndDot() {
        assertEquals("", GlucoseInput.backspace(""))
        assertEquals("6", GlucoseInput.backspace("6."))
        assertEquals("6.", GlucoseInput.backspace("6.2"))
    }

    @Test
    fun parse_rejectsEmptyZeroAndDotOnly() {
        assertNull(GlucoseInput.parse(""))
        assertNull(GlucoseInput.parse("0"))
        assertNull(GlucoseInput.parse("0."))
        assertEquals(6f, GlucoseInput.parse("6.")!!, 0.001f)
        assertEquals(6.2f, GlucoseInput.parse("6.2")!!, 0.001f)
    }

    @Test
    fun plausible_boundsAreInclusive() {
        assertFalse(GlucoseInput.isPlausible(0.9f))
        assertTrue(GlucoseInput.isPlausible(1.0f))
        assertTrue(GlucoseInput.isPlausible(50.0f))
        assertFalse(GlucoseInput.isPlausible(50.1f))
    }
}
