package com.quocdat.lingolens.service

import com.quocdat.lingolens.model.LearnedWord
import com.quocdat.lingolens.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object FakeWordRepository {
    private val _learnedWords = MutableStateFlow<List<LearnedWord>>(emptyList())
    val learnedWords: StateFlow<List<LearnedWord>> = _learnedWords.asStateFlow()

    private val _currentUser = MutableStateFlow(
        User(
            name = "Quốc Đạt",
            targetLevel = "B1",
            streakDays = 5,
            wordsLearnedCount = 3,
            dailyGoalCount = 5
        )
    )
    val currentUser: StateFlow<User> = _currentUser.asStateFlow()

    init {
        // Pre-populate with some initial mock learned words
        val initialWords = listOf(
            LearnedWord(
                id = UUID.randomUUID().toString(),
                word = "book",
                translation = "quyển sách",
                partOfSpeech = "Noun",
                definition = "A written or printed work consisting of pages glued or sewn together along one side and bound in covers.",
                level = "B1",
                synonyms = listOf("volume", "tome"),
                exampleSentence = "She reads an interesting book in the library every afternoon.",
                exampleSentenceB2 = "This comprehensive volume offers deep insights into classical literary theory.",
                timestamp = System.currentTimeMillis() - 86400000 * 2 // 2 days ago
            ),
            LearnedWord(
                id = UUID.randomUUID().toString(),
                word = "laptop",
                translation = "máy tính xách tay",
                partOfSpeech = "Noun",
                definition = "A computer that is portable and suitable for use while traveling.",
                level = "B1",
                synonyms = listOf("notebook"),
                exampleSentence = "I use my laptop to do my homework and study English online.",
                exampleSentenceB2 = "Modern portable computers have revolutionized remote workspace flexibility globally.",
                timestamp = System.currentTimeMillis() - 86400000 // 1 day ago
            ),
            LearnedWord(
                id = UUID.randomUUID().toString(),
                word = "cup",
                translation = "cái cốc",
                partOfSpeech = "Noun",
                definition = "A small bowl-shaped container for drinking from, typically having a handle.",
                level = "B1",
                synonyms = listOf("mug", "glass"),
                exampleSentence = "He drank a hot cup of coffee to stay awake this morning.",
                exampleSentenceB2 = "The ceramic mug was delicately crafted, showcasing intricate artisanal patterns.",
                timestamp = System.currentTimeMillis() - 3600000 * 4 // 4 hours ago
            )
        )
        _learnedWords.value = initialWords
        _currentUser.value = _currentUser.value.copy(wordsLearnedCount = initialWords.size)
    }

    fun saveWord(word: LearnedWord) {
        val currentList = _learnedWords.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == word.id || it.word.lowercase() == word.word.lowercase() }
        if (existingIndex >= 0) {
            currentList[existingIndex] = word
        } else {
            currentList.add(0, word) // Add to top
        }
        _learnedWords.value = currentList
        _currentUser.value = _currentUser.value.copy(
            wordsLearnedCount = currentList.size
        )
    }

    fun deleteWord(id: String) {
        val currentList = _learnedWords.value.toMutableList()
        currentList.removeAll { it.id == id }
        _learnedWords.value = currentList
        _currentUser.value = _currentUser.value.copy(
            wordsLearnedCount = currentList.size
        )
    }

    fun updateTargetLevel(level: String) {
        _currentUser.value = _currentUser.value.copy(
            targetLevel = level
        )
    }

    fun updateStreak(streak: Int) {
        _currentUser.value = _currentUser.value.copy(
            streakDays = streak
        )
    }
}
