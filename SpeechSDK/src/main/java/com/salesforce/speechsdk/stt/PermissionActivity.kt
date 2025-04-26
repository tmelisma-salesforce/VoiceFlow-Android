package com.salesforce.speechsdk.stt

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.salesforce.speechsdk.R

/**
 * A self-contained class that handles checking for and requesting the necessary permissions
 * from the user in order to perform speech-to-text and text-to-speech operations.
 */
internal class PermissionActivity : AppCompatActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            handleActivityResults(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askForPermission()
    }

    @VisibleForTesting
    internal fun handleActivityResults(granted: Boolean) {
        if (granted) {
            invokeCallback()
        } else {
            val shouldShowRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this, requiredPermission)

            if (!shouldShowRationale) {
                invokeCallback()
            } else {
                showAlertDialog(permissionRationaleTitle, permissionRationaleText)
            }
        }
    }

    private fun showAlertDialog(title: CharSequence, message: CharSequence) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.speechsdk_proceed) { dialog, _ ->
                dialog.dismiss()
                askForPermission()
            }
            .setNegativeButton(R.string.speechsdk_cancel) { dialog, _ ->
                dialog.dismiss()
                invokeCallback()
            }
            .create()
            .show()
    }

    private fun invokeCallback() {
        callback?.invoke(isPermGranted())
        dismiss()
    }

    private fun isPermGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            requiredPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun askForPermission() {
        permissionLauncher.launch(requiredPermission)
    }

    private fun dismiss() {
        this.finish()
        requiredPermission = ""
        permissionRationaleTitle = ""
        permissionRationaleText = ""
        callback = null
    }

    companion object {
        internal var callback: ((Boolean) -> Unit)? = null
        internal var requiredPermission: String = ""
        internal var permissionRationaleTitle: String = ""
        internal var permissionRationaleText: String = ""

        fun requestMicrophonePermission(
            context: Context,
            permissionRationaleText: String? = null,
            callback: ((Boolean) -> Unit)? = null
        ) {
            val rationaleTitle =
                context.resources.getString(R.string.speechsdk_microphone_permission_required)
            val rationaleText = permissionRationaleText
                ?: context.resources.getString(R.string.speechsdk_microphone_default_rationale_text)
            launch(
                context,
                Manifest.permission.RECORD_AUDIO,
                rationaleTitle,
                rationaleText,
                callback
            )
        }

        private fun launch(
            context: Context,
            requiredPermission: String,
            permissionRationaleTitle: String,
            permissionRationaleText: String,
            callback: ((Boolean) -> Unit)? = null
        ) {
            PermissionActivity.requiredPermission = requiredPermission
            PermissionActivity.permissionRationaleTitle = permissionRationaleTitle
            PermissionActivity.permissionRationaleText = permissionRationaleText
            PermissionActivity.callback = callback

            val intent = Intent(context, PermissionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (context !is Activity) {
                    // NEW TASK is needed if a base context, or application context is passed in instead of an Activity
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}
