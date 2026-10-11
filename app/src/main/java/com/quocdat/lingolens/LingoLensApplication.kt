package com.quocdat.lingolens

import android.app.Application
import kotlinx.coroutines.launch

class LingoLensApplication : Application() {
    val container by lazy { AppContainer(applicationContext) }
    override fun onCreate() {
        super.onCreate()
        // Recover a committed Room operation even if the process died before WorkManager enqueue.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            container.authRepository.session.collect { stored ->
                if (stored != null) container.resumeStoredSync()
            }
        }
    }
}
