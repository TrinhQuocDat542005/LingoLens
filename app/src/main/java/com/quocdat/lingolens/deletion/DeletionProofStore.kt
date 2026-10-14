package com.quocdat.lingolens.deletion

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.quocdat.lingolens.data.local.SessionTokenCipher
import com.quocdat.lingolens.data.local.TokenCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.deletionProofDataStore by preferencesDataStore("deletion_proofs")
data class PendingDeletion(val accountId:Long,val generation:String,val code:String)
interface DeletionProofs {
    fun observe():Flow<List<PendingDeletion>>
    suspend fun get(account:Long):PendingDeletion?
    suspend fun save(proof:PendingDeletion)
    suspend fun remove(proof:PendingDeletion)
}
class DeletionProofStore(context:Context,private val cipher:SessionTokenCipher=TokenCipher()):DeletionProofs {
    private val context=context.applicationContext
    private val gson=Gson()
    companion object {
        fun newCode():String=Base64.encodeToString(ByteArray(32).also{SecureRandom().nextBytes(it)},Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    override fun observe()=context.deletionProofDataStore.data.map{prefs->prefs.asMap().entries.mapNotNull{(key,value)->
        if(!key.name.startsWith("proof-") || value !is String)null
        else runCatching{cipher.decrypt(value)?.let{gson.fromJson(it,PendingDeletion::class.java)}}.getOrNull()
            ?.takeIf{it.accountId>0 && it.generation.isNotBlank() && it.code.matches(Regex("[A-Za-z0-9_-]{43}"))}
    }.sortedBy{it.accountId}}
    override suspend fun get(account:Long)=observe().first().firstOrNull{it.accountId==account}
    override suspend fun save(proof:PendingDeletion){
        require(proof.accountId>0 && proof.generation.isNotBlank() && proof.code.matches(Regex("[A-Za-z0-9_-]{43}")))
        context.deletionProofDataStore.edit{it[stringPreferencesKey("proof-${proof.accountId}")]=cipher.encrypt(gson.toJson(proof))}
    }
    override suspend fun remove(proof:PendingDeletion){
        context.deletionProofDataStore.edit{prefs->
            val key=stringPreferencesKey("proof-${proof.accountId}")
            val current=prefs[key]?.let(cipher::decrypt)?.let{gson.fromJson(it,PendingDeletion::class.java)}
            if(current?.code==proof.code)prefs.remove(key)
        }
    }
}
