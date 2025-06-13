package com.salesforce.voiceflow

import android.app.Application
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager

/**
 * Application class for our application.
 */
class MainApplication : Application() {

    companion object {
        private const val FEATURE_APP_USES_KOTLIN = "KT"
    }

    // region Activity Implementation
    override fun onCreate() {
        super.onCreate()
        MobileSyncSDKManager.initNative(
            applicationContext,
            MainActivity::class.java,
        )
        MobileSyncSDKManager.getInstance().registerUsedAppFeature(FEATURE_APP_USES_KOTLIN)
    }
}
