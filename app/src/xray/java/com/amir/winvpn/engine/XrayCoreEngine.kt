package com.amir.winvpn.engine

import android.content.Context
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

internal class XrayCoreEngine(
    context: Context,
    private val onStopped: () -> Unit
) : CoreEngine {

    private val handler = object : CoreCallbackHandler {

        override fun startup(): Long {
            return 0L
        }

        override fun shutdown(): Long {
            onStopped()
            return 0L
        }

        override fun onEmitStatus(
            code: Long,
            message: String?
        ): Long {
            return 0L
        }
    }

    private val controller: CoreController

    init {
        try {
            Libv2ray.initCoreEnv(
                context.filesDir.absolutePath,
                ""
            )

            controller = Libv2ray.newCoreController(handler)

        } catch (e: Throwable) {

            val detail = buildErrorMessage(
                "Core initialization failed",
                e
            )

            throw CoreStartException(detail, e)
        }
    }

    override fun start(
        configJson: String,
        tunFd: Int
    ) {
        try {

            if (configJson.isBlank()) {
                throw CoreStartException(
                    "Core failed to start: empty Xray configuration"
                )
            }

            if (tunFd < 0) {
                throw CoreStartException(
                    "Core failed to start: invalid TUN file descriptor: $tunFd"
                )
            }

            controller.startLoop(
                configJson,
                tunFd
            )

        } catch (e: Throwable) {

            val detail = buildString {
                append("Core failed to start")

                val message = e.message
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

                if (message != null) {
                    append(": ")
                    append(message)
                }

                val cause = e.cause

                if (
                    cause != null &&
                    cause !== e &&
                    !cause.message.isNullOrBlank()
                ) {
                    append(" | Cause: ")
                    append(cause.message)
                }
            }

            throw CoreStartException(
                detail,
                e
            )
        }

        try {

            if (!controller.getIsRunning()) {

                throw CoreStartException(
                    "Core failed to start: Xray controller is not running after startLoop()"
                )
            }

        } catch (e: CoreStartException) {
            throw e

        } catch (e: Throwable) {

            throw CoreStartException(
                buildErrorMessage(
                    "Core status check failed",
                    e
                ),
                e
            )
        }
    }

    override fun stop() {
        try {
            controller.stopLoop()
        } catch (_: Throwable) {
            // Ignore shutdown errors.
        }
    }

    override fun isRunning(): Boolean {
        return try {
            controller.getIsRunning()
        } catch (_: Throwable) {
            false
        }
    }

    override fun measureDelay(url: String): Long {
        return try {
            controller.measureDelay(url)
        } catch (e: Throwable) {
            throw CoreStartException(
                buildErrorMessage(
                    "Core delay test failed",
                    e
                ),
                e
            )
        }
    }

    private fun buildErrorMessage(
        prefix: String,
        error: Throwable
    ): String {

        val message = error.message
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val causeMessage = error.cause
            ?.message
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return buildString {

            append(prefix)

            if (message != null) {
                append(": ")
                append(message)
            }

            if (
                causeMessage != null &&
                causeMessage != message
            ) {
                append(" | Cause: ")
                append(causeMessage)
            }

            if (message == null && causeMessage == null) {
                append(": ")
                append(error.javaClass.name)
            }
        }
    }
}
