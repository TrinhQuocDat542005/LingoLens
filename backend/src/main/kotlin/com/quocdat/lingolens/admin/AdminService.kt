package com.quocdat.lingolens.admin

import com.quocdat.lingolens.common.UserNotFoundException
import com.quocdat.lingolens.security.RefreshTokenRepository
import com.quocdat.lingolens.security.RoleRepository
import com.quocdat.lingolens.user.User
import com.quocdat.lingolens.user.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

@Service
class AdminService(
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val vocabularies: VocabularyRepository,
    private val histories: RecognitionHistoryRepository,
    private val reports: RecognitionReportRepository,
    private val audits: AdminAuditLogRepository
) {
    private val allowedRoles = setOf("ROLE_USER", "ROLE_ADMIN")

    @Transactional(readOnly = true)
    fun dashboard(): AdminDashboardResponse {
        val today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate()
        val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val counts = users.findAll().groupingBy { it.createdAt.atZone(ZoneOffset.UTC).toLocalDate() }.eachCount()
        val weekAgo = today.minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant()
        return AdminDashboardResponse(
            totalUsers = users.count(), activeUsers = users.countByEnabledTrue(), disabledUsers = users.countByEnabledFalse(),
            adminUsers = users.countByRole("ROLE_ADMIN"), newUsersLast7Days = users.countByCreatedAtAfter(weekAgo),
            totalVocabularies = vocabularies.count(), publishedVocabularies = vocabularies.countByPublishedTrue(),
            totalScans = histories.count(), scansLast7Days = histories.countByCreatedAtAfter(weekAgo),
            pendingReports = reports.countByResolvedFalse(),
            userGrowth = dates.map { DailyMetric(it.toString(), counts[it]?.toLong() ?: 0) },
            recentActivity = audits.findTop20ByOrderByCreatedAtDesc().map(::toAudit)
        )
    }

    @Transactional(readOnly = true)
    fun listUsers(search: String, enabled: Boolean?, role: String?, page: Int, size: Int): PageResponse<AdminUserResponse> {
        val result = users.searchAdminUsers(search.trim(), enabled, role.orEmpty(), PageRequest.of(page, size.coerceIn(1, 100), Sort.by("createdAt").descending()))
        return PageResponse(result.content.map(::toUser), result.number, result.size, result.totalElements, result.totalPages)
    }

    @Transactional
    fun updateStatus(id: Long, request: UpdateUserStatusRequest, actorEmail: String): AdminUserResponse {
        val user = requireUser(id)
        require(!(user.getEmail() == actorEmail && !request.enabled)) { "Bạn không thể tự khóa tài khoản đang đăng nhập" }
        user.enabled = request.enabled
        user.updatedAt = Instant.now()
        if (!request.enabled) refreshTokens.findAllByUserAndRevokedAtIsNull(user).forEach { it.revokedAt = Instant.now() }
        audit(actorEmail, "UPDATE_STATUS", "USER", id.toString(), "enabled=${request.enabled}")
        return toUser(user)
    }

    @Transactional
    fun updateRoles(id: Long, request: UpdateUserRolesRequest, actorEmail: String): AdminUserResponse {
        val requested = request.roles.map { it.uppercase() }.toSet()
        require(requested.isNotEmpty() && requested.all { it in allowedRoles }) { "Vai trò chỉ được phép là ROLE_USER hoặc ROLE_ADMIN" }
        val user = requireUser(id)
        require(!(user.getEmail() == actorEmail && "ROLE_ADMIN" !in requested)) { "Bạn không thể tự gỡ quyền ADMIN" }
        user.roles = requested.map { name -> roles.findByName(name).orElseThrow { IllegalArgumentException("Role $name chưa được khởi tạo") } }.toMutableSet()
        user.updatedAt = Instant.now()
        audit(actorEmail, "UPDATE_ROLES", "USER", id.toString(), requested.sorted().joinToString())
        return toUser(user)
    }

    @Transactional(readOnly = true)
    fun listVocabularies(search: String, published: Boolean?, page: Int, size: Int): PageResponse<VocabularyResponse> {
        val result = vocabularies.search(search.trim(), published, PageRequest.of(page, size.coerceIn(1, 100), Sort.by("updatedAt").descending()))
        return PageResponse(result.content.map(::toVocabulary), result.number, result.size, result.totalElements, result.totalPages)
    }

    @Transactional
    fun createVocabulary(request: VocabularyRequest, actorEmail: String): VocabularyResponse {
        require(!vocabularies.existsByWordIgnoreCase(request.word.trim())) { "Từ vựng đã tồn tại" }
        val saved = vocabularies.save(Vocabulary(word = request.word.trim(), translation = request.translation.trim(), phonetic = request.phonetic?.trim(), partOfSpeech = request.partOfSpeech.trim(), definition = request.definition.trim(), exampleSentence = request.exampleSentence?.trim(), exampleSentenceB2 = request.exampleSentenceB2?.trim(), synonyms = request.synonyms?.trim(), level = request.level, published = request.published))
        audit(actorEmail, "CREATE", "VOCABULARY", saved.id.toString(), saved.word)
        return toVocabulary(saved)
    }

    @Transactional
    fun updateVocabulary(id: Long, request: VocabularyRequest, actorEmail: String): VocabularyResponse {
        val item = vocabularies.findById(id).orElseThrow { IllegalArgumentException("Không tìm thấy từ vựng") }
        vocabularies.findByWordIgnoreCase(request.word.trim()).filter { it.id != id }.ifPresent { throw IllegalArgumentException("Từ vựng đã tồn tại") }
        item.word = request.word.trim(); item.translation = request.translation.trim(); item.phonetic = request.phonetic?.trim()
        item.partOfSpeech = request.partOfSpeech.trim(); item.definition = request.definition.trim(); item.exampleSentence = request.exampleSentence?.trim()
        item.exampleSentenceB2 = request.exampleSentenceB2?.trim(); item.synonyms = request.synonyms?.trim(); item.level = request.level
        item.published = request.published; item.updatedAt = Instant.now()
        audit(actorEmail, "UPDATE", "VOCABULARY", id.toString(), item.word)
        return toVocabulary(item)
    }

    @Transactional
    fun deleteVocabulary(id: Long, actorEmail: String) {
        val item = vocabularies.findById(id).orElseThrow { IllegalArgumentException("Không tìm thấy từ vựng") }
        vocabularies.delete(item)
        audit(actorEmail, "DELETE", "VOCABULARY", id.toString(), item.word)
    }

    @Transactional(readOnly = true)
    fun listReports(resolved: Boolean?, page: Int, size: Int): PageResponse<RecognitionReportResponse> {
        val result = reports.search(resolved, PageRequest.of(page, size.coerceIn(1, 100), Sort.by("createdAt").descending()))
        return PageResponse(result.content.map(::toReport), result.number, result.size, result.totalElements, result.totalPages)
    }

    @Transactional
    fun resolveReport(id: Long, request: ResolveReportRequest, actorEmail: String): RecognitionReportResponse {
        val report = reports.findById(id).orElseThrow { IllegalArgumentException("Không tìm thấy báo cáo") }
        report.resolved = request.resolved
        report.resolvedAt = if (request.resolved) Instant.now() else null
        report.resolvedBy = if (request.resolved) users.findByEmail(actorEmail).orElse(null) else null
        audit(actorEmail, if (request.resolved) "RESOLVE" else "REOPEN", "REPORT", id.toString(), null)
        return toReport(report)
    }

    @Transactional(readOnly = true)
    fun auditLogs() = audits.findTop20ByOrderByCreatedAtDesc().map(::toAudit)

    private fun requireUser(id: Long) = users.findById(id).orElseThrow { UserNotFoundException("Không tìm thấy người dùng") }
    private fun actor(email: String) = users.findByEmail(email).orElse(null)
    private fun audit(email: String, action: String, type: String, id: String?, details: String?) = audits.save(AdminAuditLog(adminUser = actor(email), adminEmail = email, action = action, targetType = type, targetId = id, details = details))
    private fun toUser(u: User) = AdminUserResponse(u.id!!, u.getEmail(), u.getName(), u.targetLevel, u.dailyGoal, u.streakDays, u.enabled, u.roles.map { it.name }.sorted(), u.createdAt, u.updatedAt)
    private fun toVocabulary(v: Vocabulary) = VocabularyResponse(v.id!!, v.word, v.translation, v.phonetic, v.partOfSpeech, v.definition, v.exampleSentence, v.exampleSentenceB2, v.synonyms, v.level, v.published, v.createdAt, v.updatedAt)
    private fun toReport(r: RecognitionReport) = RecognitionReportResponse(r.id!!, r.user.id!!, r.user.getEmail(), r.expectedLabel, r.actualLabel, r.confidence, r.note, r.resolved, r.resolvedAt, r.resolvedBy?.getEmail(), r.createdAt)
    private fun toAudit(a: AdminAuditLog) = AuditLogResponse(a.id!!, a.adminEmail, a.action, a.targetType, a.targetId, a.details, a.createdAt)
}
