/*
 * Copyright (c) 2025 Toni Melisma
 */
package com.salesforce.voiceflow

import android.app.Application
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.ui.LoginActivity

class MainApplication : Application() {

    companion object {
        private const val FEATURE_APP_USES_KOTLIN = "KT"
    }

    override fun onCreate() {
        super.onCreate()
        MobileSyncSDKManager.initNative(
            applicationContext,
            MainActivity::class.java,
            LoginActivity::class.java // Use standard LoginActivity
        )
        MobileSyncSDKManager.getInstance().registerUsedAppFeature(FEATURE_APP_USES_KOTLIN)

        /*
		 * Un-comment the line below to enable push notifications in this app.
		 * Replace 'pnInterface' with your implementation of 'PushNotificationInterface'.
		 * Add your Firebase 'google-services.json' file to the 'app' folder of your project.
		 */
        // MobileSyncSDKManager.getInstance().pushNotificationReceiver = pnInterface
    }
}