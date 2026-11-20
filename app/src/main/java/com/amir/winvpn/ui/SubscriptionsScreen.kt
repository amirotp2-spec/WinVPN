package com.amir.winvpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
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

            Button(
                onClick = {
                    loading = true
                    message = null

                    scope.launch {
                        val result = vm.importSubscription(url)

                        loading = false

                        message = result.fold(
                            onSuccess = { pair ->
                                val imported = pair.first
                                val failed = pair.second

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
                            onFailure = { error ->
                                error.message
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
                        "Updating..."
                    } else {
                        "Update Subscription"
                    }
                )
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            message?.let {
                Text(it)
            }
        }
    }
}
