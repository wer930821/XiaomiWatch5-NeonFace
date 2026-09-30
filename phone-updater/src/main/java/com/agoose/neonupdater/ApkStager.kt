package com.agoose.neonupdater

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

data class StagedApk(
    val path: String,
    val fileName: String,
    val label: String,
    val packageName: String,
    val version: String,
    val size: Long,
    val isWearApk: Boolean,
) {
    val sizeText: String
        get() = when {
            size >= 1_000_000 -> String.format("%.1f MB", size / 1_000_000.0)
            size >= 1_000 -> String.format("%.0f kB", size / 1_000.0)
            else -> "$size B"
        }
}

object ApkStager {
    fun stage(context: Context, uri: Uri): StagedApk {
        val target = File(context.cacheDir, "staged.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected file")

        var fileName = "app.apk"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) fileName = cursor.getString(0)
            }
        return parseStaged(context, target, fileName)
    }

    fun stageFile(context: Context, file: File, fileName: String): StagedApk {
        val target = File(context.cacheDir, "staged.apk")
        file.inputStream().use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return parseStaged(context, target, fileName)
    }

    private fun parseStaged(context: Context, target: File, fileName: String): StagedApk {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(
            target.absolutePath,
            android.content.pm.PackageManager.GET_CONFIGURATIONS
        )
        var label = fileName
        var packageName = "—"
        var version = "—"
        var isWear = false

        if (info != null) {
            packageName = info.packageName ?: packageName
            @Suppress("DEPRECATION")
            version = "${info.versionName ?: "?"} (${info.versionCode})"
            info.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = target.absolutePath
                appInfo.publicSourceDir = target.absolutePath
                runCatching { label = pm.getApplicationLabel(appInfo).toString() }
            }
            isWear = info.reqFeatures?.any { it.name == "android.hardware.type.watch" } == true
        }

        return StagedApk(
            path = target.absolutePath,
            fileName = fileName,
            label = label,
            packageName = packageName,
            version = version,
            size = target.length(),
            isWearApk = isWear,
        )
    }
}