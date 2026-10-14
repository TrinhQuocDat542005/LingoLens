package com.quocdat.lingolens

import android.content.Context
import com.google.gson.Gson
import com.quocdat.lingolens.data.local.SessionStorage
import com.quocdat.lingolens.data.remote.AuthInterceptor
import com.quocdat.lingolens.data.remote.TokenAuthenticator
import com.quocdat.lingolens.data.remote.api.AuthApi
import com.quocdat.lingolens.data.remote.api.UserApi
import com.quocdat.lingolens.data.remote.api.RecognitionApi
import com.quocdat.lingolens.data.repository.AuthRepository
import com.quocdat.lingolens.data.repository.RecognitionRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val deletingAccounts=java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private suspend fun activeAccount(id:Long)=id !in deletingAccounts && storage.current()!=null && storage.cachedProfile()?.id==id
    @Volatile private var analyticsPaused=false
    @Volatile private var analyticsAccount:Long?=null
    suspend fun resumeStoredSync() { storage.cachedProfile()?.id?.takeIf{it !in deletingAccounts}?.let {syncScheduler.resume(it);if(!analyticsPaused)analyticsScheduler.resume(it)} }
    private val learningDatabase = androidx.room.Room.databaseBuilder(context.applicationContext,
        com.quocdat.lingolens.data.local.LearningDatabase::class.java, "learning.db")
        .addMigrations(com.quocdat.lingolens.data.local.LearningDatabase.MIGRATION_1_2,
            com.quocdat.lingolens.data.local.LearningDatabase.MIGRATION_2_3,
            com.quocdat.lingolens.data.local.LearningDatabase.MIGRATION_3_4,
            com.quocdat.lingolens.data.local.LearningDatabase.MIGRATION_4_5).build()
    val syncScheduler = com.quocdat.lingolens.sync.SyncScheduler(context)
    val learningRepository = com.quocdat.lingolens.data.repository.LearningRepository(learningDatabase, onCommitted = syncScheduler::schedule)
    val syncDatabase get() = learningDatabase
    val reviewRepository = com.quocdat.lingolens.data.repository.ReviewRepository(learningDatabase, scheduleSync = syncScheduler::schedule,
        onFinished={analyticsRepository.onReviewFinished(it)})
    private val gson = Gson()
    private val storage = SessionStorage(context)
    private val baseUrl = BuildConfig.API_BASE_URL

    private val publicClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
            })
        }
        .build()

    private val publicRetrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(publicClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val authApi = publicRetrofit.create(AuthApi::class.java)

    private val authenticatedClient = publicClient.newBuilder()
        .addInterceptor(AuthInterceptor(storage))
        .authenticator(TokenAuthenticator(storage, authApi))
        .build()

    private val authenticatedRetrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(authenticatedClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val authRepository = AuthRepository(
        authApi = authenticatedRetrofit.create(AuthApi::class.java),
        userApi = authenticatedRetrofit.create(UserApi::class.java),
        storage = storage,
        gson = gson,
        beforeLogout={pauseAnalytics(it)}
    )

    val analyticsScheduler=com.quocdat.lingolens.analytics.AnalyticsScheduler(context)
    val analyticsRepository=com.quocdat.lingolens.analytics.AnalyticsRepository(learningDatabase,
        com.quocdat.lingolens.analytics.RemoteAnalyticsGateway(authenticatedRetrofit.create(com.quocdat.lingolens.analytics.AnalyticsApi::class.java)),
        identity={id->analyticsIdentity(id)},version=BuildConfig.VERSION_NAME,schedule=analyticsScheduler::schedule,cancel=analyticsScheduler::cancel)
    private suspend fun analyticsIdentity(id:Long):String? {
        if(analyticsPaused || id in deletingAccounts)return null
        val before=storage.current() ?: return null
        if(storage.cachedProfile()?.id!=id || storage.current()!=before)return null
        return before.generation ?: before.refreshToken
    }
    fun pauseAnalytics(account:Long?=analyticsAccount){analyticsPaused=true;account?.let(analyticsScheduler::cancel);analyticsAccount=null}
    suspend fun activateAnalytics(account:Long,foreground:com.quocdat.lingolens.analytics.AnalyticsForeground?){
        analyticsAccount?.takeIf{it!=account}?.let(analyticsScheduler::cancel)
        analyticsAccount=account;analyticsPaused=false
        analyticsRepository.bestEffort{analyticsRepository.prune(account)}
        analyticsRepository.bestEffort{analyticsRepository.initialize(account)}
        analyticsRepository.bestEffort{analyticsRepository.recover(account)}
        captureForeground(foreground);analyticsScheduler.resume(account)
    }
    suspend fun captureForeground(foreground:com.quocdat.lingolens.analytics.AnalyticsForeground?){
        if(foreground==null)return
        storage.cachedProfile()?.id?.let{id->analyticsRepository.bestEffort{analyticsRepository.capture(id,"app_open",foreground.id,foreground.startedAt)}}
    }

    val deletionRepository=com.quocdat.lingolens.deletion.DeletionRepository(
        com.quocdat.lingolens.deletion.DeletionProofStore(context),
        com.quocdat.lingolens.deletion.RemoteDeletionGateway(
            authenticatedRetrofit.create(com.quocdat.lingolens.deletion.DeletionApi::class.java),
            publicRetrofit.create(com.quocdat.lingolens.deletion.DeletionStatusApi::class.java)),
        identity={id->if(activeAccount(id)){val current=storage.current();current?.generation ?: current?.refreshToken}else null},
        stopAndDrain={id->
            deletingAccounts.add(id)
            if(storage.cachedProfile()?.id==id)pauseAnalytics(id) else analyticsScheduler.cancel(id)
            syncScheduler.cancel(id)
            syncEngine.awaitIdle();lessonSyncEngine.awaitIdle();analyticsRepository.awaitIdle()
        },
        cleaner=com.quocdat.lingolens.deletion.AccountDataCleaner(learningDatabase),
        clearOwnedSession={proof->
            val current=storage.current()
            if(current!=null && (storage.cachedProfile()?.id==proof.accountId || current.generation==proof.generation))storage.clearIfCurrent(current)
            com.quocdat.lingolens.analytics.AnalyticsPromptPreferences(context).forget(proof.accountId)
        })

    val recognitionRepository = RecognitionRepository(
        api = authenticatedRetrofit.create(RecognitionApi::class.java),
        gson = gson,
        db = learningDatabase,
        schedule = syncScheduler::schedule
    )
    val vocabularyRepository = com.quocdat.lingolens.data.repository.VocabularyRepository(
        authenticatedRetrofit.create(com.quocdat.lingolens.data.repository.VocabularyApi::class.java),
        learningDatabase, learningRepository)
    val topicRepository=com.quocdat.lingolens.topic.TopicRepository(learningDatabase,
        com.quocdat.lingolens.topic.RemoteTopicGateway(authenticatedRetrofit.create(com.quocdat.lingolens.topic.TopicApi::class.java)))
    val lessonRepository = com.quocdat.lingolens.lesson.LessonRepository(
        authenticatedRetrofit.create(com.quocdat.lingolens.lesson.LessonApi::class.java))
    val contentPackRepository = com.quocdat.lingolens.lesson.ContentPackRepository(learningDatabase,
        com.quocdat.lingolens.lesson.RemotePackGateway(authenticatedRetrofit.create(com.quocdat.lingolens.lesson.PackApi::class.java)),
        isSignedIn = { id -> activeAccount(id) },scheduleSync = syncScheduler::schedule)
    val lessonSessionRepository = com.quocdat.lingolens.lesson.LessonSessionRepository(learningDatabase,contentPackRepository,
        isSignedIn = { id -> activeAccount(id) },scheduleSync = syncScheduler::schedule,
        onStarted={analyticsRepository.onLessonStarted(it)},onCompleted={id,session->analyticsRepository.onLessonCompleted(id,session)})
    val syncEngine = com.quocdat.lingolens.sync.SyncEngine(learningDatabase,
        authenticatedRetrofit.create(com.quocdat.lingolens.sync.SyncApi::class.java),
        isSignedIn = { id -> activeAccount(id) })
    val lessonSyncEngine = com.quocdat.lingolens.lesson.LessonSyncEngine(learningDatabase,
        com.quocdat.lingolens.lesson.RemoteLessonSyncGateway(authenticatedRetrofit.create(com.quocdat.lingolens.lesson.LessonSyncApi::class.java)),
        isSignedIn = { id -> activeAccount(id) })
    val syncCoordinator = com.quocdat.lingolens.sync.SyncCoordinator(syncEngine::run,lessonSyncEngine::run)
    val lessonStudyRepository = com.quocdat.lingolens.lesson.LessonStudyRepository(learningDatabase,contentPackRepository,lessonSessionRepository,
        com.quocdat.lingolens.lesson.RemoteStudyGateway(authenticatedRetrofit.create(com.quocdat.lingolens.lesson.StudyApi::class.java),
            com.quocdat.lingolens.lesson.RemoteLessonSyncGateway(authenticatedRetrofit.create(com.quocdat.lingolens.lesson.LessonSyncApi::class.java))),
        isSignedIn = { id -> activeAccount(id) },scheduleSync = syncScheduler::schedule)
}
