package com.amir.winvpn.engine

import android.content.Context
import libv2ray.Libv2ray

/** Variant compiled when app/libs/libv2ray.aar is present. */
object EngineFactory {
    const val AVAILABLE = true

    fun create(context: Context, onCoreStopped: () -> Unit): CoreEngine =
        XrayCoreEngine(context.applicationContext, onCoreStopped)

    fun coreVersion(): String? = try { Libv2ray.checkVersionX() } catch (e: Throwable) { null }
}
