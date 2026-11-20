package com.amir.winvpn.engine

import android.content.Context
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * Real Xray engine backed by AndroidLibXrayLite.
 *
 * Flow:
 *
 * Android VpnService
 *        ↓
 * TUN file descriptor
 *        ↓
 * CoreController.startLoop(config, tunFd)
 *        ↓
 * Xray
 *        ↓
 * VLESS / VMess / Trojan
 */
internal class XrayCoreEngine(
    context: Context,
    private val onStopped: () -> Unit
) : CoreEngine {

    companion object {
        private const val TAG = "XrayCoreEngine"
    }

    private val handler =
        object : CoreCallbackHandler {

            override fun startup(): Long {

                Log.i(
                    TAG,
                    "Xray startup callback received"
                )

                return 0L
            }

            override fun shutdown(): Long {

                Log.i(
                    TAG,
                    "Xray shutdown callback received"
                )

                runCatching {
                    onStopped()
                }.onFailure {
                    Log.w(
                        TAG,
                        "onStopped callback failed",
                        it
                    )
                }

                return 0L
            }

            override fun onEmitStatus(
                code: Long,
                message: String?
            ): Long {

                /*
                 * Do not log configuration content.
                 * Only log the status code/message generated
                 * by the native core.
                 */
                Log.d(
                    TAG,
                    "Xray status code=$code message=${message ?: ""}"
                )

                return 0L
            }
        }

    private val controller: CoreController

    init {

        try {

            /*
             * AndroidLibXrayLite uses this directory for its
             * runtime environment and certificates/assets.
             */
            Libv2ray.initCoreEnv(
                context.filesDir.absolutePath,
                ""
            )

            controller =
                Libv2ray.newCoreController(
                    handler
                )

            Log.i(
                TAG,
                "AndroidLibXrayLite initialized: ${
                    runCatching {
                        Libv2ray.checkVersionX()
                    }.getOrNull() ?: "unknown"
                }"
            )

        } catch (e: Throwable) {

            Log.e(
                TAG,
                "Failed to initialize Xray core",
                e
            )

            throw CoreStartException(
                "Failed to initialize Xray core",
                e
            )
        }
    }

    override fun start(
        configJson: String,
        tunFd: Int
    ) {

        if (configJson.isBlank()) {

            throw CoreStartException(
                "Xray configuration is empty"
            )
        }

        if (tunFd < 0) {

            throw CoreStartException(
                "Invalid TUN file descriptor: $tunFd"
            )
        }

        Log.i(
            TAG,
            "Starting Xray. tunFd=$tunFd configLength=${configJson.length}"
        )

        try {

            /*
             * AndroidLibXrayLite passes the TUN descriptor
             * to Xray through XRAY_TUN_FD.
             */
            controller.startLoop(
                configJson,
                tunFd
            )

        } catch (e: Throwable) {

            Log.e(
                TAG,
                "Xray startLoop failed",
                e
            )

            throw CoreStartException(
                "Core failed to start: ${
                    e.message ?: "unknown error"
                }",
                e
            )
        }

        /*
         * startLoop() returns after the core has been
         * initialized. Verify the native running state.
         */
        if (!controller.getIsRunning()) {

            throw CoreStartException(
                "Core is not running after startLoop"
            )
        }

        Log.i(
            TAG,
            "Xray core is running"
        )
    }

    override fun stop() {

        Log.i(
            TAG,
            "Stopping Xray core"
        )

        try {

            if (controller.getIsRunning()) {
                controller.stopLoop()
            }

        } catch (e: Throwable) {

            /*
             * Stop is intentionally best-effort.
             * The VPN service will close its own TUN fd.
             */
            Log.w(
                TAG,
                "Xray stopLoop failed",
                e
            )
        }
    }

    override fun isRunning(): Boolean {

        return try {

            controller.getIsRunning()

        } catch (e: Throwable) {

            Log.w(
                TAG,
                "Unable to query Xray running state",
                e
            )

            false
        }
    }

    override fun measureDelay(
        url: String
    ): Long {

        if (url.isBlank()) {

            throw IllegalArgumentException(
                "Probe URL is empty"
            )
        }

        if (!isRunning()) {

            throw IllegalStateException(
                "Xray core is not running"
            )
        }

        Log.d(
            TAG,
            "Running proxy connectivity probe"
        )

        return try {

            val delay =
                controller.measureDelay(
                    url
                )

            Log.i(
                TAG,
                "Proxy connectivity probe succeeded: ${delay}ms"
            )

            delay

        } catch (e: Throwable) {

            /*
             * This is the important part for debugging.
             *
             * The previous implementation swallowed the
             * reason until WinVpnService converted it into
             * a generic Retry.
             */
            Log.e(
                TAG,
                "Proxy connectivity probe failed",
                e
            )

            throw e
        }
    }
}
