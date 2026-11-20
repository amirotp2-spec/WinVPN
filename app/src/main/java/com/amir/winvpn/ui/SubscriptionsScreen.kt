package com.amir.winvpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.amir.winvpn.ui.theme.WinColors

@Composable
fun SubscriptionsScreen(vm: MainViewModel, onBack: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScreenScaffold("Subscriptions", onBack) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; message = null },
            label = { Text("Subscription URL") },
            placeholder = { Text("https://example.com/subscription") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                loading = true
                message = null
                scope.launch {
                    val result = vm.importSubscription(url)
                    loading = false
                    message = result.fold(
                        onSuccess = { (imported, failed) -> "Imported $imported configs${if (failed > 0) "; skipped $failed invalid lines" else ""}." },
                        onFailure = { it.message ?: "Subscription update failed" }
                    )
                }
            },
            enabled = url.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Updating…" else "Update Subscription")
        }
        message?.let { Text(it, color = if (it.startsWith("Imported")) WinColors.Connected else WinColors.Error) }
        Text(
            "Supports plain-text and Base64 subscriptions containing VLESS, VMess, Trojan, Shadowsocks, SOCKS5 and HTTP links.",
            color = WinColors.TextSecondary
        )
    }
}
