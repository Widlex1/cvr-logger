package com.rsgd.cvrlogger

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object ExtensionManager {
    const val EXPORT_EXTENSION_PACKAGE = "com.rsgd.cvrlogger.extension.export"
    const val ACTION_EXPORT = "com.rsgd.cvrlogger.ACTION_EXPORT"

    fun isExportExtensionInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(EXPORT_EXTENSION_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getExportIntent(fileName: String, logContent: String): Intent {
        return Intent(ACTION_EXPORT).apply {
            setPackage(EXPORT_EXTENSION_PACKAGE)
            putExtra("FILE_NAME", fileName)
            putExtra("LOG_CONTENT", logContent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
