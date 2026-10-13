package com.quocdat.lingolens.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.security.JwtService
import com.quocdat.lingolens.user.UserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import java.util.UUID

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class IdentityBoundAuthTests {
    @Autowired lateinit var mvc:MockMvc
    @Autowired lateinit var mapper:ObjectMapper
    @Autowired lateinit var users:UserRepository
    @Autowired lateinit var jwt:JwtService
    @Value("\${security.jwt.secret-key}") lateinit var secret:String
    private fun account():Pair<Long,JsonNode> {
        val email="identity-${UUID.randomUUID()}@example.invalid"
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RegisterRequest(email,"Secure123!","Identity"))))
            .andExpect(status().isCreated)
        val tokens=mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(LoginRequest(email,"Secure123!"))))
            .andExpect(status().isOk).andReturn().response.contentAsString)["data"]
        return users.findByEmail(email).orElseThrow().id!! to tokens
    }
    private fun signed(email:String,claims:Map<String,Any> = emptyMap(),expired:Boolean=false):String =
        Jwts.builder().claims(claims).subject(email).issuedAt(Date(System.currentTimeMillis()-2000))
            .expiration(Date(System.currentTimeMillis()+if(expired)-1000 else 60000))
            .signWith(Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))).compact()
    private fun boundaries(id:Long)=listOf("/api/v1/users/me","/api/v1/curriculum","/api/v1/recognitions",
        "/api/v1/sync/learning?accountId=$id","/api/v1/sync/reviews?accountId=$id","/api/v1/admin/hello")

    @Test fun `login and refresh bind same stable ID without changing response contract`() {
        val(id,tokens)=account()
        assertEquals(setOf("accessToken","refreshToken","tokenType","expiresIn","email","name","roles"),tokens.fieldNames().asSequence().toSet())
        assertEquals(id,jwt.extractClaim(tokens["accessToken"].asText()){(it["userId"] as Number).toLong()})
        val email=tokens["email"].asText()
        assertEquals(email,jwt.extractUsername(tokens["accessToken"].asText()))
        val refreshed=mapper.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RefreshTokenRequest(tokens["refreshToken"].asText()))))
            .andExpect(status().isOk).andReturn().response.contentAsString)["data"]
        assertEquals(id,jwt.extractClaim(refreshed["accessToken"].asText()){(it["userId"] as Number).toLong()})
        assertEquals(tokens.fieldNames().asSequence().toSet(),refreshed.fieldNames().asSequence().toSet())
        assertNotEquals(tokens["refreshToken"],refreshed["refreshToken"])
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RefreshTokenRequest(tokens["refreshToken"].asText()))))
            .andExpect(status().isUnauthorized)
        boundaries(id).dropLast(1).forEach{path->mvc.perform(get(path).header("Authorization","Bearer ${refreshed["accessToken"].asText()}"))
            .andExpect(status().isOk)}
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer ${refreshed["accessToken"].asText()}")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(LogoutRequest(refreshed["refreshToken"].asText()))))
            .andExpect(status().isOk)
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RefreshTokenRequest(refreshed["refreshToken"].asText()))))
            .andExpect(status().isUnauthorized)
    }

    @Test fun `legacy missing malformed and wrong identity claims cannot cross any protected boundary`() {
        val(id,tokens)=account();val email=tokens["email"].asText()
        val invalid=listOf(signed(email))+listOf<Any>(0L,-1L,Long.MAX_VALUE,id.toString(),id.toDouble(),true,listOf(id))
            .map{signed(email,mapOf("userId" to it))}+signed("other@example.invalid",mapOf("userId" to id))
        invalid.forEach{token->boundaries(id).forEach{path->mvc.perform(get(path).header("Authorization","Bearer $token"))
            .andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("INVALID_TOKEN"))}}
    }

    @Test fun `expired and tampered tokens stay rejected and claimed roles never grant authority`() {
        val(id,tokens)=account();val email=tokens["email"].asText()
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer ${signed(email,mapOf("userId" to id),true)}"))
            .andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
        val parts=tokens["accessToken"].asText().split('.');val signature=parts[2]
        val altered=parts.take(2).joinToString(".")+"."+(if(signature[0]=='A')"B" else "A")+signature.drop(1)
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer $altered"))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/admin/hello").header("Authorization","Bearer ${signed(email,mapOf("userId" to id,"roles" to listOf("ROLE_ADMIN")))}"))
            .andExpect(status().isForbidden)
        val user=users.findById(id).orElseThrow()
        val generated=jwt.generateToken(mapOf("userId" to id+1000),user)
        assertEquals(id,jwt.extractClaim(generated){(it["userId"] as Number).toLong()})
    }

    @Test fun `logout cannot revoke another identity refresh token and logout all still works`() {
        val(_,a)=account();val(_,b)=account()
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer ${a["accessToken"].asText()}")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(LogoutRequest(b["refreshToken"].asText()))))
            .andExpect(status().isUnauthorized)
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RefreshTokenRequest(b["refreshToken"].asText())))).andExpect(status().isOk)
        mvc.perform(post("/api/v1/auth/logout-all").header("Authorization","Bearer ${a["accessToken"].asText()}"))
            .andExpect(status().isOk)
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RefreshTokenRequest(a["refreshToken"].asText())))).andExpect(status().isUnauthorized)
    }
}
