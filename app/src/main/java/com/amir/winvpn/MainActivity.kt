package com.amir.winvpn

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amir.winvpn.ui.AboutScreen
import com.amir.winvpn.ui.ConfigScreen
import com.amir.winvpn.ui.HomeScreen
import com.amir.winvpn.ui.MainViewModel
import com.amir.winvpn.ui.SettingsScreen
import com.amir.winvpn.ui.SubscriptionsScreen
import com.amir.winvpn.ui.theme.WinColors
import com.amir.winvpn.ui.theme.WinVpnTheme
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { WinVpnTheme { WinVpnApp() } }
    }
}

private enum class Screen { HOME, CONFIGS, CONFIGS_ADD, SUBSCRIPTIONS, SETTINGS, ABOUT }

@Composable
fun WinVpnApp(vm: MainViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        vm.onVpnPermissionResult(it.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        vm.vpnPermissionRequest.collect { request ->
            request?.let { vpnPermissionLauncher.launch(it) }
        }
    }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    Box(
        Modifier.fillMaxSize().background(WinColors.Background)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                vm,
                onOpenConfigs = { add -> screen = if (add) Screen.CONFIGS_ADD else Screen.CONFIGS },
                onOpenSettings = { screen = Screen.SETTINGS },
                onOpenSubscriptions = { screen = Screen.SUBSCRIPTIONS },
                onOpenAbout = { screen = Screen.ABOUT }
            )
            Screen.CONFIGS, Screen.CONFIGS_ADD ->
                ConfigScreen(vm, startWithAdd = screen == Screen.CONFIGS_ADD) { screen = Screen.HOME }
            Screen.SUBSCRIPTIONS -> SubscriptionsScreen(vm) { screen = Screen.HOME }
            Screen.SETTINGS -> SettingsScreen(vm) { screen = Screen.HOME }
            Screen.ABOUT -> AboutScreen { screen = Screen.HOME }
        }
    }
}
