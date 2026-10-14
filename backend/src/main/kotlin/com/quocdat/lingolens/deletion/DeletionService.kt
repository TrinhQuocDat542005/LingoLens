package com.quocdat.lingolens.deletion

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64

data class DeletionState(val status:String)

@Service
class DeletionService(private val jdbc:JdbcTemplate,private val passwords:PasswordEncoder) {
    private fun hash(code:String):String? {
        if(!code.matches(Regex("[A-Za-z0-9_-]{43}")))return null
        val bytes=runCatching{Base64.getUrlDecoder().decode(code)}.getOrNull() ?: return null
        if(bytes.size!=32)return null
        return MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    }
    @Transactional(readOnly=true)
    fun status(code:String,now:Instant=Instant.now()):DeletionState {
        val candidate=hash(code) ?: "0".repeat(64)
        val rows=jdbc.queryForList("SELECT code_hash,status,expires_at FROM account_deletion_receipts WHERE code_hash=?",candidate)
        val row=rows.firstOrNull()
        val equal=MessageDigest.isEqual(candidate.toByteArray(Charsets.US_ASCII),(row?.get("code_hash")?.toString() ?: "f".repeat(64)).toByteArray(Charsets.US_ASCII))
        val expiry=when(val at=row?.get("expires_at")){is Timestamp->at.toInstant();is java.time.OffsetDateTime->at.toInstant();else->Instant.MIN}
        return DeletionState(if(equal && hash(code)!=null && expiry.isAfter(now))"DELETED" else "UNKNOWN")
    }
    @Transactional
    fun delete(account:Long,password:String,code:String,now:Instant=Instant.now(),afterDelete:()->Unit={}):DeletionState {
        val digest=hash(code) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Deletion confirmation code must contain 256 random bits")
        // Deletion must exclude concurrent FK inserts; this lock is intentional PostgreSQL behavior.
        val user=jdbc.queryForList("SELECT email,password,name FROM users WHERE id=? FOR UPDATE",account).firstOrNull()
        if(user==null){
            if(status(code,now).status=="DELETED")return DeletionState("DELETED")
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account no longer exists")
        }
        if(!passwords.matches(password,user["password"].toString()))throw ResponseStatusException(HttpStatus.FORBIDDEN,"Password confirmation failed")
        if(jdbc.queryForObject("SELECT count(*) FROM account_deletion_receipts WHERE code_hash=?",Int::class.java,digest)!!>0)
            throw ResponseStatusException(HttpStatus.CONFLICT,"Confirmation code already used; create a new request")
        val email=user["email"].toString()
        jdbc.update("DELETE FROM admin_audit_logs WHERE admin_user_id=? OR LOWER(admin_email)=LOWER(?) OR (target_type='USER' AND target_id=?) OR (target_type='REPORT' AND target_id IN (SELECT CAST(id AS VARCHAR) FROM recognition_reports WHERE user_id=?)) OR POSITION(LOWER(?) IN LOWER(COALESCE(details,'')))>0",account,email,account.toString(),account,email)
        jdbc.update("UPDATE recognition_reports SET resolved_by=NULL WHERE resolved_by=? AND user_id<>?",account,account)
        jdbc.update("DELETE FROM lesson_progress WHERE user_id=?",account)
        jdbc.update("DELETE FROM attempt_answers WHERE attempt_id IN (SELECT id FROM lesson_attempts WHERE user_id=?)",account)
        jdbc.update("DELETE FROM xp_events WHERE user_id=?",account)
        jdbc.update("DELETE FROM lesson_attempts WHERE user_id=?",account)
        for(table in listOf("enrollments","review_events","review_cards","learning_entries","sync_receipts","analytics_events","recognition_reports","recognition_history","refresh_tokens","user_roles","learner_settings"))
            jdbc.update("DELETE FROM $table WHERE user_id=?",account)
        jdbc.update("DELETE FROM users WHERE id=?",account)
        afterDelete()
        jdbc.update("INSERT INTO account_deletion_receipts(code_hash,status,expires_at) VALUES (?,'DELETED',?)",digest,Timestamp.from(now.plusSeconds(7*86400L)))
        return DeletionState("DELETED")
    }
    @Transactional fun prune(now:Instant=Instant.now())=jdbc.update("DELETE FROM account_deletion_receipts WHERE expires_at<=?",Timestamp.from(now))
}
