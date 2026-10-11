package com.quocdat.lingolens.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.model.LearnedWord
import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import retrofit2.http.*
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException

data class PublishedVocabulary(val id: Long, val word: String, val translation: String, val phonetic: String?,
    val partOfSpeech: String, val definition: String, val level: String,
    val exampleSentence: String, val exampleSentenceB2: String, val synonyms: List<String>) {
    fun learned() = LearnedWord(id.toString(), word, translation, partOfSpeech, definition, level,
        synonyms, exampleSentence, exampleSentenceB2, phonetic = phonetic)
}
data class VocabularyPage(val content: List<PublishedVocabulary>, val totalPages: Int)
interface VocabularyApi {
    @GET("api/v1/vocabularies") suspend fun list(@Query("page") page: Int, @Query("size") size: Int = 100): ApiEnvelope<VocabularyPage>
    @GET("api/v1/vocabularies/{word}") suspend fun lookup(@Path("word") word: String): ApiEnvelope<PublishedVocabulary>
}
data class VocabularyLookup(val word: LearnedWord?, val message: String? = null)

class VocabularyRepository(private val api: VocabularyApi, private val db: LearningDatabase,
                           private val learning: LearningRepository, private val gson: Gson = Gson()) {
    private val dao = db.learningDao()
    suspend fun refreshCatalog() {
        val entries = mutableListOf<VocabularyCache>()
        var page = 0
        do {
            val result = requireNotNull(api.list(page).data)
            entries += result.content.map { VocabularyCache(normalizeWord(it.word), gson.toJson(it.learned()), System.currentTimeMillis()) }
            page++
        } while (page < result.totalPages)
        db.withTransaction { dao.clearCache(); dao.cache(entries) }
    }
    suspend fun lookup(accountId: Long, word: String, refresh: Boolean = false): VocabularyLookup {
        val key = normalizeWord(word)
        if (!refresh) {
            val saved = learning.find(accountId, key)
            val cached = dao.cached(key)
            if (saved != null) return VocabularyLookup(saved, "Bản lưu cá nhân trên thiết bị. Làm mới để kiểm tra nội dung máy chủ.")
            if (cached != null) return VocabularyLookup(gson.fromJson(cached.content, LearnedWord::class.java), "Nội dung cache trên thiết bị. Làm mới để kiểm tra bản mới nhất.")
        }
        try {
            val entry = requireNotNull(api.lookup(key).data).learned()
            dao.cache(listOf(VocabularyCache(key, gson.toJson(entry), System.currentTimeMillis())))
            return VocabularyLookup(entry)
        } catch (ex: CancellationException) { throw ex
        } catch (ex: Exception) {
            if (ex is HttpException && ex.code() == 404) {
                dao.evict(key)
                return VocabularyLookup(learning.find(accountId, key), "Từ chưa được xuất bản. Nếu đã lưu, bạn đang xem bản lưu cá nhân.")
            }
            // Authentication failures must not silently fall back to shared cached content.
            if (ex is HttpException && ex.code() in 400..499 && ex.code() !in listOf(408, 429)) throw ex
            val saved = learning.find(accountId, key)
            val cached = dao.cached(key)?.let { gson.fromJson(it.content, LearnedWord::class.java) }
            return VocabularyLookup(saved ?: cached, if (saved != null || cached != null) "Đang dùng nội dung đã lưu trên thiết bị; máy chủ chưa kết nối." else "Không tải được nội dung. Kết nối máy chủ rồi thử lại.")
        }
    }
}
