package dev.blazelight.sdflasher

import android.app.Application
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FlashApplication : Application() {

    companion object {
        init {
            // Configure libsu Shell before it's created
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(10)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Optionally pre-warm the root shell in background
        // This helps avoid delays when first root command is needed
        Shell.getShell { shell ->
            if (BuildConfig.DEBUG) {
                val status = if (shell.isRoot) "Root granted" else "Root denied"
                android.util.Log.d("FlashApplication", "Shell status: $status")
            }
        }
    }
}
