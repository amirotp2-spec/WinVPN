package com.amir.winvpn.engine

import com.amir.winvpn.config.ConfigType
import com.amir.winvpn.config.ProxyProfile
import com.amir.winvpn.data.AppSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds an Xray configuration for the Android VPN service.
 *
 * Architecture:
 *
 * Android VpnService
 *        ↓
 * Android TUN file descriptor
 *        ↓
 * AndroidLibXrayLite / Xray
 *        ↓
 * VLESS / VMess / Trojan outbound
 *
 * The TUN file descriptor is supplied to Xray by the native
 * AndroidLibXrayLite layer.
 */
object XrayConfigBuilder {

    const val TAG_PROXY = "proxy"
    const val TAG_TUN = "tun-in"
    const val TAG_DIRECT = "direct"
    const val TAG_BLOCK = "block"

    fun build(
        p: ProxyProfile,
        mtu: Int = 1500,
        settings: AppSettings = AppSettings()
    ): String {

        require(p.protocol != ConfigType.UNKNOWN) {
            "Unsupported proxy protocol"
        }

        require(p.address.isNotBlank()) {
            "Proxy address is empty"
        }

        require(p.port in 1..65535) {
            "Invalid proxy port: ${p.port}"
        }

        require(p.secret.isNotBlank()) {
            "Proxy credential is empty"
        }

        val root = JSONObject()

        /*
         * Keep Xray logging enabled at warning level.
         * This is useful for diagnosing connection failures
         * without producing excessive logs.
         */
        root.put(
            "log",
            JSONObject()
                .put("loglevel", "warning")
                .put("access", "none")
        )

        /*
         * Android TUN inbound.
         *
         * The actual Android TUN file descriptor is supplied
         * by AndroidLibXrayLite.
         */
        root.put(
            "inbounds",
            JSONArray().put(
                JSONObject()
                    .put("tag", TAG_TUN)
                    .put("protocol", "tun")
                    .put(
                        "settings",
                        JSONObject()
                            .put("name", "xray0")
                            .put("mtu", mtu)
                            .put(
                                "gateway",
                                JSONArray()
                                    .put("10.10.14.1/30")
                                    .put("fd00:10:10:14::1/126")
                            )
                    )
            )
        )

        /*
         * First outbound is always the selected proxy.
         *
         * Android traffic entering the TUN therefore goes:
         *
         * TUN → proxy → VLESS/VMess/Trojan server
         */
        root.put(
            "outbounds",
            JSONArray()
                .put(proxyOutbound(p, settings))
                .put(
                    JSONObject()
                        .put("tag", TAG_DIRECT)
                        .put("protocol", "freedom")
                )
                .put(
                    JSONObject()
                        .put("tag", TAG_BLOCK)
                        .put("protocol", "blackhole")
                )
        )

        /*
         * Route everything entering the TUN to the proxy.
         *
         * This is important because simply creating a TUN inbound
         * does not automatically mean every packet is sent to
         * the selected outbound.
         */
        root.put(
            "routing",
            JSONObject()
                .put("domainStrategy", settings.domainStrategy)
                .put(
                    "rules",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "field")
                            .put(
                                "inboundTag",
                                JSONArray().put(TAG_TUN)
                            )
                            .put("outboundTag", TAG_PROXY)
                    )
                )
        )

        return root.toString()
    }

    private fun proxyOutbound(
        p: ProxyProfile,
        settings: AppSettings
    ): JSONObject {

        val settings =
            when (p.protocol) {

                ConfigType.VLESS -> {

                    JSONObject()
                        .put(
                            "vnext",
                            JSONArray().put(
                                JSONObject()
                                    .put(
                                        "address",
                                        cleanAddress(p.address)
                                    )
                                    .put(
                                        "port",
                                        p.port
                                    )
                                    .put(
                                        "users",
                                        JSONArray().put(
                                            JSONObject()
                                                .put(
                                                    "id",
                                                    p.secret
                                                )
                                                .put(
                                                    "encryption",
                                                    p.encryption
                                                        ?.ifBlank { "none" }
                                                        ?: "none"
                                                )
                                                .apply {
                                                    p.flow
                                                        ?.takeIf {
                                                            it.isNotBlank()
                                                        }
                                                        ?.let {
                                                            put(
                                                                "flow",
                                                                it
                                                            )
                                                        }
                                                }
                                        )
                                    )
                            )
                        )
                }

                ConfigType.VMESS -> {

                    JSONObject()
                        .put(
                            "vnext",
                            JSONArray().put(
                                JSONObject()
                                    .put(
                                        "address",
                                        cleanAddress(p.address)
                                    )
                                    .put(
                                        "port",
                                        p.port
                                    )
                                    .put(
                                        "users",
                                        JSONArray().put(
                                            JSONObject()
                                                .put(
                                                    "id",
                                                    p.secret
                                                )
                                                .put(
                                                    "security",
                                                    p.vmessCipher
                                                        ?.ifBlank { "auto" }
                                                        ?: "auto"
                                                )
                                        )
                                    )
                            )
                        )
                }

                ConfigType.TROJAN -> {

                    JSONObject()
                        .put(
                            "servers",
                            JSONArray().put(
                                JSONObject()
                                    .put(
                                        "address",
                                        cleanAddress(p.address)
                                    )
                                    .put(
                                        "port",
                                        p.port
                                    )
                                    .put(
                                        "password",
                                        p.secret
                                    )
                            )
                        )
                }

                ConfigType.SHADOWSOCKS -> {
                    JSONObject().put(
                        "servers", JSONArray().put(
                            JSONObject()
                                .put("address", cleanAddress(p.address))
                                .put("port", p.port)
                                .put("method", p.encryption?.ifBlank { "aes-128-gcm" } ?: "aes-128-gcm")
                                .put("password", p.secret)
                        )
                    )
                }

                ConfigType.SOCKS5 -> {
                    JSONObject().apply {
                        put("servers", JSONArray().put(JSONObject().apply {
                            put("address", cleanAddress(p.address))
                            put("port", p.port)
                            if (p.secret.isNotBlank()) {
                                put("users", JSONArray().put(JSONObject().put("user", "").put("pass", p.secret)))
                            }
                        }))
                    }
                }

                ConfigType.HTTP -> {
                    JSONObject().apply {
                        put("servers", JSONArray().put(JSONObject().apply {
                            put("address", cleanAddress(p.address))
                            put("port", p.port)
                            if (p.secret.isNotBlank()) {
                                put("users", JSONArray().put(JSONObject().put("user", "").put("pass", p.secret)))
                            }
                        }))
                    }
                }

                ConfigType.UNKNOWN -> {
                    throw IllegalArgumentException(
                        "Unsupported protocol"
                    )
                }
            }

        return JSONObject()
            .put(
                "tag",
                TAG_PROXY
            )
            .put(
                "protocol",
                p.protocol.name.lowercase()
            )
            .put(
                "settings",
                settings
            )
            .put(
                "streamSettings",
                streamSettings(p)
            )
    }

    private fun streamSettings(
        p: ProxyProfile
    ): JSONObject {

        val network =
            p.network
                .trim()
                .lowercase()

        val security =
            p.security
                .trim()
                .lowercase()

        val stream =
            JSONObject()
                .put(
                    "network",
                    network
                )
                .put(
                    "security",
                    if (security.isBlank()) {
                        "none"
                    } else {
                        security
                    }
                )

        when (network) {

            "ws" -> {

                val ws =
                    JSONObject()
                        .put(
                            "path",
                            normalizePath(
                                p.path
                            )
                        )

                p.hostHeader
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let {

                        ws.put(
                            "headers",
                            JSONObject()
                                .put(
                                    "Host",
                                    it
                                )
                        )
                    }

                stream.put(
                    "wsSettings",
                    ws
                )
            }

            "grpc" -> {

                val grpc =
                    JSONObject()
                        .put(
                            "serviceName",
                            p.serviceName
                                ?.trim()
                                ?: ""
                        )

                p.hostHeader
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let {

                        grpc.put(
                            "authority",
                            it
                        )
                    }

                stream.put(
                    "grpcSettings",
                    grpc
                )
            }

            "httpupgrade" -> {

                val httpUpgrade =
                    JSONObject()
                        .put(
                            "path",
                            normalizePath(
                                p.path
                            )
                        )

                p.hostHeader
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let {

                        httpUpgrade.put(
                            "host",
                            it
                        )
                    }

                stream.put(
                    "httpupgradeSettings",
                    httpUpgrade
                )
            }

            "xhttp" -> {

                val xhttp =
                    JSONObject()
                        .put(
                            "path",
                            normalizePath(
                                p.path
                            )
                        )

                p.hostHeader
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let {

                        xhttp.put(
                            "host",
                            it
                        )
                    }

                p.xhttpMode
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let {

                        xhttp.put(
                            "mode",
                            it
                        )
                    }

                stream.put(
                    "xhttpSettings",
                    xhttp
                )
            }
        }

        when (security) {

            "tls" -> {

                val tls =
                    JSONObject()

                (
                    p.sni
                        ?.trim()
                        ?.takeIf {
                            it.isNotEmpty()
                        }
                        ?: p.hostHeader
                            ?.trim()
                            ?.takeIf {
                                it.isNotEmpty()
                            }
                )?.let {

                    tls.put(
                        "serverName",
                        it
                    )
                }

                p.fingerprint
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let {

                        tls.put(
                            "fingerprint",
                            it
                        )
                    }

                if (p.alpn.isNotEmpty()) {

                    tls.put(
                        "alpn",
                        JSONArray(
                            p.alpn
                        )
                    )
                }

                stream.put(
                    "tlsSettings",
                    tls
                )
            }

            "reality" -> {

                val reality =
                    JSONObject()

                (
                    p.sni
                        ?.trim()
                        ?.takeIf {
                            it.isNotEmpty()
                        }
                        ?: p.hostHeader
                            ?.trim()
                            ?.takeIf {
                                it.isNotEmpty()
                            }
                )?.let {

                    reality.put(
                        "serverName",
                        it
                    )
                }

                reality.put(
                    "fingerprint",
                    p.fingerprint
                        ?.trim()
                        ?.takeIf {
                            it.isNotEmpty()
                        }
                        ?: "chrome"
                )

                p.publicKey
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let {

                        reality.put(
                            "publicKey",
                            it
                        )
                    }

                p.shortId
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let {

                        reality.put(
                            "shortId",
                            it
                        )
                    }

                p.spiderX
                    ?.let {

                        reality.put(
                            "spiderX",
                            it
                        )
                    }

                stream.put(
                    "realitySettings",
                    reality
                )
            }
        }

        return stream
    }

    /**
     * URI parsers may keep IPv6 brackets around an address.
     *
     * Xray's address field must contain the host itself,
     * not the URI's surrounding brackets.
     *
     * Example:
     *
     * [2606:4700:3037::6815:4842]
     *
     * becomes:
     *
     * 2606:4700:3037::6815:4842
     */
    private fun cleanAddress(
        address: String
    ): String {

        val value =
            address.trim()

        return if (
            value.length >= 2 &&
            value.first() == '[' &&
            value.last() == ']'
        ) {
            value.substring(
                1,
                value.length - 1
            )
        } else {
            value
        }
    }

    private fun normalizePath(
        path: String?
    ): String {

        val value =
            path
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?: "/"

        return if (
            value.startsWith("/")
        ) {
            value
        } else {
            "/$value"
        }
    }
}
