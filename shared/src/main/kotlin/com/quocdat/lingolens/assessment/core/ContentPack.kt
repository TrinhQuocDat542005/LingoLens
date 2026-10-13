package com.quocdat.lingolens.assessment.core

data class PackManifest(val schemaVersion: Int, val courseId: String, val lessons: List<PackReference>)
data class PackReference(val lessonId: String, val revisionId: String, val title: String, val position: Int,
    val contentStatus: String, val downloadUrl: String, val byteLength: Long, val checksum: String)
data class PackedWord(val word: String, val translation: String, val phonetic: String, val partOfSpeech: String,
    val definition: String, val example: String, val level: String)
data class PackedPattern(val pattern: String, val example: String, val translation: String)
data class PackedOption(val key: String, val label: String, val position: Int)
// optionKey stays a String for source/wire compatibility. Fill packs carry "" and approved variants.
// Nullable additions also preserve absent fields when Gson reads an older cached pack.
data class PackedAnswerKey(val optionKey: String, val acceptedAnswers: List<String>? = null)
data class PackedQuestion(val id: String, val type: String, val prompt: String, val explanation: String,
    val position: Int, val weight: Int, val options: List<PackedOption>, val answerKey: PackedAnswerKey,
    val speechText: String? = null, val fallbackPrompt: String? = null)
data class LessonPack(val schemaVersion: Int, val courseId: String, val lessonId: String, val revisionId: String,
    val title: String, val introduction: String, val contentStatus: String, val pronunciationLocale: String,
    val position: Int, val sentencePatterns: List<PackedPattern>, val vocabularies: List<PackedWord>, val questions: List<PackedQuestion>)

