package com.quocdat.lingolens.deletion

import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import com.quocdat.lingolens.data.remote.dto.UserProfileDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.*
import java.io.IOException

data class DeletionReply(val status:String)
data class DeleteRequest(val password:String,val code:String)
data class StatusRequest(val code:String)
interface DeletionGateway {
    suspend fun status(code:String):DeletionReply
    suspend fun verify(account:Long):Boolean
    suspend fun delete(account:Long,password:String,code:String):DeletionReply
}
interface DeletionApi {
    @GET("api/v1/users/me") suspend fun verify(@Header("X-LingoLens-Account") account:Long):Response<ApiEnvelope<UserProfileDto>>
    @POST("api/v1/users/me/deletion") suspend fun delete(@Header("X-LingoLens-Account") account:Long,@Body request:DeleteRequest):Response<ApiEnvelope<DeletionReply>>
}
interface DeletionStatusApi {
    @POST("api/v1/account-deletion/status") suspend fun status(@Body request:StatusRequest):Response<ApiEnvelope<DeletionReply>>
}
class RemoteDeletionGateway(private val api:DeletionApi,private val publicApi:DeletionStatusApi):DeletionGateway {
    private fun <T> body(response:Response<ApiEnvelope<T>>):T {
        if(!response.isSuccessful)throw HttpException(response)
        return response.body()?.takeIf{it.success}?.data ?: throw IOException("Invalid deletion response")
    }
    override suspend fun status(code:String)=body(publicApi.status(StatusRequest(code)))
    override suspend fun verify(account:Long)=body(api.verify(account)).id==account
    override suspend fun delete(account:Long,password:String,code:String)=body(api.delete(account,DeleteRequest(password,code)))
}
enum class DeletionOutcome {DELETED,RETRY_REQUIRED,UNCONFIRMED}
class DeletionRepository(val proofs:DeletionProofs,private val gateway:DeletionGateway,
    private val identity:suspend(Long)->String?,private val stopAndDrain:suspend(Long)->Unit,
    private val cleaner:AccountDataCleaner,private val clearOwnedSession:suspend(PendingDeletion)->Unit) {
    private val mutex=Mutex()
    private suspend fun finish(proof:PendingDeletion):DeletionOutcome=withContext(NonCancellable){
        stopAndDrain(proof.accountId)
        cleaner.purge(proof.accountId)
        clearOwnedSession(proof)
        proofs.remove(proof)
        DeletionOutcome.DELETED
    }
    private fun confirmed(reply:DeletionReply):Boolean {
        if(reply.status !in setOf("DELETED","UNKNOWN"))throw IOException("Invalid deletion status")
        return reply.status=="DELETED"
    }
    suspend fun start(account:Long,password:String):DeletionOutcome=mutex.withLock {
        require(password.isNotEmpty()){"Nhập lại mật khẩu để xác nhận."}
        val generation=identity(account) ?: return@withLock DeletionOutcome.UNCONFIRMED
        val proof=(proofs.get(account)?.copy(generation=generation) ?: PendingDeletion(account,generation,DeletionProofStore.newCode()))
        proofs.save(proof) // Separate durable encrypted store; complete before any HTTP request.
        if(confirmed(gateway.status(proof.code)))return@withLock finish(proof)
        if(identity(account)!=generation)return@withLock DeletionOutcome.UNCONFIRMED
        try{
            if(confirmed(gateway.delete(account,password,proof.code)))finish(proof) else DeletionOutcome.UNCONFIRMED
        }catch(cancel:CancellationException){throw cancel
        }catch(error:Exception){
            val recovered=try{confirmed(gateway.status(proof.code))}catch(cancel:CancellationException){throw cancel}catch(_:Exception){false}
            if(recovered)finish(proof)
            else if(error is HttpException && error.code()==401)DeletionOutcome.UNCONFIRMED
            else throw error
        }
    }
    suspend fun resume(proof:PendingDeletion):DeletionOutcome=mutex.withLock {
        if(confirmed(gateway.status(proof.code)))return@withLock finish(proof)
        if(identity(proof.accountId)==null)return@withLock DeletionOutcome.UNCONFIRMED
        try{if(gateway.verify(proof.accountId))DeletionOutcome.RETRY_REQUIRED else DeletionOutcome.UNCONFIRMED}
        catch(error:HttpException){if(error.code()==401)DeletionOutcome.UNCONFIRMED else throw error}
    }
}
