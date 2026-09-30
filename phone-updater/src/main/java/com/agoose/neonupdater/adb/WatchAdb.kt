package com.agoose.neonupdater.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/**
 * App-wide ADB connection manager. One instance, one key pair, one live connection at a time.
 */
class WatchAdb private constructor(
    private val identity: AdbKeys.Identity,
) : AbsAdbConnectionManager() {

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(20, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = identity.privateKey

    override fun getCertificate(): Certificate = identity.certificate

    override fun getDeviceName(): String = "NeonFace Updater (${Build.MODEL})"

    companion object {
        @Volatile
        private var instance: WatchAdb? = null

        /** Blocking: generates the key pair on first call. Never call from the main thread. */
        fun get(context: Context): WatchAdb {
            return instance ?: synchronized(this) {
                instance ?: WatchAdb(AdbKeys.loadOrCreate(context.applicationContext.filesDir))
                    .also { instance = it }
            }
        }
    }
}
