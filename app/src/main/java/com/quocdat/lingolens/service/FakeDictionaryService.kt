package com.quocdat.lingolens.service

import com.quocdat.lingolens.model.LearnedWord
import java.util.UUID

object FakeDictionaryService {
    private val basicTranslations = mapOf(
        "apple" to "quả táo", "backpack" to "ba lô", "banana" to "quả chuối",
        "bicycle" to "xe đạp", "bird" to "con chim", "bottle" to "cái chai",
        "bread" to "bánh mì", "cake" to "bánh ngọt", "car" to "ô tô",
        "chair" to "cái ghế", "clock" to "đồng hồ", "computer" to "máy tính",
        "flower" to "bông hoa", "food" to "thức ăn", "keyboard" to "bàn phím",
        "mouse" to "chuột máy tính", "orange" to "quả cam", "pen" to "bút",
        "phone" to "điện thoại", "plant" to "cây cảnh", "shoe" to "giày",
        "table" to "cái bàn", "television" to "ti vi", "tree" to "cây",
        "watch" to "đồng hồ đeo tay"
    )
    private val fakeDatabase = mapOf(
        "cat" to LearnedWord(
            id = "",
            word = "cat",
            translation = "con mèo",
            partOfSpeech = "Noun",
            definition = "A small domesticated carnivorous mammal with soft fur, a short snout, and retractile claws.",
            level = "B1",
            synonyms = listOf("feline", "kitten", "kitty"),
            exampleSentence = "The black cat is sleeping soundly on the comfortable sofa.",
            exampleSentenceB2 = "Feline behavior can be highly unpredictable when introduced to unfamiliar surroundings.",
            imagePath = null
        ),
        "dog" to LearnedWord(
            id = "",
            word = "dog",
            translation = "con chó",
            partOfSpeech = "Noun",
            definition = "A domesticated carnivorous mammal that typically has a long snout, an acute sense of smell, and a barking voice.",
            level = "B1",
            synonyms = listOf("canine", "pup", "puppy"),
            exampleSentence = "My loyal dog always barks happily when I return home from work.",
            exampleSentenceB2 = "Canine companionship has been proven to significantly reduce stress levels in human owners.",
            imagePath = null
        ),
        "book" to LearnedWord(
            id = "",
            word = "book",
            translation = "quyển sách",
            partOfSpeech = "Noun",
            definition = "A written or printed work consisting of pages glued or sewn together along one side and bound in covers.",
            level = "B1",
            synonyms = listOf("volume", "tome", "novel"),
            exampleSentence = "She reads an interesting book in the library every afternoon.",
            exampleSentenceB2 = "This comprehensive volume offers deep insights into classical literary theory.",
            imagePath = null
        ),
        "laptop" to LearnedWord(
            id = "",
            word = "laptop",
            translation = "máy tính xách tay",
            partOfSpeech = "Noun",
            definition = "A computer that is portable and suitable for use while traveling.",
            level = "B1",
            synonyms = listOf("notebook", "portable computer"),
            exampleSentence = "I use my laptop to do my homework and study English online.",
            exampleSentenceB2 = "Modern portable computers have revolutionized remote workspace flexibility globally.",
            imagePath = null
        ),
        "cup" to LearnedWord(
            id = "",
            word = "cup",
            translation = "cái cốc",
            partOfSpeech = "Noun",
            definition = "A small bowl-shaped container for drinking from, typically having a handle.",
            level = "B1",
            synonyms = listOf("mug", "glass", "tumbler"),
            exampleSentence = "He drank a hot cup of coffee to stay awake this morning.",
            exampleSentenceB2 = "The ceramic mug was delicately crafted, showcasing intricate artisanal patterns.",
            imagePath = null
        )
    )

    fun lookupWord(word: String): LearnedWord {
        val normalized = word.lowercase().trim()
        val template = fakeDatabase[normalized] ?: LearnedWord(
            id = "",
            word = word,
            translation = basicTranslations[normalized] ?: "chưa có bản dịch",
            partOfSpeech = "Noun",
            definition = "An everyday object recognized by LingoLens. A full dictionary definition will be loaded in week 5.",
            level = "B1",
            synonyms = emptyList(),
            exampleSentence = "I can see a $word in the picture.",
            exampleSentenceB2 = "The $word was identified from the captured image with on-device machine learning.",
            imagePath = null
        )
        return template.copy(id = UUID.randomUUID().toString(), timestamp = System.currentTimeMillis())
    }
}
