package com.aakash.novafork

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.aakash.novafork.ui.MainActivity

class NovaApp : Application(), ImageLoaderFactory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        // First, so a crash anywhere below still leaves a report for the next start.
        CrashReporter.install(this)
        super.onCreate()
        graph = AppGraph(this)
        registerActivityLifecycleCallbacks(MainScreenLaunches())
    }

    /** Opening the main screen is an app launch for the library (see AppGraph.onMainScreenCreated). */
    private inner class MainScreenLaunches : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is MainActivity) graph.onMainScreenCreated()
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    /** Coil's singleton loader (OkHttp with secure DNS, video frames, smb:// art). */
    override fun newImageLoader(): ImageLoader = graph.imageLoader

    companion object {
        fun graph(context: Context): AppGraph = (context.applicationContext as NovaApp).graph
    }
}
