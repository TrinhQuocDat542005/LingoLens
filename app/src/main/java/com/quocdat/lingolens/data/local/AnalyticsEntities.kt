package com.quocdat.lingolens.data.local

import androidx.room.*

@Entity
data class AnalyticsConsentState(@PrimaryKey val accountId:Long,val enabled:Boolean=false,
    val consentVersion:String?=null,val consentRevision:Long=0,val consentAt:Long?=null,
    val pendingOptOut:Boolean=false,val updatedAt:Long)

@Entity(primaryKeys=["accountId","eventId"],
    foreignKeys=[ForeignKey(entity=AnalyticsConsentState::class,parentColumns=["accountId"],childColumns=["accountId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index(value=["accountId","state","queuedAt"])])
data class AnalyticsOutbox(val accountId:Long,val eventId:String,val eventName:String,val sessionId:String?,val attemptId:String?,
    val occurredAt:Long,val timezone:String,val appVersion:String,val propertiesJson:String,val payloadHash:String,
    val consentRevision:Long,val state:String="PENDING",val retryCount:Int=0,val queuedAt:Long,val lastError:String?=null)

@Dao
interface AnalyticsDao {
    @Upsert suspend fun saveConsent(state:AnalyticsConsentState)
    @Query("SELECT * FROM AnalyticsConsentState WHERE accountId=:accountId") suspend fun consent(accountId:Long):AnalyticsConsentState?
    @Query("SELECT * FROM AnalyticsConsentState WHERE accountId=:accountId") fun observeConsent(accountId:Long):kotlinx.coroutines.flow.Flow<AnalyticsConsentState?>
    @Insert suspend fun enqueue(event:AnalyticsOutbox)
    @Query("SELECT * FROM AnalyticsOutbox WHERE accountId=:accountId ORDER BY queuedAt,eventId") suspend fun events(accountId:Long):List<AnalyticsOutbox>
    @Query("DELETE FROM AnalyticsConsentState WHERE accountId=:accountId") suspend fun deleteConsent(accountId:Long)
}
