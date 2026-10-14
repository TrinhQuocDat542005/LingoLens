package com.quocdat.lingolens.analytics

import java.util.UUID

data class AnalyticsForeground(val id:String,val startedAt:Long)
class AnalyticsForegroundTracker(private val clock:()->Long=System::currentTimeMillis) {
    private var active=0
    private var foreground:AnalyticsForeground?=null
    @Synchronized fun started():AnalyticsForeground {
        active++;if(foreground==null)foreground=AnalyticsForeground(UUID.randomUUID().toString(),clock())
        return foreground!!
    }
    @Synchronized fun stopped(changingConfigurations:Boolean){
        active=(active-1).coerceAtLeast(0)
        if(active==0 && !changingConfigurations)foreground=null
    }
    @Synchronized fun current()=foreground
}
