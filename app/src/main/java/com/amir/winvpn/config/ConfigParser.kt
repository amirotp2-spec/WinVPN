package com.amir.winvpn.config

import android.util.Base64 as AndroidBase64
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

object ConfigParser {
    private class Fail(val unsupported: Boolean) : RuntimeException()
    private fun invalid() = Fail(false)
    private fun unsupported() = Fail(true)

    fun detectType(input: String): ConfigType {
        val t = input.trim().lowercase()
        return when {
            t.startsWith("vless://") -> ConfigType.VLESS
            t.startsWith("vmess://") -> ConfigType.VMESS
            t.startsWith("trojan://") -> ConfigType.TROJAN
            t.startsWith("ss://") -> ConfigType.SHADOWSOCKS
            t.startsWith("socks://") || t.startsWith("socks5://") -> ConfigType.SOCKS5
            t.startsWith("http://") || t.startsWith("http-proxy://") -> ConfigType.HTTP
            else -> ConfigType.UNKNOWN
        }
    }

    fun parse(input: String): ParseResult {
        val raw = input.trim()
        if (raw.isEmpty() || raw.any { it.isWhitespace() }) return ParseResult.Invalid
        return try {
            when (detectType(raw)) {
                ConfigType.VLESS -> parseUri(raw, ConfigType.VLESS)
                ConfigType.TROJAN -> parseUri(raw, ConfigType.TROJAN)
                ConfigType.VMESS -> parseVmess(raw)
                ConfigType.SHADOWSOCKS -> parseShadowsocks(raw)
                ConfigType.SOCKS5 -> parseSimpleProxy(raw, ConfigType.SOCKS5)
                ConfigType.HTTP -> parseSimpleProxy(raw, ConfigType.HTTP)
                ConfigType.UNKNOWN -> ParseResult.Invalid
            }
        } catch (f: Fail) {
            if (f.unsupported) ParseResult.Unsupported else ParseResult.Invalid
        } catch (_: Throwable) {
            ParseResult.Invalid
        }
    }

    private fun parseUri(raw: String, type: ConfigType): ParseResult {
        val body = raw.substringAfter("://")
        val name = body.substringAfter('#', "").takeIf { it.isNotBlank() }?.let(::decode)
        val noFragment = body.substringBefore('#')
        val params = parseQuery(noFragment.substringAfter('?', ""))
        val authority = noFragment.substringBefore('?').substringBefore('/')
        if (!authority.contains('@')) throw invalid()
        val secret = decode(authority.substringBeforeLast('@'))
        if (secret.isBlank()) throw invalid()
        val (host, port) = splitHostPort(authority.substringAfterLast('@'))
        val network = normalizeNetwork(params["type"])
        val security = normalizeSecurity(params["security"], if (type == ConfigType.TROJAN) "tls" else "none")
        val headerType = params["headertype"]
        if (network == "tcp" && !headerType.isNullOrEmpty() && headerType != "none") throw unsupported()

        val profile = ProxyProfile(
            protocol = type, address = host, port = port, secret = secret,
            network = network, security = security,
            sni = params["sni"].blankToNull(), fingerprint = params["fp"].blankToNull(),
            alpn = splitList(params["alpn"]), publicKey = params["pbk"].blankToNull(),
            shortId = params["sid"].blankToNull(), spiderX = params["spx"].blankToNull(),
            flow = if (type == ConfigType.VLESS) params["flow"].blankToNull() else null,
            encryption = if (type == ConfigType.VLESS) params["encryption"].blankToNull() else null,
            path = params["path"].blankToNull(), hostHeader = params["host"].blankToNull(),
            serviceName = params["servicename"].blankToNull(), xhttpMode = params["mode"].blankToNull()
        )
        if (profile.security == "reality" && profile.publicKey == null) throw invalid()
        return ParseResult.Valid(type, name, raw, profile)
    }

    private fun parseVmess(raw: String): ParseResult {
        val bytes = decodeBase64(raw.substringAfter("://")) ?: throw invalid()
        val json = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrElse { throw invalid() }
        val address = json.optString("add")
        val id = json.optString("id")
        val port = json.optString("port").toIntOrNull()
        if (address.isBlank() || id.isBlank() || port !in 1..65535) throw invalid()
        val network = normalizeNetwork(json.optString("net"))
        val header = json.optString("type")
        if (network == "tcp" && header.isNotEmpty() && header != "none") throw unsupported()
        val security = normalizeSecurity(json.optString("tls"), "none")
        val path = json.optString("path").blankToNull()
        val profile = ProxyProfile(
            protocol = ConfigType.VMESS, address = address, port = port, secret = id,
            network = network, security = security, sni = json.optString("sni").blankToNull(),
            fingerprint = json.optString("fp").blankToNull(), alpn = splitList(json.optString("alpn")),
            vmessCipher = json.optString("scy").blankToNull() ?: "auto",
            path = if (network == "grpc") null else path, hostHeader = json.optString("host").blankToNull(),
            serviceName = if (network == "grpc") path else null
        )
        return ParseResult.Valid(ConfigType.VMESS, json.optString("ps").blankToNull(), raw, profile)
    }

    private fun parseShadowsocks(raw: String): ParseResult {
        val body = raw.substringAfter("://").substringBefore('#')
        val name = raw.substringAfter('#', "").takeIf { it.isNotBlank() }?.let(::decode)
        val decoded = decodeBase64(body.substringBefore('?'))
        val authority = if (decoded != null && String(decoded, Charsets.UTF_8).contains('@')) String(decoded, Charsets.UTF_8)
                        else body.substringBefore('?')
        val at = authority.lastIndexOf('@')
        if (at <= 0) throw invalid()
        val userInfo = authority.substring(0, at)
        val hostPort = authority.substring(at + 1)
        val colon = userInfo.indexOf(':')
        if (colon <= 0) throw invalid()
        val method = userInfo.substring(0, colon)
        val password = userInfo.substring(colon + 1)
        val (host, port) = splitHostPort(hostPort)
        return ParseResult.Valid(ConfigType.SHADOWSOCKS, name, raw,
            ProxyProfile(ConfigType.SHADOWSOCKS, host, port, password, encryption = method))
    }

    private fun parseSimpleProxy(raw: String, type: ConfigType): ParseResult {
        val uri = raw.substringBefore('#')
        val body = uri.substringAfter("://")
        val userInfo = body.substringBeforeLast('@', "")
        val hostPort = body.substringAfterLast('@')
        val (host, port) = splitHostPort(hostPort)
        val secret = userInfo.substringAfter(':', "")
        val name = raw.substringAfter('#', "").takeIf { it.isNotBlank() }?.let(::decode)
        return ParseResult.Valid(type, name, raw,
            ProxyProfile(type, host, port, secret.ifBlank { "" }))
    }

    private fun normalizeNetwork(value: String?): String = when (value?.lowercase().orEmpty()) {
        "", "tcp", "raw" -> "tcp"
        "ws", "websocket" -> "ws"
        "grpc" -> "grpc"
        "httpupgrade" -> "httpupgrade"
        "xhttp", "splithttp" -> "xhttp"
        else -> throw unsupported()
    }

    private fun normalizeSecurity(value: String?, default: String): String = when (value?.lowercase().orEmpty()) {
        "" -> default
        "none" -> "none"
        "tls" -> "tls"
        "reality" -> "reality"
        else -> throw unsupported()
    }

    private fun splitHostPort(hostPort: String): Pair<String, Int> {
        val hp = hostPort.trim()
        val host: String
        val portStr: String
        if (hp.startsWith("[")) {
            val end = hp.indexOf(']')
            if (end < 0) throw invalid()
            host = hp.substring(1, end)
            portStr = hp.substring(end + 1).removePrefix(":")
        } else {
            host = hp.substringBeforeLast(':', "")
            portStr = hp.substringAfterLast(':', "")
        }
        val port = portStr.toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) throw invalid()
        return host to port
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').filter { it.isNotEmpty() }.associate {
            decode(it.substringBefore('=')).lowercase() to decode(it.substringAfter('=', ""))
        }

    private fun splitList(v: String?): List<String> =
        v?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }

    private fun decodeBase64(s: String): ByteArray? {
        val normalized = s.trim().replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) }
        return runCatching { Base64.getDecoder().decode(normalized) }.getOrNull()
            ?: runCatching { AndroidBase64.decode(s, AndroidBase64.DEFAULT) }.getOrNull()
    }

    private fun decode(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
