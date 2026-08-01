package com.quocdat.lingolens

import android.app.Application

class LingoLensApplication : Application() {
    val container by lazy { AppContainer(applicationContext) }
}
