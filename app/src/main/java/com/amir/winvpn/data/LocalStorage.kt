package com.amir.winvpn.data

import android.content.Context
import com.amir.winvpn.config.ConfigType
import com.amir.winvpn.config.VpnConfig
import org.json.JSONArray
import org.json.JSONObject

data class AppSettings(
    val autoConnect: Boolean = false,
    val keepScreenOn: Boolean = false,
    val language: String = "en",
    val deviceId: String = "",
    val allowInsecure: Boolean = false,
    val enableLocalProxy: Boolean = false,
    val enableIpv6: Boolean = false,
    val preferIpv6: Boolean = false,
    val allowLan: Boolean = false,
    val showLogs: Boolean = true,
    val enableLocalDns: Boolean = false,
    val enableFakeDns: Boolean = false,
    val vpnDns: String = "1.1.1.1",
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val mux: Boolean = false,
    val socksUdp: Boolean = true,
    val domainStrategy: String = "AsIs",
    val allowedApps: Set<String> = emptySet(),
    val bypassApps: Set<String> = emptySet()
)

class LocalStorage(context: Context) {
    private val prefs = context.getSharedPreferences("winvpn_prefs", Context.MODE_PRIVATE)

    fun loadConfigs(): List<VpnConfig> {
        val s = prefs.getString(KEY_CONFIGS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(s)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                VpnConfig(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    raw = o.getString("raw"),
                    type = runCatching { ConfigType.valueOf(o.getString("type")) }
                        .getOrDefault(ConfigType.UNKNOWN)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveConfigs(list: List<VpnConfig>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("raw", it.raw).put("type", it.type.name)
            )
        }
        prefs.edit().putString(KEY_CONFIGS, arr.toString()).apply()
    }

    fun loadSelectedId(): String? = prefs.getString(KEY_SELECTED, null)
    fun saveSelectedId(id: String?) = prefs.edit().putString(KEY_SELECTED, id).apply()

    fun loadSettings(): AppSettings {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
        return AppSettings(
            autoConnect = prefs.getBoolean(KEY_AUTO, false),
            keepScreenOn = prefs.getBoolean(KEY_SCREEN, false),
            language = prefs.getString(KEY_LANGUAGE, "en") ?: "en",
            deviceId = deviceId,
            allowInsecure = prefs.getBoolean(KEY_ALLOW_INSECURE, false),
            enableLocalProxy = prefs.getBoolean(KEY_LOCAL_PROXY, false),
            enableIpv6 = prefs.getBoolean(KEY_IPV6, false),
            preferIpv6 = prefs.getBoolean(KEY_PREFER_IPV6, false),
            allowLan = prefs.getBoolean(KEY_ALLOW_LAN, false),
            showLogs = prefs.getBoolean(KEY_SHOW_LOGS, true),
            enableLocalDns = prefs.getBoolean(KEY_LOCAL_DNS, false),
            enableFakeDns = prefs.getBoolean(KEY_FAKE_DNS, false),
            vpnDns = prefs.getString(KEY_DNS, "1.1.1.1") ?: "1.1.1.1",
            socksPort = prefs.getInt(KEY_SOCKS_PORT, 10808),
            httpPort = prefs.getInt(KEY_HTTP_PORT, 10809),
            mux = prefs.getBoolean(KEY_MUX, false),
            socksUdp = prefs.getBoolean(KEY_SOCKS_UDP, true),
            domainStrategy = prefs.getString(KEY_DOMAIN_STRATEGY, "AsIs") ?: "AsIs",
            allowedApps = prefs.getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet(),
            bypassApps = prefs.getStringSet(KEY_BYPASS_APPS, emptySet()) ?: emptySet()
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_AUTO, s.autoConnect)
            .putBoolean(KEY_SCREEN, s.keepScreenOn)
            .putString(KEY_LANGUAGE, s.language)
            .putString(KEY_DEVICE_ID, s.deviceId)
            .putBoolean(KEY_ALLOW_INSECURE, s.allowInsecure)
            .putBoolean(KEY_LOCAL_PROXY, s.enableLocalProxy)
            .putBoolean(KEY_IPV6, s.enableIpv6)
            .putBoolean(KEY_PREFER_IPV6, s.preferIpv6)
            .putBoolean(KEY_ALLOW_LAN, s.allowLan)
            .putBoolean(KEY_SHOW_LOGS, s.showLogs)
            .putBoolean(KEY_LOCAL_DNS, s.enableLocalDns)
            .putBoolean(KEY_FAKE_DNS, s.enableFakeDns)
            .putString(KEY_DNS, s.vpnDns)
            .putInt(KEY_SOCKS_PORT, s.socksPort)
            .putInt(KEY_HTTP_PORT, s.httpPort)
            .putBoolean(KEY_MUX, s.mux)
            .putBoolean(KEY_SOCKS_UDP, s.socksUdp)
            .putString(KEY_DOMAIN_STRATEGY, s.domainStrategy)
            .putStringSet(KEY_ALLOWED_APPS, s.allowedApps)
            .putStringSet(KEY_BYPASS_APPS, s.bypassApps)
            .apply()
    }

    private companion object {
        const val KEY_CONFIGS = "configs"
        const val KEY_SELECTED = "selected_id"
        const val KEY_AUTO = "auto_connect"
        const val KEY_SCREEN = "keep_screen_on"
        const val KEY_LANGUAGE = "language"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_ALLOW_INSECURE = "allow_insecure"
        const val KEY_LOCAL_PROXY = "local_proxy"
        const val KEY_IPV6 = "ipv6"
        const val KEY_PREFER_IPV6 = "prefer_ipv6"
        const val KEY_ALLOW_LAN = "allow_lan"
        const val KEY_SHOW_LOGS = "show_logs"
        const val KEY_LOCAL_DNS = "local_dns"
        const val KEY_FAKE_DNS = "fake_dns"
        const val KEY_DNS = "vpn_dns"
        const val KEY_SOCKS_PORT = "socks_port"
        const val KEY_HTTP_PORT = "http_port"
        const val KEY_MUX = "mux"
        const val KEY_SOCKS_UDP = "socks_udp"
        const val KEY_DOMAIN_STRATEGY = "domain_strategy"
        const val KEY_ALLOWED_APPS = "allowed_apps"
        const val KEY_BYPASS_APPS = "bypass_apps"
    }
}
