# 🛡️ Taqwa Fortress

> **A production-grade Android parental-control & self-accountability app that uses the Android Device Owner API to enforce digital commitments at the system level.**

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-blue)](https://developer.android.com/topic/architecture)
[![API](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-green)](https://developer.android.com/about/versions/oreo)
[![Version](https://img.shields.io/badge/Version-1.0.2-orange)](https://github.com)

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Core Systems](#-core-systems)
  - [Device Owner & System-Level Control](#1-device-owner--system-level-control)
  - [3-Layer Content Filtering](#2-3-layer-content-filtering)
  - [Wireless ADB Activation Flow](#3-wireless-adb-activation-flow)
  - [Authentication & Subscription Flow](#4-authentication--subscription-flow)
  - [Fortress Lifecycle Management](#5-fortress-lifecycle-management)
- [Data Layer](#-data-layer)
- [Security Design](#-security-design)
- [UI & Navigation](#-ui--navigation)
- [Screenshots](#-screenshots)
- [Getting Started](#-getting-started)
- [Engineering Challenges Solved](#-engineering-challenges-solved)
- [Future Roadmap](#-future-roadmap)

---

## 🔍 Overview

**Taqwa Fortress** is a self-accountability and digital wellness application built for Android. It helps users break addictive digital habits (social media, explicit content, distracting browsers) by leveraging **Android Device Owner privileges** to enforce app blocking, DNS-level content filtering, and time-locked commitments — controls that cannot be bypassed by the user without completing their chosen commitment period.

The app targets adults seeking structured recovery from digital addiction, particularly in contexts where willpower alone is insufficient. Once activated, the fortress cannot be removed or bypassed until the commitment period naturally expires.

**This is not a simple parental-control app.** It performs full device administration:

- Hides apps from the launcher entirely
- Suspends apps at the OS level (greyed out, unlaunchable)
- Sets private DNS to a content-filtering server
- Locks Chrome into SafeSearch mode with incognito disabled
- blocks uninstallation of itself
- Enforces automatic network time to prevent "time travel" bypasses

---

## ✨ Key Features

| Feature | Description |
|---|---|
| **Device Owner Activation** | Automated setup via Wireless ADB — no laptop or USB cable required |
| **DNS Content Filtering** | Forces CleanBrowsing Adult Filter as private DNS; locks the setting |
| **App Suspension & Hiding** | Suspends browsers via `setPackagesSuspended()`; hides blacklisted apps via `setApplicationHidden()` |
| **Chrome Policy Management** | Enforces SafeSearch, disables incognito mode and DNS-over-HTTPS via managed app config |
| **Commitment Lock** | Time-locked fortress periods (3 days to 1 year); countdown with real-time UI |
| **Auto-Blocking on Install** | `PackageChangeReceiver` detects new app installs and auto-blocks pre-listed packages |
| **Subscription System** | Integrated Whop payment gateway via in-app WebView with redirect-based confirmation |
| **Firebase Auth** | Email/password with verification gate + Google Sign-In |
| **Journey Tracking** | Daily mood check-ins with 90-day heatmap visualization |
| **In-App Update System** | Checks Firestore for new APK versions; downloads and installs silently |
| **Encrypted Local Storage** | All sensitive data stored in `EncryptedSharedPreferences` (AES256-GCM) |

---

## 🏗️ Architecture

The project follows **Clean Architecture** with an **MVVM** presentation layer, organized into clearly separated concerns.

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI Layer (MVVM)                          │
│  Activities / Fragments ◄──► ViewModels ◄──► LiveData          │
└─────────────────────┬───────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────┐
│                      Domain / Service Layer                      │
│  FortressActivationService · ContentFilteringService            │
│  DeviceOwnerService · WirelessAdbService · UpdateChecker        │
└─────────────────────┬───────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────┐
│                       Data / Repository Layer                    │
│  IRepository<T> interface · Local (EncryptedSharedPrefs)        │
│  Remote (Firebase Firestore) · Mappers (JSON ↔ Domain)         │
└─────────────────────────────────────────────────────────────────┘
```

### Design Patterns Used

- **Repository Pattern** — All data access abstracted behind `IRepository<T>` with separate local (encrypted) and remote (Firestore) implementations
- **Builder Pattern** — Every domain entity constructed via dedicated `*Builder` classes with validation
- **Mapper Pattern** — `IMapper<INPUT, OUTPUT>` interface for clean JSON ↔ domain object conversion
- **Sealed Classes** — All async operation results modeled as sealed `*Result` / `*State` classes
- **Observer Pattern** — `LiveData` throughout the UI layer; coroutines with `viewModelScope` for async work

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin |
| **Min SDK** | API 26 (Android 8.0 Oreo) |
| **Architecture** | MVVM + Clean Architecture |
| **Async** | Kotlin Coroutines + Flow |
| **UI** | Programmatic Views + XML Layouts + ViewBinding |
| **Authentication** | Firebase Authentication (Email + Google Sign-In) |
| **Database** | Cloud Firestore |
| **Local Storage** | `EncryptedSharedPreferences` (Tink AES256-GCM) |
| **System APIs** | `DevicePolicyManager`, `NsdManager`, `DownloadManager` |
| **Crypto** | BouncyCastle (`bcprov-jdk18on`, `bcpkix-jdk18on`) + Conscrypt |
| **ADB Automation** | `libadb-android` (MuntashirAkon) |
| **Payments** | Whop (WebView + redirect detection) |
| **DI** | Manual dependency injection via constructors |
| **CI** | GitHub Actions (APK signing + release upload) |

---

## 📁 Project Structure

```
app/src/main/java/com/example/takwafortress/
│
├── auth/
│   ├── AuthManager.kt              # Firebase Auth wrapper (email, Google, password reset)
│   └── GoogleSignInHelper.kt       # Google Sign-In flow abstraction
│
├── model/
│   ├── entities/                   # Domain entities: User, FortressPolicy, BlockedApp…
│   ├── builders/                   # Builder pattern for all entities
│   ├── enums/                      # SubscriptionStatus, CommitmentPlan, FortressState…
│   └── interfaces/                 # IFortress, ILicense, IIdentifiable
│
├── mappers/                        # IMapper<JSONObject, Entity> implementations
│
├── repository/
│   ├── interfaces/                 # IRepository<T>, IUserRepository, IBlockedAppRepository…
│   └── implementations/            # Local (EncryptedSharedPrefs) + Firebase (Firestore)
│
├── services/
│   ├── core/
│   │   ├── DeviceOwnerService.kt           # DPM wrapper — hide/suspend/restrict
│   │   ├── FortressActivationService.kt    # Full activation orchestration
│   │   ├── FortressClearService.kt         # Full deactivation & cleanup
│   │   └── UpdateChecker.kt               # Firestore version check + APK install
│   ├── filtering/
│   │   ├── ContentFilteringService.kt      # 3-layer filter activation
│   │   ├── AppSuspensionService.kt         # Browser/nuclear app management
│   │   └── BlockedAppsManager.kt          # User-defined block list
│   ├── monitoring/
│   │   ├── AppInstallMonitorService.kt     # Auto-block on install
│   │   └── TimeProtectionService.kt       # Auto-time enforcement
│   └── security/
│       ├── WirelessAdbService.kt           # Pair → connect → set-device-owner flow
│       ├── AdbDiscoveryService.kt          # mDNS listener (foreground service)
│       └── NotificationReplyReceiver.kt   # Inline notification code entry
│
├── ui/
│   ├── activities/                 # 15+ Activities covering all flows
│   ├── fragments/                  # Dashboard, Apps, Awareness, Journey tabs
│   ├── viewmodels/                 # One ViewModel per screen/flow
│   └── adapters/                   # RecyclerView adapters
│
├── receivers/
│   ├── DeviceAdminReceiver.kt      # Device Admin lifecycle events
│   ├── BootCompletedReceiver.kt    # Delayed service restart on boot
│   └── PackageChangeReceiver.kt   # App install/uninstall events
│
└── util/
    ├── constants/                  # AppConstants, BlockedPackages, DnsServers
    ├── crypto/                     # HardwareIdGenerator (SHA-256 device fingerprint)
    ├── exceptions/                 # Typed exception hierarchy
    └── network/                    # FirebaseApiClient, WhopWebhookHandler
```

---

## ⚙️ Core Systems

### 1. Device Owner & System-Level Control

`DeviceOwnerService` wraps `DevicePolicyManager` to provide clean, result-typed APIs:

```kotlin
// Hide apps completely from launcher (nuclear blacklist)
fun hideApps(packageNames: List<String>): Result<Unit> {
    if (!isDeviceOwner()) return Result.failure(Exception("Device Owner not active"))
    packageNames.forEach { pkg ->
        devicePolicyManager.setApplicationHidden(adminComponent, pkg, true)
    }
    return Result.success(Unit)
}

// Suspend apps (visible but unlaunchable, greyed out)
fun suspendApps(packageNames: List<String>): Result<Unit> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        devicePolicyManager.setPackagesSuspended(adminComponent, packageNames.toTypedArray(), true)
    }
    return Result.success(Unit)
}
```

**Protection capabilities enabled at activation:**

| Restriction | API Used |
|---|---|
| Block app uninstall | `setUninstallBlocked()` |
| Force automatic time | `setAutoTimeRequired()` |
| Lock private DNS | `addUserRestriction(DISALLOW_CONFIG_PRIVATE_DNS)` |
| Block factory reset | `addUserRestriction(DISALLOW_FACTORY_RESET)` |
| Block developer mode | `addUserRestriction(DISALLOW_DEBUGGING_FEATURES)` |
| Set private DNS hostname | `setGlobalPrivateDnsModeSpecifiedHost()` |

---

### 2. 3-Layer Content Filtering

`ContentFilteringService` activates three independent protection layers in sequence:

```
Layer 1: DNS Filtering
  └─ setGlobalPrivateDnsModeSpecifiedHost("adult-filter-dns.cleanbrowsing.org")
  └─ DISALLOW_CONFIG_PRIVATE_DNS restriction (user cannot change it)

Layer 2: Chrome Managed Configuration
  └─ IncognitoModeAvailability = false
  └─ ForceSafeSearch = true
  └─ ForceYouTubeRestrict = 2 (Strict)
  └─ DnsOverHttpsMode = "off"
  └─ ExtensionInstallBlacklist = ["*"]
  └─ DeveloperToolsDisabled = true

Layer 3: Browser Blocking
  └─ setApplicationHidden() for 19 known alternative browsers
  └─ Only com.android.chrome remains accessible
```

The DNS test function validates the filter is working by attempting to resolve a known blocked domain and checking whether an `UnknownHostException` is thrown:

```kotlin
suspend fun testDnsFilter(): DnsTestResult = withContext(Dispatchers.IO) {
    try {
        val address = InetAddress.getByName("pornhub.com")
        DnsTestResult.Failed("Domain resolved to: ${address.hostAddress}")
    } catch (e: UnknownHostException) {
        DnsTestResult.Success("DNS filter is WORKING — domain blocked")
    }
}
```

---

### 3. Wireless ADB Activation Flow

This is the most technically complex part of the app. Rather than requiring a USB cable or laptop, the app automates Device Owner provisioning entirely on-device using Wireless ADB.

**Full flow:**

```
User opens Developer Options → Wireless Debugging → Pair with code
        │
        ▼
AdbDiscoveryService (NsdManager)
  Discovers "_adb-tls-pairing._tcp" mDNS service
  Resolves IP + port
        │
        ▼
NotificationReplyReceiver
  Inline RemoteInput notification appears
  User types 6-digit code without leaving Settings screen
        │
        ▼
WirelessAdbService.pairDevice(code, port)
  Uses TakwaAdbManager (libadb-android)
  Generates RSA keypair + self-signed X.509 certificate
  Executes TLS pairing handshake
        │
        ▼
WirelessAdbService.connectAndSetDeviceOwner()
  manager.autoConnect(context, timeout)
  Detects and removes all device accounts via ADB shell
  Executes: "dpm set-device-owner com.example.takwafortress/.receivers.DeviceAdminReceiver"
  Verifies via DevicePolicyManager.isDeviceOwnerApp()
        │
        ▼
FortressActivationService.activateFortress()
  Applies all restrictions
  Saves FortressPolicy to EncryptedSharedPreferences
  Updates Firestore user document (hasDeviceOwner = true)
```

**Certificate generation** (BouncyCastle + Android's native X.509):

```kotlin
private fun generateCert(keyPair: KeyPair): Certificate {
    val subject = X500Name("CN=TakwaFortress,O=TakwaFortress,C=US")
    val signer  = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
    val holder  = JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, keyPair.public)
                      .build(signer)
    return JcaX509CertificateConverter().getCertificate(holder)
}
```

---

### 4. Authentication & Subscription Flow

```
WelcomeActivity
    │
    ├─ Google Sign-In → Firebase Auth → Firestore user document
    │
    └─ Email Registration
           │
           ▼
       Email Verification Gate
       (Firebase sends link → user verifies → Firestore doc created)
           │
           ▼
       CommitmentSelectionActivity
       (Plan selection: Trial/Monthly/Quarterly/Biannual/Annual)
           │
           ├─ Free Trial → sets subscriptionStatus = TRIAL immediately
           │
           └─ Paid Plan → opens Whop checkout in WebView
                          detects SUCCESS_REDIRECT_URL
                          sets subscriptionStatus = ACTIVE in Firestore
           │
           ▼
       DeviceOwnerSetupActivity
       (Wireless ADB activation)
           │
           ▼
       FortressDashboardActivity
```

**Routing logic in `MainViewModel.resolveRoute()`** checks these conditions in order:
1. Firebase Auth state
2. Firestore user document (30-second in-memory cache)
3. Device ID validation (detects device transfer)
4. Commitment end date (expired → `ExpiredDashboard`)
5. `subscriptionStatus` (PENDING/TRIAL/ACTIVE/EXPIRED/CANCELLED)
6. `hasDeviceOwner` flag
7. Active commitment → Dashboard

---

### 5. Fortress Lifecycle Management

**Activation** (`FortressActivationService`):
```
validateDeviceOwner → createFortressPolicy → applyRestrictions → activateContentFiltering → savePolicy
```

**Deactivation** (`FortressClearService`) — executed in strict order to prevent device lockout:
```
1. Unsuspend browsers
2. Unhide nuclear apps
3. Unblock LocalBlockedAppRepository apps
4. Unblock BlockedAppsManager apps
5. Remove all UserRestrictions (factory reset, debugging, safe boot, DNS…)
6. Reset private DNS to opportunistic
7. Disable forced auto-time
8. Stop DNS VPN service
9. Remove Chrome policies
10. Allow app uninstall
11. Clear local encrypted data
12. Reset Firestore user document (hasDeviceOwner=false, subscriptionStatus=PENDING)
13. clearDeviceOwnerApp() ← MUST be last
```

---

## 💾 Data Layer

### Repository Pattern

All repositories implement `IRepository<T : IIdentifiable>`:

```kotlin
interface IRepository<T : IIdentifiable> {
    suspend fun create(item: T): ID
    suspend fun get(id: ID): T
    suspend fun getAll(): List<T>
    suspend fun update(item: T)
    suspend fun delete(id: ID)
    suspend fun exists(id: ID): Boolean
}
```

| Repository | Storage | Purpose |
|---|---|---|
| `FirebaseUserRepository` | Cloud Firestore | User account, subscription, commitment dates |
| `LocalFortressPolicyRepository` | EncryptedSharedPrefs | Active fortress state and settings |
| `LocalBlockedAppRepository` | EncryptedSharedPrefs | User-defined blocked apps list |
| `LocalDeviceInfoRepository` | EncryptedSharedPrefs | Hardware ID, brand, activation method |
| `LocalLicenseRepository` | EncryptedSharedPrefs | Legacy license keys |

### Entity Builders

Every domain object is constructed through a validated builder:

```kotlin
val policy = FortressPolicyBuilder.newBuilder()
    .setCommitmentPlan(CommitmentPlan.MONTHLY)
    .setActivationTimestamp(System.currentTimeMillis())
    .setExpiryTimestamp(expiryTime)
    .setIsDeviceOwnerActive(true)
    .setBlockedApps(BlockedPackages.ALL_BLOCKED)
    .setCurrentState(FortressState.ACTIVATING)
    .build() // throws IllegalArgumentException if required fields missing
```

### Mapper Layer

```kotlin
interface IMapper<INPUT, OUTPUT> {
    fun map(data: INPUT): OUTPUT       // JSON → Domain
    fun reverseMap(data: OUTPUT): INPUT // Domain → JSON
}
```

Mappers handle backward compatibility (e.g., `BlockedAppMapper` uses `optBoolean("isInstalled", true)` for fields added in later versions).

---

## 🔐 Security Design

### Hardware ID Generation

Device fingerprint is generated by hashing multiple hardware identifiers with SHA-256:

```kotlin
val components = listOf(
    "android_id:$androidId",
    "manufacturer:${Build.MANUFACTURER}",
    "model:${Build.MODEL}",
    "brand:${Build.BRAND}",
    "device:${Build.DEVICE}",
    "board:${Build.BOARD}"
)
val fingerprint = SHA256(components.joinToString("|")).substring(0, 32)
```

This fingerprint is stored in Firestore and compared on every login to detect device transfers or factory resets.

### Encrypted Storage

All local sensitive data uses `EncryptedSharedPreferences` backed by **Android Keystore** (AES256-SIV for keys, AES256-GCM for values):

```kotlin
EncryptedSharedPreferences.create(
    context,
    "taqwa_fortress_policy_prefs",
    masterKey,
    PrefKeyEncryptionScheme.AES256_SIV,
    PrefValueEncryptionScheme.AES256_GCM
)
```

### Anti-Bypass Measures

| Threat | Mitigation |
|---|---|
| Uninstall the app (via Settings) | `setUninstallBlocked(true)` via Device Owner |
| Change system time | `setAutoTimeRequired(true)` + restriction |
| Use incognito browser | Chrome incognito disabled via managed config |
| Use Chrome DoH to bypass DNS | `DnsOverHttpsMode = "off"` in Chrome policy |
| Install an alternative browser | `setApplicationHidden()` for 19 browsers |
| Enter Safe Mode | `DISALLOW_SAFE_BOOT` restriction |
| Install a VPN app | DNS locked at system level, VPN blocked |

> **Intentional Design Decisions — Factory Reset & ADB are allowed:**
>
> Factory reset and USB/Wireless ADB debugging are **deliberately left unrestricted**. The philosophy behind this choice is that Taqwa Fortress is a *self-accountability* tool, not a prison. Forcing a user into a locked device they cannot escape creates a hostile, trust-breaking experience — especially if they need emergency access, want to troubleshoot a device issue, or simply decide the commitment isn't for them.
>
> A user who factory resets is making a conscious choice. The app does not attempt to prevent that. What it does is **reset their Firestore account state** (`hasDeviceOwner = false`, `subscriptionStatus = PENDING`) so they must go through the full setup flow again — serving as a natural, low-coercion deterrent. The goal is friction through commitment design, not technical imprisonment.

---

## 🖼️ UI & Navigation

The app uses a **single-Activity dashboard** (`FortressDashboardActivity`) with four fragment tabs built entirely in programmatic Kotlin (no XML for fragments):

| Tab | Fragment | Purpose |
|---|---|---|
| 🛡️ Fortress | `DashboardFragment` | Countdown timer, protection status, DNS controls |
| 📱 Apps | `AppsFragment` | Block/unblock installed apps; pre-block future installs |
| 📖 Awareness | `AwarenessFragment` | Educational articles on neuroscience and recovery |
| 🗺️ Journey | `JourneyFragment` | Daily mood check-in + 90-day heatmap calendar |

All fragments share a single `FortressStatusViewModel` that exposes:
- `remainingTime: LiveData<RemainingTime>` — updates every second via coroutine loop
- `progressPercent: LiveData<Int>` — commitment progress 0–100%
- `isCommitmentExpired: LiveData<Boolean>` — triggers expired mode across all tabs
- `protectionReport: LiveData<ProtectionReport>` — DNS + blocked apps status

**Soft Dark design system** defined in `colors.xml`:
```xml
<color name="background_dark">#161B27</color>   <!-- warm navy -->
<color name="background_card">#1E2535</color>    <!-- card surface -->
<color name="green">#5DB88A</color>              <!-- muted teal-green -->
<color name="blue">#4A90D9</color>               <!-- primary accent -->
<color name="text_primary">#EFF3F8</color>       <!-- soft white -->
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Meerkat (2025.1) or later
- JDK 17
- Android device running API 26+ (physical device required for Device Owner features)

### Setup

```bash
git clone https://github.com/your-username/TakwaFortress.git
cd TakwaFortress
```

1. Add your `google-services.json` to `app/`
2. Create a Firestore database with a `users` collection and an `app_config/version` document
3. (Optional) Set up a Whop product and update `CommitmentPlan.kt` with your checkout URLs

```bash
./gradlew assembleDebug
```

### Environment Variables (for release signing)

```
KEYSTORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

---

## 🧩 Engineering Challenges Solved

### 1. On-Device ADB Pairing Without a Laptop

The standard Device Owner provisioning flow requires ADB from a computer. This app solves that by:
- Using `NsdManager` to discover the `_adb-tls-pairing._tcp` mDNS service broadcast by Wireless Debugging
- Implementing a foreground service that listens 24/7 and posts an inline `RemoteInput` notification the moment a pairing code screen is opened in Settings
- Running the full TLS pairing + `dpm set-device-owner` command flow in a background coroutine — no user friction beyond typing the 6-digit code in the notification shade

### 2. BouncyCastle Compatibility on Android

Android ships its own stripped-down BC provider that conflicts with the full BouncyCastle JAR. The solution:
- Excluded all `bcprov-jdk15*` and `bcpkix-jdk15*` variants in `configurations.all`
- Used `bcprov-jdk18on` + `bcpkix-jdk18on` exclusively
- Registered the provider at position 1 in `TaqwaApplication.onCreate()` before any crypto operations
- Used `JcaX509CertificateConverter` **without** `.setProvider("BC")` for the final X.509 conversion step to let Android's native provider handle it — this was the root cause of `NoSuchAlgorithmException` crashes on several OEM devices

### 3. ADB Account Cleanup Automation

`dpm set-device-owner` fails if any Google/Samsung accounts are on the device. Rather than asking users to manually remove them, the app:
- Parses `dumpsys account` output to find all accounts and their types
- Dynamically resolves the owning package by keyword-matching against installed packages (handles Microsoft suite where `com.microsoft.office` can be owned by Outlook, Teams, Word, etc.)
- Uninstalls account-holding packages via `pm uninstall --user 0`
- Falls back to `sqlite3` direct DB deletion if no owning package is found
- Polls until `dumpsys account` shows 0 remaining accounts before proceeding

### 4. Foreground Service Race Condition on Boot

Naïve boot-time service starts caused `ForegroundServiceDidNotStartInTimeException` on Android 12+. Solution:
- `BootCompletedReceiver` delays all service starts by 15 seconds via `Handler.postDelayed()`
- Safe mode detection was removed from the boot receiver entirely (it was causing boot loops)
- `stopDiscovery()` in `DeviceOwnerSetupViewModel` delays `stopService()` by 600ms to give `onStartCommand` + `startForeground()` time to complete

### 5. LiveData Dead Zone After ADB Command

When `dpm set-device-owner` succeeds, Android kills and restarts the app process. Any LiveData observer that was registered before the restart never fires. Solution:
- `DeviceOwnerSetupActivity.onResume()` directly queries `DevicePolicyManager.isDeviceOwnerApp()` as the source of truth
- A `successHandled` boolean flag prevents the success dialog from showing twice if both `onResume` and the LiveData observer fire

---

## 🗺️ Future Roadmap

- [ ] **Samsung Knox SDK integration** — 10-second Device Owner activation without ADB for Samsung devices
- [ ] **WebView Killer** — `AccessibilityService` to detect and redirect in-app browser WebViews to managed Chrome
- [ ] **Accountability Partner** — Real-time notifications to a trusted contact on bypass attempts
- [ ] **iOS App** — Profile-based MDM restriction enforcement
- [ ] **Firebase Cloud Functions** — Server-side license validation and webhook handling for Whop payments
- [ ] **Hilt DI** — Replace manual constructor injection with Hilt
- [ ] **Unit & Integration Tests** — Repository, mapper, and service layer test coverage

---

## 📄 License

This project is proprietary software. All rights reserved.

---

## 👤 Author

Built and maintained as a solo engineering project, covering the full stack: Android system APIs, Firebase backend, payment integration, crypto/TLS implementation, and UI design.

> *"The browser blocking, DNS filtering, and device owner APIs are publicly documented. The engineering challenge is integrating them into a system that is cohesive, robust, and genuinely hard to bypass."*
