package com.amir.winvpn.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amir.winvpn.config.ConfigParser
import com.amir.winvpn.config.ConfigRepository
import com.amir.winvpn.config.ParseResult
import com.amir.winvpn.config.VpnConfig
import com.amir.winvpn.data.AppSettings
import com.amir.winvpn.data.LocalStorage
import com.amir.winvpn.engine.EngineFactory
import com.amir.winvpn.vpn.VpnError
import com.amir.winvpn.vpn.VpnState
import com.amir.winvpn.vpn.VpnStateHolder
import com.amir.winvpn.vpn.WinVpnService
import com.amir.winvpn.subscription.SubscriptionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val storage = LocalStorage(app)
    private val repo = ConfigRepository(storage)

    val configs = repo.configs
    val selectedId = repo.selectedId
    val selected: StateFlow<VpnConfig?> = combine(repo.configs, repo.selectedId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repo.selectedConfig())

    val vpnState = VpnStateHolder.state
    val error = VpnStateHolder.error
    val connectedSince = VpnStateHolder.connectedSince
    val coreAvailable: Boolean get() = EngineFactory.AVAILABLE

    private val _vpnPermissionRequest = MutableStateFlow<Intent?>(null)
    val vpnPermissionRequest: StateFlow<Intent?> = _vpnPermissionRequest.asStateFlow()

    private val _settings = MutableStateFlow(storage.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        // Auto-connect only if the VPN permission was already granted before.
        if (_settings.value.autoConnect && repo.selectedConfig() != null &&
            EngineFactory.AVAILABLE && VpnService.prepare(app) == null
        ) connect()
    }

    /** Returns Invalid/Unsupported when the text is not a runnable configuration. */
    fun addConfig(text: String): ParseResult {
        val result = ConfigParser.parse(text)
        if (result is ParseResult.Valid) repo.add(result)
        return result
    }

    suspend fun importSubscription(url: String): Result<Pair<Int, Int>> = runCatching {
        val result = SubscriptionManager.fetch(url)
        result.configs.forEach { repo.addParsed(it.parsed, it.name) }
        result.configs.size to result.failedLines
    }

    fun select(id: String) = repo.select(id)
    fun rename(id: String, name: String) = repo.rename(id, name)
    fun delete(id: String) = repo.delete(id)
    fun deleteAll() = repo.deleteAll()

    fun reportError(error: VpnError) {
        val s = VpnStateHolder.state.value
        if (s == VpnState.DISCONNECTED || s == VpnState.ERROR) VpnStateHolder.set(VpnState.ERROR, error)
    }

    /** Hands the SELECTED config's id to the service (the service reads the config itself). */
    fun connect() {
        val config = repo.selectedConfig()
        if (config == null) {
            reportError(VpnError.INVALID_CONFIG)
            return
        }
        if (!EngineFactory.AVAILABLE) {
            reportError(VpnError.CORE_MISSING)
            return
        }

        val permissionIntent = VpnService.prepare(app)
        if (permissionIntent != null) {
            _vpnPermissionRequest.value = permissionIntent
            return
        }

        startVpnService(config.id)
    }

    fun onVpnPermissionResult(granted: Boolean) {
        _vpnPermissionRequest.value = null
        if (!granted) {
            reportError(VpnError.PERMISSION_DENIED)
            return
        }
        repo.selectedConfig()?.let { startVpnService(it.id) }
    }

    private fun startVpnService(configId: String) {
        val intent = Intent(app, WinVpnService::class.java)
            .setAction(WinVpnService.ACTION_CONNECT)
            .putExtra(WinVpnService.EXTRA_CONFIG_ID, configId)
        runCatching {
            ContextCompat.startForegroundService(app, intent)
        }.onFailure {
            reportError(VpnError.CONNECTION_FAILED)
        }
    }

    fun disconnect() {
        app.startService(
            Intent(app, WinVpnService::class.java).setAction(WinVpnService.ACTION_DISCONNECT)
        )
    }

    fun setAutoConnect(v: Boolean) = updateSettings { it.copy(autoConnect = v) }
    fun setKeepScreenOn(v: Boolean) = updateSettings { it.copy(keepScreenOn = v) }
    fun setAllowInsecure(v: Boolean) = updateSettings { it.copy(allowInsecure = v) }
    fun setIpv6(v: Boolean) = updateSettings { it.copy(enableIpv6 = v) }
    fun setPreferIpv6(v: Boolean) = updateSettings { it.copy(preferIpv6 = v) }
    fun setAllowLan(v: Boolean) = updateSettings { it.copy(allowLan = v) }
    fun setShowLogs(v: Boolean) = updateSettings { it.copy(showLogs = v) }
    fun setLocalDns(v: Boolean) = updateSettings { it.copy(enableLocalDns = v) }
    fun setFakeDns(v: Boolean) = updateSettings { it.copy(enableFakeDns = v) }
    fun setMux(v: Boolean) = updateSettings { it.copy(mux = v) }
    fun setSocksUdp(v: Boolean) = updateSettings { it.copy(socksUdp = v) }
    fun setDns(v: String) = updateSettings { it.copy(vpnDns = v.trim().ifBlank { "1.1.1.1" }) }
    fun setDomainStrategy(v: String) = updateSettings { it.copy(domainStrategy = v) }
    fun setLanguage(v: String) = updateSettings { it.copy(language = v) }

    private fun updateSettings(f: (AppSettings) -> AppSettings) {
        val new = f(_settings.value)
        _settings.value = new
        storage.saveSettings(new)
    }
}
