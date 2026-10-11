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
    suspend fun resumeStoredSync() { storage.cachedProfile()?.id?.let(syncScheduler::resume) }
    private val learningDatabase = androidx.room.Room.databaseBuilder(context.applicationContext,
        com.quocdat.lingolens.data.local.LearningDatabase::class.java, "learning.db")
        .addMigrations(com.quocdat.lingolens.data.local.LearningDatabase.MIGRATION_1_2,
            com.quocdat.lingolens.data.local.LearningDatabase.MIGRATION_2_3,
            com.quocdat.lingolens.data.local.LearningDatabase.MIGRATION_3_4).build()
    val syncScheduler = com.quocdat.lingolens.sync.SyncScheduler(context)
    val learningRepository = com.quocdat.lingolens.data.repository.LearningRepository(learningDatabase, onCommitted = syncScheduler::schedule)
    val syncDatabase get() = learningDatabase
    val reviewRepository = com.quocdat.lingolens.data.repository.ReviewRepository(learningDatabase, scheduleSync = syncScheduler::schedule)
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
        gson = gson
    )

    val recognitionRepository = RecognitionRepository(
        api = authenticatedRetrofit.create(RecognitionApi::class.java),
        gson = gson,
        db = learningDatabase,
        schedule = syncScheduler::schedule
    )
    val vocabularyRepository = com.quocdat.lingolens.data.repository.VocabularyRepository(
        authenticatedRetrofit.create(com.quocdat.lingolens.data.repository.VocabularyApi::class.java),
        learningDatabase, learningRepository)
    val lessonRepository = com.quocdat.lingolens.lesson.LessonRepository(
        authenticatedRetrofit.create(com.quocdat.lingolens.lesson.LessonApi::class.java))
    val contentPackRepository = com.quocdat.lingolens.lesson.ContentPackRepository(learningDatabase,
        com.quocdat.lingolens.lesson.RemotePackGateway(authenticatedRetrofit.create(com.quocdat.lingolens.lesson.PackApi::class.java)),
        isSignedIn = { id -> storage.current() != null && storage.cachedProfile()?.id == id },scheduleSync = syncScheduler::schedule)
    val lessonSessionRepository = com.quocdat.lingolens.lesson.LessonSessionRepository(learningDatabase,contentPackRepository,
        isSignedIn = { id -> storage.current() != null && storage.cachedProfile()?.id == id },scheduleSync = syncScheduler::schedule)
    val syncEngine = com.quocdat.lingolens.sync.SyncEngine(learningDatabase,
        authenticatedRetrofit.create(com.quocdat.lingolens.sync.SyncApi::class.java),
        isSignedIn = { id -> storage.current() != null && storage.cachedProfile()?.id == id })
    val lessonSyncEngine = com.quocdat.lingolens.lesson.LessonSyncEngine(learningDatabase,
        com.quocdat.lingolens.lesson.RemoteLessonSyncGateway(authenticatedRetrofit.create(com.quocdat.lingolens.lesson.LessonSyncApi::class.java)),
        isSignedIn = { id -> storage.current() != null && storage.cachedProfile()?.id == id })
    val syncCoordinator = com.quocdat.lingolens.sync.SyncCoordinator(syncEngine::run,lessonSyncEngine::run)
}
