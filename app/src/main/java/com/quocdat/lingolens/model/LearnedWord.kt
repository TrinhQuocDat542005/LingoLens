package com.quocdat.lingolens.model

data class LearnedWord(
    val id: String,
    val word: String,
    val translation: String,
    val partOfSpeech: String,
    val definition: String,
    val level: String, // "B1" or "B2"
    val synonyms: List<String> = emptyList(),
    val exampleSentence: String,
    val exampleSentenceB2: String,
    val imagePath: String? = null, // Local URI of the captured image
    val audioPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
