package com.quocdat.lingolens.recognition

object SupportedVocabulary {
    val words: List<String> = listOf(
        "apple", "backpack", "banana", "bicycle", "bird", "book", "bottle", "bread",
        "bowl", "cake", "car", "cat", "chair", "clock", "computer", "couch", "cup", "dog", "flower", "food",
        "keyboard", "laptop", "mouse", "orange", "pen", "phone", "plant", "shoe",
        "table", "television", "tree", "watch", "bed"
    )

    private val aliases: Map<String, String> = mapOf(
        "apple" to "apple",
        "backpack" to "backpack", "bag" to "backpack",
        "banana" to "banana",
        "bicycle" to "bicycle", "bike" to "bicycle", "cycling" to "bicycle",
        "bird" to "bird",
        "book" to "book", "publication" to "book",
        "bottle" to "bottle",
        "bowl" to "bowl",
        "bread" to "bread",
        "cake" to "cake", "dessert" to "cake",
        "car" to "car", "vehicle" to "car",
        "cat" to "cat",
        "chair" to "chair",
        "couch" to "couch", "sofa" to "couch",
        "clock" to "clock",
        "cup" to "cup", "mug" to "cup", "drinkware" to "cup",
        "dog" to "dog",
        "flower" to "flower",
        "food" to "food", "dish" to "food", "meal" to "food",
        "keyboard" to "keyboard", "computer keyboard" to "keyboard",
        "computer" to "computer", "personal computer" to "computer", "desktop computer" to "computer",
        "laptop" to "laptop", "notebook computer" to "laptop",
        "mouse" to "mouse", "computer mouse" to "mouse",
        "orange" to "orange", "citrus" to "orange",
        "pen" to "pen", "stationery" to "pen",
        "phone" to "phone", "mobile phone" to "phone", "smartphone" to "phone",
        "plant" to "plant", "houseplant" to "plant",
        "shoe" to "shoe", "footwear" to "shoe",
        "table" to "table", "desk" to "table",
        "dining table" to "table",
        "television" to "television", "tv" to "television", "screen" to "television",
        "tree" to "tree",
        "watch" to "watch", "wristwatch" to "watch",
        "bed" to "bed",
        "cell phone" to "phone"
    )

    fun canonicalWord(label: String): String? {
        val normalized = label.trim().lowercase()
        return aliases[normalized] ?: aliases.entries
            .firstOrNull { (alias, _) -> normalized.contains(alias) }
            ?.value
    }
}
