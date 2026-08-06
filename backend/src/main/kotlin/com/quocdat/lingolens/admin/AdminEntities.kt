package com.quocdat.lingolens.admin

import com.quocdat.lingolens.user.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "vocabularies")
class Vocabulary(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, unique = true) var word: String,
    @Column(nullable = false) var translation: String,
    var phonetic: String? = null,
    @Column(name = "part_of_speech", nullable = false) var partOfSpeech: String = "Noun",
    @Column(nullable = false, columnDefinition = "TEXT") var definition: String,
    @Column(name = "example_sentence", columnDefinition = "TEXT") var exampleSentence: String? = null,
    @Column(name = "example_sentence_b2", columnDefinition = "TEXT") var exampleSentenceB2: String? = null,
    @Column(columnDefinition = "TEXT") var synonyms: String? = null,
    @Column(nullable = false) var level: String = "B1",
    @Column(name = "is_published", nullable = false) var published: Boolean = true,
    @Column(name = "created_at", nullable = false, updatable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "recognition_history")
class RecognitionHistory(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) var user: User,
    @Column(name = "detected_label", nullable = false) var detectedLabel: String,
    @Column(nullable = false) var confidence: Float,
    var engine: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "recognition_reports")
class RecognitionReport(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) var user: User,
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recognition_history_id", unique = true) var history: RecognitionHistory? = null,
    @Column(name = "expected_label", nullable = false) var expectedLabel: String,
    @Column(name = "actual_label", nullable = false) var actualLabel: String,
    var confidence: Float? = null,
    @Column(columnDefinition = "TEXT") var note: String? = null,
    @Column(name = "is_resolved", nullable = false) var resolved: Boolean = false,
    @Column(name = "resolved_at") var resolvedAt: Instant? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resolved_by") var resolvedBy: User? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "admin_audit_logs")
class AdminAuditLog(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "admin_user_id") var adminUser: User? = null,
    @Column(name = "admin_email", nullable = false) var adminEmail: String,
    @Column(nullable = false) var action: String,
    @Column(name = "target_type", nullable = false) var targetType: String,
    @Column(name = "target_id") var targetId: String? = null,
    @Column(columnDefinition = "TEXT") var details: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
