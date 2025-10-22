package ch.slf.whiterisk.kmpsam

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

/**
 * Android implementation of PlatformContext.
 * Provides access to the Application Context.
 */
actual object PlatformContext {
    @SuppressLint("StaticFieldLeak")
    private lateinit var applicationContext: Context
    
    /**
     * Initializes the platform context with the Application instance.
     * This should be called from the Application.onCreate() or MainActivity.onCreate().
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }
    
    /**
     * Returns the Application Context.
     */
    actual fun get(): Any {
        return applicationContext
    }
}

