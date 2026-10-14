package com.quocdat.lingolens

import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.remote.TokenAuthenticator
import com.quocdat.lingolens.data.remote.AuthInterceptor
import com.quocdat.lingolens.data.remote.RequestAccount
import com.quocdat.lingolens.data.remote.api.AuthApi
import com.quocdat.lingolens.data.remote.dto.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class TokenAuthenticatorTest {
    private class Store : CredentialStore {
        var value: StoredSession? = StoredSession("old-access", "old-refresh", "a@example.com", "A")
        var id = 1L
        override suspend fun current() = value
        override suspend fun cachedProfile() = value?.let { UserProfileDto(id, it.email, it.name, "B1", 0, 5, emptyList()) }
        override suspend fun saveRefreshed(expected: StoredSession, tokens: TokenDto): Boolean {
            if (value != expected) return false
            value = expected.copy(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken); return true
        }
        override suspend fun clearIfCurrent(expected: StoredSession) { if (value == expected) value = null }
    }
    private class Api : AuthApi {
        val calls = AtomicInteger()
        var beforeReturn: () -> Unit = {}
        var error: Exception? = null
        override suspend fun refresh(request: RefreshRequest): ApiEnvelope<TokenDto> {
            calls.incrementAndGet(); error?.let { throw it }; beforeReturn()
            return ApiEnvelope(true, "ok", TokenDto("new-access", "new-refresh", "Bearer", 900, "a@example.com", "A", emptyList()))
        }
        override suspend fun register(request: RegisterRequest): ApiEnvelope<UserProfileDto> = throw IllegalStateException("unused")
        override suspend fun login(request: LoginRequest): ApiEnvelope<TokenDto> = throw IllegalStateException("unused")
        override suspend fun logout(request: LogoutRequest): ApiEnvelope<Unit> = throw IllegalStateException("unused")
        override suspend fun logoutAll(): ApiEnvelope<Unit> = throw IllegalStateException("unused")
    }
    private fun expired() = Response.Builder().request(Request.Builder().url("https://example.com/")
        .header("Authorization", "Bearer old-access").header("X-LingoLens-Account", "1").build())
        .protocol(Protocol.HTTP_1_1).code(401).message("Expired").build()
    @Test fun `revoked refresh or legacy access logs out once without an infinite refresh loop`() {
        for(code in listOf(401,403)){
            val store=Store();val api=Api().apply{error=retrofit2.HttpException(retrofit2.Response.error<Any>(code,"{\"code\":\"INVALID_TOKEN\"}".toResponseBody()))}
            val authenticator=TokenAuthenticator(store,api)
            assertNull(authenticator.authenticate(null,expired()));assertNull(store.value)
            repeat(3){assertNull(authenticator.authenticate(null,expired()))}
            assertEquals(1,api.calls.get())
        }
    }
    @Test fun `access rejected for missing identity claim signs out without refresh and retains error body`() {
        val store=Store();val api=Api()
        val response=expired().newBuilder().body("{\"code\":\"INVALID_TOKEN\",\"message\":\"Missing userId\"}".toResponseBody()).build()
        assertNull(TokenAuthenticator(store,api).authenticate(null,response));assertNull(store.value)
        assertEquals(0,api.calls.get());assertTrue(response.body!!.string().contains("INVALID_TOKEN"))
    }
    @Test fun `second unauthorized response logs out current session but cannot clear a newer login`() {
        val store=Store();val api=Api();val authenticator=TokenAuthenticator(store,api)
        val retried=expired().newBuilder().priorResponse(expired()).build()
        assertNull(authenticator.authenticate(null,retried));assertNull(store.value);assertEquals(0,api.calls.get())
        store.value=StoredSession("old-access","different-refresh","a@example.com","A","new-login")
        val stale=retried.newBuilder().request(retried.request.newBuilder()
            .tag(RequestAccount::class.java,RequestAccount("a@example.com","old-login")).build()).build()
        assertNull(authenticator.authenticate(null,stale));assertEquals("new-login",store.value!!.generation)
    }
    @Test fun `concurrent expired requests refresh once and reuse rotated credentials`() {
        val store = Store(); val api = Api(); val authenticator = TokenAuthenticator(store, api)
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val requests = (1..2).map { pool.submit<String?> { start.await(); authenticator.authenticate(null, expired())?.header("Authorization") } }
            start.countDown()
            assertEquals(listOf("Bearer new-access", "Bearer new-access"), requests.map { it.get(5, TimeUnit.SECONDS) })
            assertEquals(1, api.calls.get())
        } finally { pool.shutdownNow() }
    }
    @Test fun `network failure keeps session while account switch prevents refresh`() {
        val store = Store(); val api = Api().apply { error = IOException("offline") }
        assertNull(TokenAuthenticator(store, api).authenticate(null, expired()))
        assertNotNull(store.value)
        store.id = 2
        assertNull(TokenAuthenticator(store, api).authenticate(null, expired()))
        assertEquals(1, api.calls.get())
    }
    @Test fun `late refresh response cannot overwrite a newer signed in account`() {
        val store = Store(); val api = Api().apply { beforeReturn = { store.value = StoredSession("b-access", "b-refresh", "b@example.com", "B"); store.id = 2 } }
        assertNull(TokenAuthenticator(store, api).authenticate(null, expired()))
        assertEquals("b-access", store.value?.accessToken)
    }
    @Test fun `request from previous account is never replayed with current credentials`() {
        val store = Store().apply { value = StoredSession("b-access", "b-refresh", "b@example.com", "B") }
        val api = Api()
        val response = expired().newBuilder().request(expired().request.newBuilder()
            .removeHeader("X-LingoLens-Account").tag(RequestAccount::class.java, RequestAccount("a@example.com")).build()).build()
        assertNull(TokenAuthenticator(store, api).authenticate(null, response))
        assertEquals(0, api.calls.get())
    }
    @Test fun `A account header cannot dispatch using B token after logout login`() {
        val store=Store();var dispatched=0;var bearer:String?=null
        val client=OkHttpClient.Builder().addInterceptor(AuthInterceptor(store)).addInterceptor{chain->
            dispatched++;bearer=chain.request().header("Authorization")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody()).build()
        }.build()
        val request=Request.Builder().url("http://localhost/api/v1/lesson-attempts").header("X-LingoLens-Account","1").build()
        store.value=null
        try{client.newCall(request).execute().close();fail("Signed out request must be rejected")}catch(_:IOException){}
        store.value=StoredSession("b-access","b-refresh","b@example.com","B");store.id=2
        try{client.newCall(request).execute().close();fail("A request must be rejected before dispatch with B credentials")}catch(_:IOException){}
        assertEquals(0,dispatched);assertNull(bearer)
        store.value=StoredSession("a-access","a-refresh","a@example.com","A");store.id=1
        client.newCall(request).execute().close();assertEquals(1,dispatched);assertEquals("Bearer a-access",bearer)
    }
    @Test fun `account switch between profile check and token read cannot dispatch A with B token`() {
        var value:StoredSession?=StoredSession("a","ar","a@example.invalid","A")
        val store=object:CredentialStore {
            override suspend fun current()=value
            override suspend fun cachedProfile():UserProfileDto? {value=StoredSession("b","br","b@example.invalid","B");return UserProfileDto(1,"a@example.invalid","A","A1",0,5,emptyList())}
            override suspend fun saveRefreshed(expected:StoredSession,tokens:TokenDto)=false
            override suspend fun clearIfCurrent(expected:StoredSession){}
        }
        var sent=0;val client=OkHttpClient.Builder().addInterceptor(AuthInterceptor(store)).addInterceptor{chain->sent++;Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("{}".toResponseBody()).build()}.build()
        try{client.newCall(Request.Builder().url("http://localhost/").header("X-LingoLens-Account","1").build()).execute().close();fail("Identity race must stop transport")}catch(_:IOException){}
        assertEquals(0,sent)
    }
}
