package com.quocdat.lingolens

import android.app.Application
import kotlinx.coroutines.launch

class LingoLensApplication : Application() {
    val container by lazy { AppContainer(applicationContext) }
    val analyticsForeground=com.quocdat.lingolens.analytics.AnalyticsForegroundTracker()
    private val backgroundScope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object:ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity:android.app.Activity){val foreground=analyticsForeground.started();backgroundScope.launch{container.captureForeground(foreground)}}
            override fun onActivityStopped(activity:android.app.Activity){analyticsForeground.stopped(activity.isChangingConfigurations)}
            override fun onActivityCreated(activity:android.app.Activity,state:android.os.Bundle?){}
            override fun onActivityResumed(activity:android.app.Activity){}
            override fun onActivityPaused(activity:android.app.Activity){}
            override fun onActivitySaveInstanceState(activity:android.app.Activity,state:android.os.Bundle){}
            override fun onActivityDestroyed(activity:android.app.Activity){}
        })
        // Recover a committed Room operation even if the process died before WorkManager enqueue.
        backgroundScope.launch {
            var previousGeneration:String?=null
            container.authRepository.session.collect { stored ->
                if(stored==null || (previousGeneration!=null && previousGeneration!=stored.generation))container.pauseAnalytics()
                previousGeneration=stored?.generation
                if (stored != null) container.resumeStoredSync()
            }
        }
    }
}
