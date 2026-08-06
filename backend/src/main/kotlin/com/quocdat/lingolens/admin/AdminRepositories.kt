package com.quocdat.lingolens.admin

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional
import com.quocdat.lingolens.user.User

interface VocabularyRepository : JpaRepository<Vocabulary, Long> {
    fun existsByWordIgnoreCase(word: String): Boolean
    fun findByWordIgnoreCase(word: String): Optional<Vocabulary>
    fun countByPublishedTrue(): Long

    @Query("""select v from Vocabulary v where
        (:search = '' or lower(v.word) like lower(concat('%', :search, '%')) or lower(v.translation) like lower(concat('%', :search, '%')))
        and (:published is null or v.published = :published)""")
    fun search(@Param("search") search: String, @Param("published") published: Boolean?, pageable: Pageable): Page<Vocabulary>
}

interface RecognitionHistoryRepository : JpaRepository<RecognitionHistory, Long> {
    fun countByCreatedAtAfter(since: Instant): Long
    fun findByIdAndUser(id: Long, user: User): Optional<RecognitionHistory>
    fun findAllByUser(user: User, pageable: Pageable): Page<RecognitionHistory>
}

interface RecognitionReportRepository : JpaRepository<RecognitionReport, Long> {
    fun countByResolvedFalse(): Long
    fun existsByHistory(history: RecognitionHistory): Boolean
    @Query("select r from RecognitionReport r where (:resolved is null or r.resolved = :resolved)")
    fun search(@Param("resolved") resolved: Boolean?, pageable: Pageable): Page<RecognitionReport>
}

interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, Long> {
    fun findTop20ByOrderByCreatedAtDesc(): List<AdminAuditLog>
}
