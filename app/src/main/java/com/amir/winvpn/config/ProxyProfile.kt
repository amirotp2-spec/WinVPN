package com.amir.winvpn.config

/**
 * Fully parsed, engine-ready description of one proxy server.
 * [secret] is the UUID (VLESS/VMess) or password (Trojan) and must never be logged,
 * hence the redacting [toString].
 */
data class ProxyProfile(
    val protocol: ConfigType,
    val address: String,
    val port: Int,
    val secret: String,
    val network: String = "tcp",      // tcp | ws | grpc | httpupgrade | xhttp
    val security: String = "none",    // none | tls | reality
    val sni: String? = null,
    val fingerprint: String? = null,
    val alpn: List<String> = emptyList(),
    val publicKey: String? = null,    // REALITY
    val shortId: String? = null,      // REALITY
    val spiderX: String? = null,      // REALITY
    val flow: String? = null,         // VLESS
    val encryption: String? = null,   // VLESS
    val vmessCipher: String? = null,  // VMess
    val path: String? = null,
    val hostHeader: String? = null,
    val serviceName: String? = null,  // gRPC
    val xhttpMode: String? = null
) {
    override fun toString(): String = "ProxyProfile(protocol=$protocol, network=$network, security=$security)"
}
