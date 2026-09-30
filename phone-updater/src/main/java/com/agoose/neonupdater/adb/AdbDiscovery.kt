package com.agoose.neonupdater.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

enum class EndpointKind { PAIRING, CONNECT }

data class AdbEndpoint(
    val serviceName: String,
    val host: String,
    val port: Int,
    val kind: EndpointKind,
) {
    val id: String get() = "$kind@$serviceName"
}

/**
 * Finds ADB daemons on the local network via mDNS.
 *
 * A watch with Wireless debugging on advertises `_adb-tls-connect._tcp`; while its "Pair new
 * device" screen is open it additionally advertises `_adb-tls-pairing._tcp` on a different port.
 * Services hosted by this phone are filtered out.
 */
class AdbDiscovery(context: Context) {

    private val appContext = context.applicationContext

    fun endpoints(): Flow<List<AdbEndpoint>> = callbackFlow {
        val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = ConcurrentHashMap<String, AdbEndpoint>()
        val resolveLock = Mutex()
        val localAddresses = localAddresses()

        fun publish() {
            trySend(found.values.sortedWith(compareBy({ it.host }, { it.kind })))
        }

        val listeners = SERVICE_TYPES.map { (serviceType, kind) ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(type: String?, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(type: String?, errorCode: Int) = Unit
                override fun onDiscoveryStarted(type: String?) = Unit
                override fun onDiscoveryStopped(type: String?) = Unit

                override fun onServiceFound(info: NsdServiceInfo) {
                    launch {
                        // NsdManager tolerates only one in-flight resolve at a time.
                        val resolved = resolveLock.withLock { resolve(nsd, info) } ?: return@launch
                        @Suppress("DEPRECATION")
                        val host = resolved.host?.hostAddress ?: return@launch
                        if (host in localAddresses) return@launch
                        val endpoint = AdbEndpoint(
                            serviceName = resolved.serviceName ?: host,
                            host = host,
                            port = resolved.port,
                            kind = kind,
                        )
                        found[endpoint.id] = endpoint
                        publish()
                    }
                }

                override fun onServiceLost(info: NsdServiceInfo) {
                    found.remove("$kind@${info.serviceName}")
                    publish()
                }
            }
            nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            listener
        }

        publish()
        awaitClose {
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        }
    }

    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? {
        repeat(RESOLVE_ATTEMPTS) { attempt ->
            val resolved = resolveOnce(nsd, info)
            if (resolved != null) return resolved
            delay(300L * (attempt + 1))
        }
        return null
    }

    private suspend fun resolveOnce(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (continuation.isActive) continuation.resume(serviceInfo)
                }
            })
        }

    private fun localAddresses(): Set<String> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { Collections.list(it.inetAddresses) }
            .mapNotNull { it.hostAddress }
            .toSet()
    }.getOrDefault(emptySet())

    companion object {
        private const val RESOLVE_ATTEMPTS = 3

        private val SERVICE_TYPES = listOf(
            "_adb-tls-pairing._tcp" to EndpointKind.PAIRING,
            "_adb-tls-connect._tcp" to EndpointKind.CONNECT,
        )

        /** The phone's own IPv4 address on the current network, shown as a hint in the UI. */
        fun localIpv4(): String? = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()
                ?.hostAddress
        }.getOrNull()
    }
}
