package com.amir.winvpn.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amir.winvpn.engine.EngineFactory
import com.amir.winvpn.ui.theme.WinColors

@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold("About", onBack) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = WinColors.Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("WIN VPN", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WinColors.Accent)
                Text("Version 2.0.0", color = WinColors.TextSecondary)
                Text("Core: ${EngineFactory.coreVersion() ?: "not installed"}", color = WinColors.TextSecondary, fontSize = 12.sp)
                Text("com.amir.winvpn", color = WinColors.TextSecondary, fontSize = 12.sp)
                Text(
                    "\nManage VLESS, VMess and Trojan configurations and connect with a single tap.",
                    color = WinColors.TextPrimary
                )
            }
        }
    }
}
