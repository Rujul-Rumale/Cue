package dev.context.app

import android.app.Application

class ContextApp : Application() {

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }

    companion object {
        /**
         * Process-wide access point for receivers and other non-composable entry points.
         * Application.onCreate always runs before any broadcast, so this is non-null
         * whenever the process is alive.
         */
        @Volatile
        lateinit var container: AppContainer
    }
}