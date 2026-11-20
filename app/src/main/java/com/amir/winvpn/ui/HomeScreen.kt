package com.amir.winvpn.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.winvpn.ui.theme.WinColors
import com.amir.winvpn.vpn.VpnError
import com.amir.winvpn.vpn.VpnState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenConfigs: (addNew: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    val state by vm.vpnState.collectAsStateWithLifecycle()
    val configs by vm.configs.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val since by vm.connectedSince.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    val view = LocalView.current
    LaunchedEffect(settings.keepScreenOn, state) {
        view.keepScreenOn = settings.keepScreenOn && state == VpnState.CONNECTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (it.resultCode == Activity.RESULT_OK) vm.connect() else vm.reportError(VpnError.PERMISSION_DENIED) }

    fun onConnectClick() {
        if (state == VpnState.DISCONNECTING) return
        if (state == VpnState.CONNECTING || state == VpnState.CONNECTED) { vm.disconnect(); return }
        if (selected == null) { onOpenConfigs(true); return }
        if (!vm.coreAvailable) { vm.reportError(VpnError.CORE_MISSING); return }
        val intent = VpnService.prepare(context)
        if (intent == null) vm.connect() else permissionLauncher.launch(intent)
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (since > 0L) { now = System.currentTimeMillis(); delay(1000) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("WIN VPN", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp, color = WinColors.Accent)
        Spacer(Modifier.height(20.dp))

        val statusColor by animateColorAsState(state.color(), tween(500), label = "status")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(8.dp))
            Text(
                when (state) {
                    VpnState.DISCONNECTED -> "Disconnected"
                    VpnState.CONNECTING -> "Connecting…"
                    VpnState.CONNECTED -> "Connected"
                    VpnState.DISCONNECTING -> "Disconnecting…"
                    VpnState.ERROR -> "Error"
                },
                color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
            )
        }
        if (state == VpnState.CONNECTED && since > 0L) {
            val s = ((now - since) / 1000).coerceAtLeast(0)
            Text("%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60),
                color = WinColors.TextSecondary, fontSize = 13.sp)
        }

        if (state == VpnState.ERROR) {
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it.message, color = WinColors.Error, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(12.dp))
        ConnectButton(state, ::onConnectClick)

        Text(
            selected?.let { "${it.name} · ${it.type.label}" } ?: "No configuration selected",
            color = WinColors.TextSecondary, fontSize = 14.sp
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = WinColors.SurfaceHigh)
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Configurations", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (configs.isNotEmpty()) TextButton(onClick = { onOpenConfigs(false) }) { Text("Manage") }
        }
        Spacer(Modifier.height(8.dp))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            configs.forEach { c ->
                ConfigCard(c, selected = c.id == selectedId, onClick = { vm.select(c.id) })
            }
            OutlinedButton(
                onClick = { onOpenConfigs(true) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Configuration")
            }
        }

        Spacer(Modifier.height(20.dp))
        NavRow(Icons.Default.Settings, "Settings", onOpenSettings)
        Spacer(Modifier.height(10.dp))
        NavRow(Icons.Default.Info, "Subscriptions", onOpenSubscriptions)
        Spacer(Modifier.height(10.dp))
        NavRow(Icons.Default.Info, "About", onOpenAbout)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WinColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = WinColors.Accent)
            Spacer(Modifier.width(14.dp))
            Text(title, fontWeight = FontWeight.Medium)
        }
    }
}

private fun VpnState.color(): Color = when (this) {
    VpnState.DISCONNECTED -> WinColors.Disconnected
    VpnState.CONNECTING -> WinColors.Connecting
    VpnState.CONNECTED -> WinColors.Connected
    VpnState.DISCONNECTING -> WinColors.Connecting
    VpnState.ERROR -> WinColors.Error
}

@Composable
fun ConnectButton(state: VpnState, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p"
    )
    val color by animateColorAsState(
        if (state == VpnState.DISCONNECTED) WinColors.Accent else state.color(),
        tween(500), label = "btn"
    )

    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2
            when (state) {
                VpnState.CONNECTING, VpnState.DISCONNECTING -> {
                    drawCircle(color.copy(alpha = (1f - pulse) * 0.6f), maxR * (0.65f + 0.35f * pulse),
                        style = Stroke(4.dp.toPx()))
                    val p2 = (pulse + 0.5f) % 1f
                    drawCircle(color.copy(alpha = (1f - p2) * 0.3f), maxR * (0.65f + 0.35f * p2),
                        style = Stroke(3.dp.toPx()))
                }
                VpnState.CONNECTED -> {
                    drawCircle(color.copy(alpha = 0.10f), maxR)
                    drawCircle(color.copy(alpha = 0.16f), maxR * 0.82f)
                }
                VpnState.DISCONNECTED, VpnState.ERROR -> drawCircle(color.copy(alpha = 0.08f), maxR * 0.82f)
            }
        }
        Box(
            Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(WinColors.SurfaceHigh, WinColors.Surface)))
                .border(3.dp, color, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when (state) {
                    VpnState.DISCONNECTED -> "CONNECT"
                    VpnState.CONNECTING -> "CANCEL"
                    VpnState.CONNECTED -> "DISCONNECT"
                    VpnState.DISCONNECTING -> "STOPPING"
                    VpnState.ERROR -> "RETRY"
                },
                color = color, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 15.sp
            )
        }
    }
}
