package com.amir.winvpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

enum class VpnError(val message: String) {
    INVALID_CONFIG("Invalid configuration"),
    UNSUPPORTED_CONFIG("Unsupported configuration"),
    PERMISSION_DENIED("VPN permission denied"),
    CORE_MISSING("Core failed to start: the VPN core library is not installed in this build"),
    CORE_FAILED("Core failed to start"),
    CONNECTION_FAILED("Connection failed"),
    CONNECTION_TIMEOUT("Connection timeout")
}

/** Process-wide state shared between WinVpnService and the UI. All transitions are atomic. */
object VpnStateHolder {
    private val lock = Any()
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    private val _error = MutableStateFlow<VpnError?>(null)
    private val _connectedSince = MutableStateFlow(0L)

    val state: StateFlow<VpnState> = _state.asStateFlow()
    val error: StateFlow<VpnError?> = _error.asStateFlow()
    val connectedSince: StateFlow<Long> = _connectedSince.asStateFlow()

    fun set(state: VpnState, error: VpnError? = null) {
        synchronized(lock) { apply(state, error) }
    }

    /** Moves to [state] only if the current state is [expected]. */
    fun trySet(expected: VpnState, state: VpnState, error: VpnError? = null): Boolean =
        synchronized(lock) {
            if (_state.value != expected) {
                false
            } else {
                apply(state, error)
                true
            }
        }

    private fun apply(state: VpnState, error: VpnError?) {
        _error.value = if (state == VpnState.ERROR) error else null
        _connectedSince.value = if (state == VpnState.CONNECTED) System.currentTimeMillis() else 0L
        _state.value = state
    }
}
