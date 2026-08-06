package com.quocdat.lingolens.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedVocabularyTest {
    @Test
    fun `exact ML label maps to supported word`() {
        assertEquals("cat", SupportedVocabulary.canonicalWord("Cat"))
        assertEquals("keyboard", SupportedVocabulary.canonicalWord("Computer keyboard"))
    }

    @Test
    fun `common aliases map to canonical learning word`() {
        assertEquals("phone", SupportedVocabulary.canonicalWord("Mobile phone"))
        assertEquals("cup", SupportedVocabulary.canonicalWord("Mug"))
        assertEquals("computer", SupportedVocabulary.canonicalWord("Personal computer"))
        assertEquals("phone", SupportedVocabulary.canonicalWord("cell phone"))
        assertEquals("table", SupportedVocabulary.canonicalWord("dining table"))
    }

    @Test
    fun `unsupported label is not guessed`() {
        assertNull(SupportedVocabulary.canonicalWord("Mountain range"))
    }

    @Test
    fun `low confidence result requires confirmation`() {
        val result = RecognitionResult(
            candidates = listOf(RecognitionCandidate("dog", "Dog", 0.54f)),
            rawLabels = emptyList()
        )
        assertTrue(result.needsConfirmation)
    }

    @Test
    fun `high confidence result can be accepted automatically`() {
        val result = RecognitionResult(
            candidates = listOf(RecognitionCandidate("dog", "Dog", 0.88f)),
            rawLabels = emptyList()
        )
        assertFalse(result.needsConfirmation)
    }
}
