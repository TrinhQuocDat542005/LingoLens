package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Draft, written before pipeline changes; integrate into backend/src/test after shared step passes. */
class SeedQuestionTypesTests {
    private val mapper=jacksonObjectMapper()
    private fun legacy()=mapper.readTree(requireNotNull(javaClass.getResourceAsStream("/content/demo-week5.json"))) as ObjectNode
    private fun mixed()=mapper.readTree(requireNotNull(javaClass.getResourceAsStream("/content/demo-week7.json"))) as ObjectNode
    @Test fun `legacy eight choice seed remains valid and unmodified`() {
        val seed=legacy();val before=seed.deepCopy()
        SeedValidator.validate(seed);assertEquals(before,seed)
        assertTrue(seed["lessons"].all{it["questions"].size()==8})
    }
    @Test fun `three lesson mixed seed validates ten equal weight questions each`() {
        val seed=mixed();SeedValidator.validate(seed)
        seed["lessons"].forEach { lesson ->
            assertEquals(10,lesson["questions"].size())
            assertEquals(setOf("MEANING_CHOICE","LISTEN_CHOICE","FILL_GAP"),lesson["questions"].map{it["type"].asText()}.toSet())
            assertEquals(2,lesson["questions"].count{it["type"].asText()=="LISTEN_CHOICE"})
            assertTrue(lesson["questions"].all{it["weight"].asInt()==1})
        }
    }
    @Test fun `missing blank or misplaced listening fallback and speech are rejected`() {
        val mutations:List<(ObjectNode)->Unit> = listOf(
            { (it["answerKey"] as ObjectNode).remove("fallbackPrompt") },
            { (it["answerKey"] as ObjectNode).put("fallbackPrompt","  ") },
            { it.remove("speechText") },
            { it.put("speechText","") },
            { it.put("fallbackPrompt","misplaced") }
        )
        mutations.forEach { change ->val root=mixed();val q=root["lessons"][0]["questions"].first{it["type"].asText()=="LISTEN_CHOICE"} as ObjectNode;change(q)
            assertThrows(IllegalArgumentException::class.java){SeedValidator.validate(root)} }
    }
    @Test fun `fill variants are explicit nonempty normalized distinct and options empty`() {
        val mutations:List<(ObjectNode)->Unit> = listOf(
            { (it["answerKey"] as ObjectNode).putArray("acceptedAnswers") },
            { (it["answerKey"] as ObjectNode).putArray("acceptedAnswers").add(" ") },
            { (it["answerKey"] as ObjectNode).putArray("acceptedAnswers").add("hello").add(" HELLO ") },
            { (it["answerKey"] as ObjectNode).put("optionKey","A") },
            { it.putArray("options").addObject().put("key","A").put("label","wrong").put("position",1) }
        )
        mutations.forEach { change ->val root=mixed();val q=root["lessons"][0]["questions"].first{it["type"].asText()=="FILL_GAP"} as ObjectNode;change(q)
            assertThrows(IllegalArgumentException::class.java){SeedValidator.validate(root)} }
    }
    @Test fun `mixed lesson limits listening count question count and unequal weights`() {
        val extraListen=mixed();val qs=extraListen["lessons"][0]["questions"]
        val template=qs.first{it["type"].asText()=="LISTEN_CHOICE"}
        val old=qs[0] as ObjectNode;old.put("type","LISTEN_CHOICE");old.put("speechText",template["speechText"].asText())
        old.set<ObjectNode>("answerKey",template["answerKey"].deepCopy())
        assertThrows(IllegalArgumentException::class.java){SeedValidator.validate(extraListen)}
        val missing=mixed();(missing["lessons"][0]["questions"] as com.fasterxml.jackson.databind.node.ArrayNode).remove(0)
        assertThrows(IllegalArgumentException::class.java){SeedValidator.validate(missing)}
        val weighted=mixed();(weighted["lessons"][0]["questions"][0] as ObjectNode).put("weight",2)
        assertThrows(IllegalArgumentException::class.java){SeedValidator.validate(weighted)}
    }
}
