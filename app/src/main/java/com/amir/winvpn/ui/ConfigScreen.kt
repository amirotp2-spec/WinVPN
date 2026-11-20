package com.amir.winvpn.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.winvpn.config.ConfigParser
import com.amir.winvpn.config.ConfigType
import com.amir.winvpn.config.ParseResult
import com.amir.winvpn.config.VpnConfig
import com.amir.winvpn.ui.theme.WinColors

@Composable
fun ConfigScreen(vm: MainViewModel, startWithAdd: Boolean, onBack: () -> Unit) {
    val configs by vm.configs.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()

    var showAdd by rememberSaveable { mutableStateOf(startWithAdd) }
    var renameTarget by remember { mutableStateOf<VpnConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<VpnConfig?>(null) }

    ScreenScaffold("Configurations", onBack) {
        Button(
            onClick = { showAdd = true },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add Configuration")
        }

        if (configs.isEmpty()) {
            Text(
                "No configurations yet. Paste a vless://, vmess:// or trojan:// link.",
                color = WinColors.TextSecondary
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            configs.forEach { c ->
                ConfigCard(
                    c, selected = c.id == selectedId,
                    onClick = { vm.select(c.id) },
                    onRename = { renameTarget = c },
                    onDelete = { deleteTarget = c }
                )
            }
        }
    }

    if (showAdd) {
        AddConfigDialog(onDismiss = { showAdd = false }, onAdd = { vm.addConfig(it) })
    }
    renameTarget?.let { c ->
        RenameDialog(c.name, onDismiss = { renameTarget = null }) {
            vm.rename(c.id, it); renameTarget = null
        }
    }
    deleteTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = WinColors.Surface,
            title = { Text("Delete") },
            text = { Text("Delete \"${c.name}\"?") },
            confirmButton = {
                TextButton(onClick = { vm.delete(c.id); deleteTarget = null }) {
                    Text("Delete", color = WinColors.Error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AddConfigDialog(onDismiss: () -> Unit, onAdd: (String) -> ParseResult) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val detected = ConfigParser.detectType(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WinColors.Surface,
        title = { Text("Paste Configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    placeholder = { Text("vless://… / vmess://… / trojan://…") },
                    minLines = 4, maxLines = 6,
                    isError = error != null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                when {
                    error != null -> Text(error.orEmpty(), color = WinColors.Error)
                    text.isNotBlank() -> Text(
                        if (detected == ConfigType.UNKNOWN) "Detected: Unknown" else "Detected: ${detected.label}",
                        color = WinColors.TextSecondary
                    )
                }
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    text = clip.trim(); error = null
                }) { Text("Import from Clipboard") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (onAdd(text)) {
                    is ParseResult.Valid -> onDismiss()
                    ParseResult.Unsupported -> error = "Unsupported configuration"
                    ParseResult.Invalid -> error = "Invalid configuration"
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WinColors.Surface,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
