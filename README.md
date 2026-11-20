# 🚀 Win VPN V2

<p align="center">
  <b>Fast • Secure • Powerful</b>
</p>

<p align="center">
  A modern Android VPN client powered by Xray Core.
</p>

---

## 📱 About

**Win VPN** یک کلاینت VPN مدرن برای اندروید است که برای مدیریت و اجرای کانفیگ‌های مختلف VPN طراحی شده است.

این پروژه از **Xray Core** و Android `VpnService` برای ایجاد اتصال واقعی VPN استفاده می‌کند.

---

## ✨ Features

- 🔐 Real VPN Connection
- ⚡ Xray Core
- 📡 Subscription Support
- 📥 Config Import
- 🔎 Automatic Config Detection
- 💾 Config Management
- 🔄 Subscription Update
- 🌐 DNS & Routing
- 📊 Speed Test
- 📋 Logs
- 📱 Per-App VPN
- 🆔 Device ID
- 🌙 Dark UI
- 🌍 Multi-Language Support
- ↔️ RTL Support
- 🎨 Modern Jetpack Compose UI

---

## 🌐 Supported Protocols

- VLESS
- VMess
- Trojan
- Shadowsocks
- SOCKS5
- HTTP

---

## 🔒 Security & Transport

Win VPN supports the following options where supported by the selected configuration:

- TLS
- Reality
- WebSocket
- gRPC
- HTTP Upgrade
- XHTTP
- TCP

---

## 📡 Subscription

Win VPN supports importing configurations from subscription links.

Supported subscription formats include:

- Plain Text
- Base64
- URL-safe Base64

Subscription features:

- Add Subscription
- Update Subscription
- Import Configurations
- Detect Config Types
- Skip Invalid Configurations
- Store Imported Configurations

---

## 🛠️ Technology

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| VPN | Android VpnService |
| Core | Xray Core |
| Build | Gradle |
| CI/CD | GitHub Actions |
| Minimum Android | Android 8.0+ |

---

## 🧩 Architecture

```text
Android App
    │
    ├── Jetpack Compose UI
    │
    ├── Config Parser
    │
    ├── Subscription Manager
    │
    ├── VPN Service
    │
    ├── Xray Config Builder
    │
    └── Xray Core
            │
            └── TUN Interface
```

---

## 📦 Build

The project uses GitHub Actions to build the Android application.

Before building, the required Xray Core AAR is fetched and verified.

The build verifies the SHA-256 hash of:

```text
app/libs/libv2ray.aar
```

If the required Core is missing or invalid, the build will fail instead of silently using a fake or fallback implementation.

---

## 🚀 GitHub Actions

Available workflows:

- `Android build`
- `Core AAR`

The Android workflow builds the APK.

The Core workflow prepares and verifies the required Xray Core.

---

## 📱 Version

**Current Version:** `2.0.0`

**Release:** `Win VPN V2`

**Status:** Stable Testing Release

---

## 📥 APK

The latest APK can be downloaded from the project's **GitHub Releases** section.

---

## 🔐 Disclaimer

Win VPN is a VPN client designed to manage and connect to user-provided configurations.

Users are responsible for the configurations, servers, subscriptions and network services they use with the application.

---

## ❤️ Credits

Built with:

- Kotlin
- Jetpack Compose
- Android VpnService
- Xray Core
- GitHub Actions

---

## 🚀 Win VPN

**Fast • Secure • Powerful**

**Win VPN V2 — 2.0.0**
