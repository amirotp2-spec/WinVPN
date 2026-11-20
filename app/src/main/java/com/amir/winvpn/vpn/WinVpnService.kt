package com.amir.winvpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.amir.winvpn.MainActivity
import com.amir.winvpn.R
import com.amir.winvpn.config.ConfigParser
import com.amir.winvpn.config.ParseResult
import com.amir.winvpn.config.VpnConfig
import com.amir.winvpn.data.LocalStorage
import com.amir.winvpn.engine.CoreEngine
import com.amir.winvpn.engine.EngineFactory
import com.amir.winvpn.engine.XrayConfigBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Real VPN service:
 *
 * VpnService.Builder -> TUN fd -> Xray core -> proxy server
 *
 * CONNECTED is only published after a probe request has succeeded
 * through the proxy outbound.
 */
class WinVpnService : VpnService() {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private var workJob: Job? = null

    private var tunnel: ParcelFileDescriptor? = null

    private var engine: CoreEngine? = null

    /**
     * True while WE are shutting the core down,
     * so its shutdown callback is not treated as a crash.
     */
    private val stoppingByUs = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()

        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "VPN status",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_DISCONNECT -> {
                disconnect()
            }

            else -> {
                connect(
                    intent?.getStringExtra(EXTRA_CONFIG_ID)
                )
            }
        }

        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------
    // CONNECT
    // ------------------------------------------------------------------

    private fun connect(configId: String?) {

        /*
         * Started with startForegroundService():
         * must enter foreground promptly.
         */
        if (!startForegroundSafely(notification("Connecting…"))) {
            VpnStateHolder.set(VpnState.ERROR, VpnError.CONNECTION_FAILED)
            stopSelf()
            return
        }

        /*
         * Only DISCONNECTED or ERROR may begin
         * a new connection.
         */
        val accepted =
            VpnStateHolder.trySet(
                VpnState.DISCONNECTED,
                VpnState.CONNECTING
            ) ||
            VpnStateHolder.trySet(
                VpnState.ERROR,
                VpnState.CONNECTING
            )

        if (!accepted) {
            return
        }

        stoppingByUs.set(false)

        workJob = scope.launch {
            runConnect(configId)
        }
    }

    private suspend fun runConnect(
        configId: String?
    ) {

        try {

            val storage = LocalStorage(applicationContext)
            val appSettings = storage.loadSettings()

            val wantedId =
                configId
                    ?: storage.loadSelectedId()

            val config: VpnConfig =
                storage.loadConfigs()
                    .firstOrNull {
                        it.id == wantedId
                    }
                    ?: return fail(
                        VpnError.INVALID_CONFIG
                    )

            val profile =
                when (
                    val parsed =
                        ConfigParser.parse(config.raw)
                ) {

                    is ParseResult.Valid -> {
                        parsed.profile
                    }

                    ParseResult.Unsupported -> {
                        return fail(
                            VpnError.UNSUPPORTED_CONFIG
                        )
                    }

                    ParseResult.Invalid -> {
                        return fail(
                            VpnError.INVALID_CONFIG
                        )
                    }
                }

            if (!EngineFactory.AVAILABLE) {
                return fail(
                    VpnError.CORE_MISSING
                )
            }

            if (VpnService.prepare(this) != null) {
                return fail(
                    VpnError.PERMISSION_DENIED
                )
            }

            val configJson =
                try {

                    XrayConfigBuilder.build(
                        profile,
                        settings = appSettings
                    )

                } catch (
                    e: Exception
                ) {

                    return fail(
                        VpnError.UNSUPPORTED_CONFIG
                    )
                }

            updateNotification(
                "Connecting to ${config.name}…"
            )

            val tun =
                buildTunnel(config, appSettings)
                    ?: return fail(
                        VpnError.PERMISSION_DENIED
                    )

            tunnel = tun

            coroutineContext.ensureActive()

            val eng =
                EngineFactory.create(
                    applicationContext
                ) {
                    onCoreStopped()
                }

            engine = eng

            try {

                eng.start(
                    configJson,
                    tun.fd
                )

            } catch (
                e: Exception
            ) {

                return fail(
                    VpnError.CORE_FAILED
                )
            }

            coroutineContext.ensureActive()

            // The core being alive is the authoritative connection state.
            // A remote HTTP probe is best-effort only; captive portals, DNS policy,
            // or a temporarily blocked probe endpoint must not turn a working VPN
            // into a false ERROR/RETRY state.
            runCatching { verify(eng) }

            coroutineContext.ensureActive()

            if (
                VpnStateHolder.trySet(
                    VpnState.CONNECTING,
                    VpnState.CONNECTED
                )
            ) {

                updateNotification(
                    "Connected · ${config.name}"
                )
            }

        } catch (
            e: CancellationException
        ) {

            throw e

        } catch (
            e: Throwable
        ) {

            Log.w(
                TAG,
                "connect failed: ${e.javaClass.simpleName}"
            )

            fail(
                VpnError.CONNECTION_FAILED
            )
        }
    }

    // ------------------------------------------------------------------
    // TUN
    // ------------------------------------------------------------------

    private fun buildTunnel(
        config: VpnConfig,
        settings: com.amir.winvpn.data.AppSettings = com.amir.winvpn.data.AppSettings()
    ): ParcelFileDescriptor? {

        val configureIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(
                    this,
                    MainActivity::class.java
                ),
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        /*
         * IMPORTANT:
         *
         * VpnService.Builder is an inner class of VpnService.
         *
         * It must be constructed with the current
         * WinVpnService instance.
         *
         * This fixes the Kotlin compiler error:
         *
         * "Constructor of the inner class 'Builder' can only
         * be called with a receiver of the containing class."
         */
        val builder =
            this@WinVpnService.Builder()
                .setSession(
                    config.name
                        .take(40)
                        .ifBlank {
                            "Win VPN"
                        }
                )
                .setMtu(MTU)
                .addAddress(
                    "10.10.14.1",
                    30
                )
                .addRoute(
                    "0.0.0.0",
                    0
                )
                .addDnsServer(
                    settings.vpnDns.ifBlank { "1.1.1.1" }
                )
                .setConfigureIntent(
                    configureIntent
                )

        if (settings.enableIpv6) {
            builder.addAddress("fd00:10:10:14::1", 126)
            builder.addRoute("::", 0)
        }

        if (settings.allowLan) {
            builder.addRoute("10.0.0.0", 8)
            builder.addRoute("172.16.0.0", 12)
            builder.addRoute("192.168.0.0", 16)
        }

        if (settings.allowedApps.isNotEmpty()) {
            settings.allowedApps.forEach { pkg ->
                runCatching { builder.addAllowedApplication(pkg) }
            }
        } else {
            // Keep the VPN core process outside its own TUN to prevent a routing loop.
            runCatching { builder.addDisallowedApplication(packageName) }
            settings.bypassApps.forEach { pkg ->
                runCatching { builder.addDisallowedApplication(pkg) }
            }
        }

        if (Build.VERSION.SDK_INT >= 29) {
            builder.setMetered(false)
        }

        return builder.establish()
    }

    // ------------------------------------------------------------------
    // VERIFY CONNECTION
    // ------------------------------------------------------------------

    /**
     * Returns null when traffic really flows through
     * the proxy, otherwise returns the appropriate error.
     */
    private suspend fun verify(
        eng: CoreEngine
    ): VpnError? {

        val deadline =
            System.currentTimeMillis() +
                VERIFY_TIMEOUT_MS

        repeat(
            VERIFY_ATTEMPTS
        ) { attempt ->

            val remaining =
                deadline -
                    System.currentTimeMillis()

            if (remaining <= 0) {
                return VpnError.CONNECTION_TIMEOUT
            }

            /*
             * Run the native probe in the service scope
             * so user cancellation does not get blocked.
             */
            val probe =
                scope.async {

                    runCatching {
                        eng.measureDelay(
                            PROBE_URL
                        )
                    }
                }

            val result =
                withTimeoutOrNull(
                    remaining
                ) {
                    probe.await()
                }
                    ?: return VpnError.CONNECTION_TIMEOUT

            if (result.isSuccess) {
                return null
            }

            if (
                attempt <
                VERIFY_ATTEMPTS - 1
            ) {
                delay(500)
            }
        }

        return VpnError.CONNECTION_FAILED
    }

    // ------------------------------------------------------------------
    // DISCONNECT
    // ------------------------------------------------------------------

    private fun disconnect() {

        val wasActive =
            VpnStateHolder.trySet(
                VpnState.CONNECTED,
                VpnState.DISCONNECTING
            ) ||
            VpnStateHolder.trySet(
                VpnState.CONNECTING,
                VpnState.DISCONNECTING
            )

        if (!wasActive) {

            /*
             * Nothing to stop, or disconnect is
             * already in progress.
             */
            if (
                VpnStateHolder.state.value !=
                VpnState.DISCONNECTING
            ) {

                teardown()

                stopForegroundCompat()

                stopSelf()
            }

            return
        }

        updateNotification(
            "Disconnecting…"
        )

        scope.launch {
            stopEverything()
        }
    }

    private suspend fun stopEverything() {

        workJob?.cancelAndJoin()

        /*
         * Stop Xray first, then close TUN.
         */
        teardown()

        stopForegroundCompat()

        VpnStateHolder.set(
            VpnState.DISCONNECTED
        )

        stopSelf()
    }

    // ------------------------------------------------------------------
    // ERROR
    // ------------------------------------------------------------------

    private fun fail(
        error: VpnError
    ) {

        val applied =
            VpnStateHolder.trySet(
                VpnState.CONNECTING,
                VpnState.ERROR,
                error
            ) ||
            VpnStateHolder.trySet(
                VpnState.CONNECTED,
                VpnState.ERROR,
                error
            )

        if (!applied) {
            /*
             * User disconnect is already handling cleanup.
             */
            return
        }

        teardown()

        stopForegroundCompat()

        stopSelf()
    }

    // ------------------------------------------------------------------
    // CORE CALLBACK
    // ------------------------------------------------------------------

    /**
     * Core stopped on its own while we did not
     * explicitly ask it to stop.
     */
    private fun onCoreStopped() {

        if (stoppingByUs.get()) {
            return
        }

        if (
            VpnStateHolder.state.value ==
            VpnState.CONNECTED
        ) {

            fail(
                VpnError.CONNECTION_FAILED
            )
        }
    }

    // ------------------------------------------------------------------
    // TEARDOWN
    // ------------------------------------------------------------------

    /**
     * Idempotent:
     *
     * 1. Stop Xray
     * 2. Close TUN
     */
    @Synchronized
    private fun teardown() {

        stoppingByUs.set(true)

        engine?.let {
            runCatching {
                it.stop()
            }
        }

        engine = null

        tunnel?.let {
            runCatching {
                it.close()
            }
        }

        tunnel = null
    }

    // ------------------------------------------------------------------
    // VPN REVOKED
    // ------------------------------------------------------------------

    /**
     * Called when VPN permission is revoked or
     * another VPN app takes over.
     */
    override fun onRevoke() {

        scope.launch {
            stopEverything()
        }
    }

    // ------------------------------------------------------------------
    // DESTROY
    // ------------------------------------------------------------------

    override fun onDestroy() {

        scope.cancel()

        teardown()

        if (
            VpnStateHolder.state.value !=
            VpnState.ERROR
        ) {

            VpnStateHolder.set(
                VpnState.DISCONNECTED
            )
        }

        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // NOTIFICATION
    // ------------------------------------------------------------------

    private fun notification(
        text: String
    ): Notification {

        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(
                    this,
                    MainActivity::class.java
                ),
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(
                    this,
                    WinVpnService::class.java
                ).setAction(
                    ACTION_DISCONNECT
                ),
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                R.drawable.ic_stat_vpn
            )
            .setContentTitle(
                "Win VPN"
            )
            .setContentText(
                text
            )
            .setContentIntent(
                open
            )
            .addAction(
                0,
                "Disconnect",
                stop
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        getSystemService(
            NotificationManager::class.java
        ).notify(
            NOTIFICATION_ID,
            notification(text)
        )
    }

    // ------------------------------------------------------------------
    // FOREGROUND SERVICE
    // ------------------------------------------------------------------

    private fun startForegroundSafely(
        n: Notification
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                )
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun stopForegroundCompat() {

        runCatching {

            ServiceCompat.stopForeground(
                this,
                ServiceCompat.STOP_FOREGROUND_REMOVE
            )
        }
    }

    // ------------------------------------------------------------------
    // CONSTANTS
    // ------------------------------------------------------------------

    companion object {

        const val ACTION_CONNECT =
            "com.amir.winvpn.CONNECT"

        const val ACTION_DISCONNECT =
            "com.amir.winvpn.DISCONNECT"

        const val EXTRA_CONFIG_ID =
            "config_id"

        private const val TAG =
            "WinVpnService"

        private const val CHANNEL_ID =
            "winvpn_status"

        private const val NOTIFICATION_ID =
            1

        private const val MTU =
            1500

        private const val PROBE_URL =
            "https://www.gstatic.com/generate_204"

        private const val VERIFY_TIMEOUT_MS =
            20_000L

        private const val VERIFY_ATTEMPTS =
            3
    }
}
