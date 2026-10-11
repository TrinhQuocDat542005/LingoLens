package com.quocdat.lingolens.vocabulary

import com.quocdat.lingolens.admin.VocabularyRepository
import com.quocdat.lingolens.common.ApiResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/vocabularies")
@Transactional(readOnly = true)
class VocabularyController(private val repository: VocabularyRepository) {
    @GetMapping
    fun list(@RequestParam(defaultValue = "") search: String,
             @RequestParam(defaultValue = "0") page: Int,
             @RequestParam(defaultValue = "50") size: Int) = ApiResponse.success(
        "Published vocabulary loaded",
        repository.search(search.trim(), true, PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by("word")))
            .let { VocabularyPage(it.content.map(::response), it.totalPages) }
    )

    @GetMapping("/{word}")
    fun lookup(@PathVariable word: String) = ApiResponse.success("Vocabulary loaded",
        response(repository.findByWordIgnoreCaseAndPublishedTrue(word.trim())
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Published vocabulary not found") }))

    private fun response(v: com.quocdat.lingolens.admin.Vocabulary) = PublishedVocabulary(
        v.id!!, v.word, v.translation, v.phonetic, v.partOfSpeech, v.definition, v.level,
        v.exampleSentence.orEmpty(), v.exampleSentenceB2.orEmpty(),
        v.synonyms.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() })
}

data class VocabularyPage(val content: List<PublishedVocabulary>, val totalPages: Int)
data class PublishedVocabulary(val id: Long, val word: String, val translation: String,
    val phonetic: String?, val partOfSpeech: String, val definition: String, val level: String,
    val exampleSentence: String, val exampleSentenceB2: String, val synonyms: List<String>)
