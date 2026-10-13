package com.quocdat.lingolens.integration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.security.MessageDigest

object MigrationChecksums {
    fun assertCommittedSourcesUnchanged() {
        val root=generateSequence(File(System.getProperty("user.dir")).absoluteFile){it.parentFile}
            .first{File(it,"docs/portfolio/week6-migration-checksums.json").isFile}
        val entries=jacksonObjectMapper().readTree(File(root,"docs/portfolio/week6-migration-checksums.json"))
        assertTrue(entries.any{it["path"].asText().endsWith("V13__attempt_confirmation.sql")})
        entries.forEach { entry ->
            val path=entry["path"].asText()
            val bytes=File(root,path).readText(Charsets.UTF_8).replace("\r\n","\n").toByteArray(Charsets.UTF_8)
            val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
            assertEquals(entry["sha256Lf"].asText().lowercase(),hash,path)
        }
    }
}
