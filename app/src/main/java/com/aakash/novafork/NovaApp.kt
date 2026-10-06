package com.aakash.novafork

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory

class NovaApp : Application(), ImageLoaderFactory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    /** Coil's singleton loader (OkHttp with secure DNS, video frames, smb:// art). */
    override fun newImageLoader(): ImageLoader = graph.imageLoader

    companion object {
        fun graph(context: Context): AppGraph = (context.applicationContext as NovaApp).graph
    }
}
