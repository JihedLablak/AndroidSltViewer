package com.nuitdinfo.sltviewer

import android.app.Application
import com.nuitdinfo.sltviewer.model.Model

class ModelViewerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: ModelViewerApplication

        // Store the current model globally, so that we don't have to re-decode it upon
        // obviously this is not optimal.
        // TODO: handle this a bit better.
        var currentModel: Model? = null
    }
}
