package com.quocdat.lingolens

import android.content.Context
import com.google.gson.Gson
import com.quocdat.lingolens.data.local.SessionStorage
import com.quocdat.lingolens.data.remote.AuthInterceptor
import com.quocdat.lingolens.data.remote.TokenAuthenticator
import com.quocdat.lingolens.data.remote.api.AuthApi
import com.quocdat.lingolens.data.remote.api.UserApi
import com.quocdat.lingolens.data.repository.AuthRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val gson = Gson()
    private val storage = SessionStorage(context)
    private val baseUrl = "http://10.0.2.2:8080/"

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
}
