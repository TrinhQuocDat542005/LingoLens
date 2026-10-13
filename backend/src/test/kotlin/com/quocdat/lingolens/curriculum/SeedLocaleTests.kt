package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Pure seed validation; PostgreSQL separately checks the locale on the emitted lesson pack. */
class SeedLocaleTests {
    private val mapper=jacksonObjectMapper()
    private fun seed()=mapper.readTree(requireNotNull(javaClass.getResourceAsStream("/content/demo-week5.json"))) as ObjectNode

    @Test fun `en US seed is accepted with its original locale`() {
        val root=seed();assertEquals("en-US",root["pronunciationLocale"].asText())
        assertDoesNotThrow{SeedValidator.validate(root)}
        assertEquals("en-US",root["pronunciationLocale"].asText())
    }
    @Test fun `unsupported seed locales are rejected without silently converting to en US`() {
        for(locale in listOf("en-GB","fr-FR","en","")){
            val root=seed().put("pronunciationLocale",locale)
            assertThrows(IllegalArgumentException::class.java){SeedValidator.validate(root)}
            assertEquals(locale,root["pronunciationLocale"].asText())
        }
    }
}
