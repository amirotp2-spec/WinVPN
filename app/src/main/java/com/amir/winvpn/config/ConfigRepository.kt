package com.amir.winvpn.config

import com.amir.winvpn.data.LocalStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class ConfigRepository(private val storage: LocalStorage) {

    private val _configs = MutableStateFlow(storage.loadConfigs())
    val configs: StateFlow<List<VpnConfig>> = _configs.asStateFlow()

    private val _selectedId = MutableStateFlow(storage.loadSelectedId())
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    fun selectedConfig(): VpnConfig? = _configs.value.firstOrNull { it.id == _selectedId.value }

    fun add(valid: ParseResult.Valid): VpnConfig {
        val name = valid.name ?: "Config ${_configs.value.size + 1}"
        val config = VpnConfig(UUID.randomUUID().toString(), name, valid.raw, valid.type)
        updateConfigs(_configs.value + config)
        if (selectedConfig() == null) select(config.id)
        return config
    }

    fun addParsed(valid: ParseResult.Valid, fallbackName: String? = null): VpnConfig {
        val name = valid.name?.takeIf { it.isNotBlank() }
            ?: fallbackName?.takeIf { it.isNotBlank() }
            ?: "Config ${_configs.value.size + 1}"
        val config = VpnConfig(UUID.randomUUID().toString(), name, valid.raw, valid.type)
        updateConfigs((_configs.value + config).distinctBy { it.raw })
        if (selectedConfig() == null) select(config.id)
        return config
    }

    fun rename(id: String, newName: String) {
        val name = newName.trim()
        if (name.isEmpty()) return
        updateConfigs(_configs.value.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun delete(id: String) {
        updateConfigs(_configs.value.filterNot { it.id == id })
        if (_selectedId.value == id) select(_configs.value.firstOrNull()?.id)
    }

    fun deleteAll() {
        updateConfigs(emptyList())
        select(null)
    }

    fun select(id: String?) {
        _selectedId.value = id
        storage.saveSelectedId(id)
    }

    private fun updateConfigs(list: List<VpnConfig>) {
        _configs.value = list
        storage.saveConfigs(list)
    }
}
