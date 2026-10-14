package com.quocdat.lingolens.deletion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.quocdat.lingolens.data.local.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class DeletionRepositoryTest {
    private lateinit var db:LearningDatabase
    private lateinit var repo:DeletionRepository
    private val stored=MutableStateFlow<List<PendingDeletion>>(emptyList())
    private var actor=1L
    private var deleted=false
    private var networkLost=false
    private var verification401=false
    private var calls=0
    private val stopped=mutableListOf<Long>()
    private val cleared=mutableListOf<Long>()
    private val proofs=object:DeletionProofs {
        override fun observe()=stored
        override suspend fun get(account:Long)=stored.value.firstOrNull{it.accountId==account}
        override suspend fun save(proof:PendingDeletion){stored.value=stored.value.filterNot{it.accountId==proof.accountId}+proof}
        override suspend fun remove(proof:PendingDeletion){stored.value=stored.value.filterNot{it.accountId==proof.accountId && it.code==proof.code}}
    }
    private val gateway=object:DeletionGateway {
        override suspend fun status(code:String)=DeletionReply(if(deleted)"DELETED" else "UNKNOWN")
        override suspend fun verify(account:Long):Boolean {if(verification401)throw HttpException(Response.error<Any>(401,"{}".toResponseBody()));return actor==account}
        override suspend fun delete(account:Long,password:String,code:String):DeletionReply {
            assertNotNull(proofs.get(account));assertEquals(code,proofs.get(account)!!.code);assertEquals(account,actor)
            calls++;if(password!="correct")throw HttpException(Response.error<Any>(403,"{}".toResponseBody()))
            deleted=true;if(networkLost){networkLost=false;throw IOException("Response lost after commit")};return DeletionReply("DELETED")
        }
    }
    private fun open(){repo=DeletionRepository(proofs,gateway,identity={id->if(actor==id)"login-$id" else null},
        stopAndDrain={stopped.add(it)},cleaner=AccountDataCleaner(db),clearOwnedSession={proof->
            assertTrue(db.learningDao().allWords(proof.accountId).isEmpty())
            if(actor==proof.accountId){cleared.add(actor);actor=0}
            com.quocdat.lingolens.analytics.AnalyticsPromptPreferences(ApplicationProvider.getApplicationContext<Context>()).forget(proof.accountId)
        })}
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),LearningDatabase::class.java).build()
        db.learningDao().save(SavedWordEntity(1,"book","{}",10));db.learningDao().save(SavedWordEntity(2,"phone","{}",20));open()
        val flags=com.quocdat.lingolens.analytics.AnalyticsPromptPreferences(ApplicationProvider.getApplicationContext<Context>())
        flags.rememberChoice(1);flags.rememberChoice(2)
    }
    @After fun close(){db.close()}
    @Test fun `proof is persisted before request and lost committed response cleans only confirmed account`()=runBlocking {
        networkLost=true;assertEquals(DeletionOutcome.DELETED,repo.start(1,"correct"));assertEquals(1,calls)
        assertEquals(listOf(1L),stopped);assertEquals(listOf(1L),cleared);assertTrue(stored.value.isEmpty())
        assertNotNull(db.learningDao().find(2,"phone"))
    }
    @Test fun `restart with proof and deleted server requires no token or password`()=runBlocking {
        val proof=PendingDeletion(1,"login-1",DeletionProofStore.newCode());proofs.save(proof);deleted=true;actor=0;open()
        assertEquals(DeletionOutcome.DELETED,repo.resume(proof));assertTrue(db.learningDao().allWords(1).isEmpty());assertTrue(stored.value.isEmpty());assertEquals(0,calls)
    }
    @Test fun `unknown proof with valid token requires password retry using same persisted code`()=runBlocking {
        val proof=PendingDeletion(1,"login-1",DeletionProofStore.newCode());proofs.save(proof)
        assertEquals(DeletionOutcome.RETRY_REQUIRED,repo.resume(proof));assertNotNull(db.learningDao().find(1,"book"));assertEquals(proof,proofs.get(1))
        assertEquals(DeletionOutcome.DELETED,repo.start(1,"correct"));assertEquals(1,calls)
    }
    @Test fun `unknown expired or wrong proof plus 401 never clears Room credentials or proof`()=runBlocking {
        val proof=PendingDeletion(1,"login-1",DeletionProofStore.newCode());proofs.save(proof);verification401=true
        assertEquals(DeletionOutcome.UNCONFIRMED,repo.resume(proof));assertNotNull(db.learningDao().find(1,"book"))
        assertTrue(stopped.isEmpty());assertTrue(cleared.isEmpty());assertEquals(proof,proofs.get(1))
    }
    @Test fun `confirmation for old A while B is signed in cannot clear B credentials or Room`()=runBlocking {
        val proof=PendingDeletion(1,"login-1",DeletionProofStore.newCode());proofs.save(proof);deleted=true;actor=2
        assertEquals(DeletionOutcome.DELETED,repo.resume(proof));assertEquals(2L,actor);assertTrue(cleared.isEmpty())
        assertNotNull(db.learningDao().find(2,"phone"));assertEquals(listOf(1L),stopped)
        val flags=com.quocdat.lingolens.analytics.AnalyticsPromptPreferences(ApplicationProvider.getApplicationContext<Context>())
        assertFalse(flags.seen(1));assertTrue(flags.seen(2))
    }
    @Test fun `wrong password preserves data and proof and successful retry uses same code`()=runBlocking {
        try{repo.start(1,"wrong");fail("Password must be confirmed")}catch(_:HttpException){}
        val proof=proofs.get(1)!!;assertNotNull(db.learningDao().find(1,"book"));assertTrue(cleared.isEmpty())
        assertEquals(DeletionOutcome.DELETED,repo.start(1,"correct"));assertEquals(2,calls)
        assertEquals(43,proof.code.length)
    }
    @Test fun `proof store survives credential cleanup and has secure random 256 bit codes`()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val cipher=object:SessionTokenCipher {override fun encrypt(value:String)="encrypted:$value";override fun decrypt(value:String)=value.removePrefix("encrypted:")}
        val store=DeletionProofStore(context,cipher)
        val proof=PendingDeletion(101,"generation",DeletionProofStore.newCode());store.save(proof)
        SessionStorage(context,cipher).clear()
        val reopened=DeletionProofStore(context,cipher);assertEquals(proof,reopened.get(101))
        val codes=(1..100).map{DeletionProofStore.newCode()}
        assertEquals(100,codes.toSet().size);assertTrue(codes.all{it.matches(Regex("[A-Za-z0-9_-]{43}"))})
        reopened.remove(proof);assertNull(store.get(101))
    }
}
