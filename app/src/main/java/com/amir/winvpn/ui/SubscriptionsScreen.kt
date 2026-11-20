package com.amir.winvpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.amir.winvpn.ui.theme.WinColors

@Composable
fun SubscriptionsScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    var url by rememberSaveable {
        mutableStateOf("")
    }

    var message by remember {
        mutableStateOf<String?>(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    ScreenScaffold(
        title = "Subscriptions",
        onBack = onBack
    ) {

        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                message = null
            },
            label = {
                Text("Subscription URL")
            },
            placeholder = {
                Text("https://example.com/subscription")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Button(
            onClick = {
                loading = true
                message = null

                scope.launch {
                    val result = vm.importSubscription(url)

                    loading = false

                    message = result.fold(
                        onSuccess = { (imported, failed) ->
                            buildString {
                                append("Imported ")
                                append(imported)
                                append(" configs")

                                if (failed > 0) {
                                    append("; skipped ")
                                    append(failed)
                                    append(" invalid lines")
                                }

                                append(".")
                            }
                        },
                        onFailure = {
                            it.message
                                ?: "Subscription update failed"
                        }
                    )
                }
            },
            enabled = url.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (loading) {
                    "Updating…"
                } else {
                    "Update Subscription"
                }
            )
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        message?.let { text ->
            Text(
                text = text,
                color = if (
                    text.startsWith("Imported")
                ) {
                    WinColors.Connected
                } else {
                    WinColors.Error
                }
            )
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Supports plain-text and Base64 subscriptions containing " +
                "VLESS, VMess, Trojan, Shadowsocks, SOCKS5 and HTTP links.",
            color = WinColors.TextSecondary
        )
    }
}
