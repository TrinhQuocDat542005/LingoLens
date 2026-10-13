package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.assessment.core.LessonPack
import org.springframework.stereotype.Component

/** Pack-only serialization; the global mapper and attempt/sync hashing are unchanged. */
@Component
class PackWireCodec(mapper: ObjectMapper) {
    private val wire = mapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL)

    fun encode(pack: LessonPack): ByteArray = wire.writeValueAsBytes(pack)
}

