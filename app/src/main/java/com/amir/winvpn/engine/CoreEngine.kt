package com.amir.winvpn.engine

/** A proxy core that consumes a TUN file descriptor and forwards its traffic. */
interface CoreEngine {
    /** Starts the core on [tunFd]. Throws [CoreStartException] on failure. */
    fun start(configJson: String, tunFd: Int)

    /** Stops the core. Safe to call more than once. */
    fun stop()

    fun isRunning(): Boolean

    /** Fetches [url] THROUGH the proxy outbound; returns latency in ms or throws. */
    fun measureDelay(url: String): Long
}

class CoreStartException(message: String, cause: Throwable? = null) : Exception(message, cause)
