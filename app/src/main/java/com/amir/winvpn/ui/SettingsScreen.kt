package com.amir.winvpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.winvpn.ui.theme.WinColors

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    ScreenScaffold("Settings", onBack) {
        Text("General", style = MaterialTheme.typography.titleMedium)
        SettingRow("Auto-connect", "Connect the selected profile when the app starts", settings.autoConnect, vm::setAutoConnect)
        SettingRow("Keep screen on", "Prevent sleep while using Win VPN", settings.keepScreenOn, vm::setKeepScreenOn)

        Card(colors = CardDefaults.cardColors(containerColor = WinColors.Surface), shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Language", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to "English", "ru" to "Русский", "zh" to "中文", "ja" to "日本語", "ar" to "العربية", "fa" to "فارسی")
                        .forEach { (code, label) ->
                            FilterChip(selected = settings.language == code, onClick = { vm.setLanguage(code) }, label = { Text(label) })
                        }
                }
                Text("Device ID", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(settings.deviceId, color = WinColors.TextSecondary, fontSize = 12.sp)
            }
        }

        Text("Tunnel & Proxy", style = MaterialTheme.typography.titleMedium)
        SettingRow("Show logs", "Keep diagnostic events visible in the Logs view", settings.showLogs, vm::setShowLogs)
        SettingRow("Allow insecure TLS", "Allow TLS certificates that cannot be verified", settings.allowInsecure, vm::setAllowInsecure)
        SettingRow("Enable IPv6", "Add IPv6 address and default route to the VPN TUN", settings.enableIpv6, vm::setIpv6)
        SettingRow("Prefer IPv6", "Prefer IPv6 when resolving supported destinations", settings.preferIpv6, vm::setPreferIpv6)
        SettingRow("Allow LAN", "Keep common private LAN routes reachable", settings.allowLan, vm::setAllowLan)
        SettingRow("Mux", "Enable Xray multiplexing when supported", settings.mux, vm::setMux)
        SettingRow("SOCKS5 UDP", "Allow UDP support in local SOCKS mode", settings.socksUdp, vm::setSocksUdp)

        Text("DNS", style = MaterialTheme.typography.titleMedium)
        SettingRow("Local DNS", "Use local DNS handling where supported", settings.enableLocalDns, vm::setLocalDns)
        SettingRow("Fake DNS", "Enable FakeDNS only when supported by the selected core", settings.enableFakeDns, vm::setFakeDns)
        OutlinedTextField(value = settings.vpnDns, onValueChange = vm::setDns, label = { Text("VPN DNS") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Text("Routing", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("AsIs", "IPIfNonMatch", "IPOnDemand").forEach { strategy ->
                FilterChip(selected = settings.domainStrategy == strategy, onClick = { vm.setDomainStrategy(strategy) }, label = { Text(strategy) })
            }
        }

        OutlinedButton(onClick = { confirmClear = true }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Delete all configurations", color = WinColors.Error)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = WinColors.Surface,
            title = { Text("Delete all?") },
            text = { Text("All saved configurations will be removed.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAll(); confirmClear = false }) { Text("Delete", color = WinColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = WinColors.Surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(subtitle, color = WinColors.TextSecondary, fontSize = 13.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
