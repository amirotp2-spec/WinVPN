package com.amir.winvpn.config

enum class ConfigType(val label: String) {
    VLESS("VLESS"), VMESS("VMess"), TROJAN("Trojan"), SHADOWSOCKS("Shadowsocks"), SOCKS5("SOCKS5"), HTTP("HTTP"), UNKNOWN("Unknown")
}

data class VpnConfig(
    val id: String,
    val name: String,
    val raw: String,
    val type: ConfigType
) {
    // raw contains credentials: keep it out of accidental logs
    override fun toString(): String = "VpnConfig(id=$id, name=$name, type=$type)"
}

sealed interface ParseResult {
    data class Valid(
        val type: ConfigType,
        val name: String?,
        val raw: String,
        val profile: ProxyProfile
    ) : ParseResult {
        override fun toString(): String = "Valid(type=$type, name=$name)"
    }

    /** Well-formed link, but uses a transport/security option this client cannot run. */
    data object Unsupported : ParseResult

    data object Invalid : ParseResult
}
