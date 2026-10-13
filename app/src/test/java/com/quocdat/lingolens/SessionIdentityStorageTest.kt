package com.quocdat.lingolens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.remote.dto.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class SessionIdentityStorageTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    // Test cipher only; production AES/GCM and AndroidKeyStore are not replaced.
    private val codec=object:SessionTokenCipher {
        override fun encrypt(value:String)="test-encoded:$value"
        override fun decrypt(value:String):String?=value.takeIf{it.startsWith("test-encoded:")}?.removePrefix("test-encoded:")
    }
    private val storage=SessionStorage(context,codec)
    private val email="same@example.invalid"
    private fun tokens(value:String)=TokenDto("access-$value","refresh-$value","Bearer",900,email,"Owner",listOf("ROLE_USER"))
    private fun profile(id:Long)=UserProfileDto(id,email,"Owner","B2",3,12,listOf("ROLE_USER"))
    @Before fun clearBefore()=runBlocking {storage.clear();Unit}
    @After fun clearAfter()=runBlocking {storage.clear();Unit}
    @Test fun `fresh login with same email clears old ID before new server profile is available`()=runBlocking {
        storage.save(tokens("old"));val previous=storage.current()!!;storage.saveProfile(profile(101),previous);assertEquals(101L,storage.cachedProfile()!!.id)
        storage.save(tokens("new"))
        assertNull(storage.cachedProfile());assertEquals("access-new",storage.current()!!.accessToken)
        storage.saveProfile(profile(101),previous);assertNull(storage.cachedProfile())
        assertNotEquals(previous.generation,storage.current()!!.generation)
        storage.saveProfile(profile(202),storage.current()!!);assertEquals(202L,storage.cachedProfile()!!.id)
        val reopened=SessionStorage(context,codec)
        assertEquals(202L,reopened.cachedProfile()!!.id);assertEquals(storage.current(),reopened.current())
    }
    @Test fun `normal refresh preserves profile but stale old login refresh cannot overwrite new identity`()=runBlocking {
        storage.save(tokens("old"));val old=storage.current()!!;storage.saveProfile(profile(101),old)
        assertTrue(storage.saveRefreshed(old,tokens("rotated")));assertEquals(101L,storage.cachedProfile()!!.id)
        assertEquals(old.generation,storage.current()!!.generation)
        storage.saveProfile(profile(101).copy(name="Updated after refresh"),old)
        assertEquals("Updated after refresh",storage.cachedProfile()!!.name)
        val previous=storage.current()!!
        storage.save(tokens("new"));storage.saveProfile(profile(202),storage.current()!!);val current=storage.current()
        assertFalse(storage.saveRefreshed(previous,tokens("late-old")));storage.clearIfCurrent(previous)
        assertEquals(current,storage.current());assertEquals(202L,storage.cachedProfile()!!.id)
        storage.clear();assertNull(storage.current());assertNull(storage.cachedProfile())
    }
}
