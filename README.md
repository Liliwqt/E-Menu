# E-Menu Smart Ordering System

E-Menu is an Android tablet ordering application for cafés, restaurants, canteens, food stalls, and other menu-based food-service businesses. It provides menu browsing, cart and checkout workflows, Firebase synchronization, local menu caching, and dedicated-device kiosk controls.

> The QR checkout records a **customer-reported payment**. Selecting **I've Paid** does not verify settlement with a bank or payment provider.

## Overview

This repository contains the **Android tablet kiosk / ordering application** for the E-Menu system. The tablet supports two practical usage modes:

- **Staff-assisted ordering:** staff carry the tablet and hand it to a customer for menu browsing and order entry.
- **Table-side self-service:** the tablet remains at a table or ordering area for customers to place orders independently.

Firebase synchronizes menu, inventory, settings, and order data with the broader E-Menu management platform. The web management platform (E-Menu Portal) is now **merged into the same project** as this kiosk app — both share the same Firebase project, Realtime Database, and security rules. The web app's source lives in the sibling `AI-Operations-Management-Platform-main` directory and is deployed to Firebase Hosting; this repository focuses on the Android kiosk client.

## Features

- Category-based digital menu and best-seller browsing
- Item details, size selection, price modifiers, and quantity controls
- Per-size stock display and inventory-aware quantity limits
- Cart management, customer-name entry, checkout, and order totals
- Static GCash/InstaPay QR and pay-at-counter options
- UUID-based, retry-safe order submission with tracked-stock updates
- Anonymous Firebase authentication with manual kiosk UID authorization
- Firebase synchronization for menus, inventory, appearance settings, and orders
- Room-backed menu cache for temporary connectivity interruptions
- Three selectable tablet layouts, including landscape and portrait modes
- Device Owner and Lock Task support for dedicated-device operation
- Restricted navigation, boot launch, secure-screen protection, and PIN-gated maintenance access
- Firebase Storage image loading restricted to the configured bucket

## Technology Stack

| Technology | Purpose |
| --- | --- |
| Kotlin | Android application development |
| Jetpack Compose and Material 3 | User interface and responsive tablet layouts |
| Hilt | Dependency injection |
| Room | Local menu cache |
| Firebase Authentication | Anonymous kiosk identity |
| Firebase Realtime Database | Menu, inventory, settings, and order synchronization |
| Coil | Remote menu image loading |
| Android Device Policy APIs | Device Owner, Lock Task, and kiosk restrictions |
| JUnit, MockK, and Compose UI Test | JVM and Android testing |
| Firebase Emulator Suite | Realtime Database Rules testing |

## System Architecture

```text
Customer / Staff
       |
       v
Android Tablet Kiosk
       |
       +---- Firebase Authentication
       |
       v
Firebase Realtime Database
       +---- Menu and settings
       +---- Inventory
       +---- Orders
       |
       v
E-Menu Portal (Web Management Platform)
       |
       v
Admin / Host / Authorized Staff
```

The Android kiosk communicates with Firebase directly. The web management platform (E-Menu Portal) is part of the same merged project — it shares the same Firebase project and Realtime Database, and is deployed separately to Firebase Hosting from the sibling `AI-Operations-Management-Platform-main` directory.

## Order Flow

```text
Browse Menu
    ↓
Select Item
    ↓
Configure Size / Quantity
    ↓
Add to Cart
    ↓
Enter Customer Name
    ↓
Choose Payment Method
    ↓
Validate Order and Stock
    ↓
Submit Order to Firebase
```

Tracked inventory decrements and order creation are sent together. Failed submissions keep the cart available for retry.

## Project Structure

```text
app/src/main/java/com/example/androidkiosk/
├── admin/                 Authentication, PIN, boot, and kiosk controls
├── data/
│   ├── local/             Room database, DAO, and entity
│   └── repository/        Firebase and Room repository implementations
├── di/                    Hilt modules and application setup
├── domain/repository/     Repository interfaces
├── model/                 Menu, cart, order, payment, and settings models
├── ui/
│   ├── animation/         Compose animation helpers
│   ├── main/              MainActivity
│   ├── menu/              MenuScreen, MenuViewModel, and UI components
│   └── theme/             Compose themes and typography
└── util/                  Image URL validation
```

Key implementation classes include `MainActivity`, `MenuScreen`, `MenuViewModel`, `MenuRepositoryImpl`, `OrderRepositoryImpl`, `MenuDatabase`, `AuthManager`, and `KioskManager`.

## Requirements

- Android Studio with Android SDK 36
- JDK 21
- Android 9 / API 28 or newer
- A Firebase project with Realtime Database and Anonymous Authentication
- Node.js 22 for Firebase Rules tests
- An unprovisioned Android device when configuring Device Owner mode

## Firebase Setup

1. Create a Firebase project and register an Android application with package name `com.example.androidkiosk`.
2. Download `google-services.json` and place it at `app/google-services.json`.
3. Enable **Anonymous** sign-in under Firebase Authentication.
4. Create a Firebase Realtime Database.
5. Review the canonical database rules in the web management project, replace the placeholder kiosk UIDs in the deployment copy, test the rules, and deploy them from that project.
6. Launch a newly installed kiosk and copy the anonymous UID shown on its registration screen.
7. Add the UID to every required allowlist expression in the deployed rules, then select **Check registration** on the tablet.

Do not commit a production `google-services.json`, real deployment UIDs, signing credentials, or locally populated rules.

The application uses these database paths:

```text
branch2/
├── categories/{category}/{item}
├── inventory/{category}/{item}/sizes/{size}/stock
├── appSettings
└── logs/{orderId}
```

Each kiosk order creates its immutable log and decrements tracked inventory in one Firebase update.
It writes `inventoryProcessed: true` and `orderSource: android_kiosk`; the web dashboard observes
that stock state and must never consume the order a second time.

Authentication creates the kiosk identity; it does not authorize database access by itself. The UID must also be allowed by the deployed Realtime Database Rules.

## Kiosk Setup

Full kiosk enforcement requires Device Owner provisioning, normally on a factory-reset or otherwise unprovisioned device. Install the application, connect with ADB, and run:

```bash
adb shell dpm set-device-owner com.example.androidkiosk/.admin.KioskDeviceAdminReceiver
```

> End-to-end setup (embedded-web first run, auto kiosk registration, building debug vs. provisioned
> APKs, WebView provider requirements, troubleshooting): see
> [`SETUP_GUIDE.md`](../AI-Operations-Management-Platform-main/SETUP_GUIDE.md) in the web project.

Release builds block ordering when Device Owner or Lock Task enforcement is unavailable. Debug builds remain usable for development and display a provisioning warning.

The default maintenance PIN is `1234`. Replace it before placing a tablet in public use.

After a successful maintenance PIN, the app opens the configured web management panel. The panel
uses its normal manager email/password login and remains signed in for the next maintenance session.
Use `-PadminPanelUrl=https://staging.example.com` to override the release panel URL when building a
staging APK.

## Local Cache and Connectivity

Firebase menu updates are mapped into Room. Previously synchronized menu data can remain visible during a temporary connection problem, but inventory synchronization and order submission still require Firebase connectivity. The application should not be treated as a fully offline ordering system.

## Build and Run

1. Clone the repository and open it in Android Studio:

   ```bash
   git clone https://github.com/Liliwqt/E-Menu-.git
   cd E-Menu-
   ```

2. Complete the Firebase setup and add `app/google-services.json`.
3. Allow Android Studio to sync Gradle.
4. Connect an Android tablet or start an emulator.
5. Build and install the debug application:

   ```bash
   ./gradlew installDebug
   ```

To create APKs directly:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

APK output is written below `app/build/outputs/apk/`.

For a signed release, copy `keystore.properties.example` to `keystore.properties` and add the local keystore values. The populated properties file and signing keystore must remain private.

## Testing

The repository includes JVM tests for menu mapping, cart and inventory behavior, order state, image validation, and release kiosk gating. Android instrumentation tests cover PIN throttling and Compose checkout behavior. Firebase Emulator tests validate kiosk authorization, order creation, order ownership, and stock rules.

```bash
# JVM tests
./gradlew testDebugUnitTest

# Android lint
./gradlew lintDebug

# Compile instrumentation tests
./gradlew compileDebugAndroidTestKotlin

# Run instrumentation tests on a connected device or emulator
./gradlew connectedDebugAndroidTest

# Firebase Realtime Database Rules tests
cd firebase-tests
npm ci --ignore-scripts
npm run test:emulator
```

Run the repository and APK credential scan from the project root:

```bash
bash scripts/security_scan.sh
```

## Security Notes

- Anonymous Firebase authentication identifies a kiosk but does not automatically authorize it.
- Deploy restrictive Realtime Database Rules before allowing customer use.
- Give each kiosk only the permissions required for menu access and order submission.
- Client-side validation and Device Owner controls are not substitutes for a trusted backend.
- A public-facing tablet must not be treated as a trusted server or payment authority.
- Review the deployment guidance in [`SECURITY.md`](SECURITY.md).

### QR Payment Warning

The bundled QR is static. **I've Paid** records `CUSTOMER_REPORTED_PAID`, not payment-provider-verified settlement. Staff must confirm the payment separately. Production-grade automatic verification requires a payment provider integration implemented through a trusted backend.

## Documentation

Detailed documentation covering system design, tablet deployment strategy, architecture, security, cloud synchronization, administrative workflows, and the wider E-Menu platform is maintained in the merged project. See [`SETUP_GUIDE.md`](../AI-Operations-Management-Platform-main/SETUP_GUIDE.md) and [`SETUP_BASIC.txt`](../AI-Operations-Management-Platform-main/SETUP_BASIC.txt) in the web project for end-to-end setup, and the web project's `README.md` for the management platform.

## Limitations

- QR payments are not automatically verified.
- Current inventory and order submission require Firebase connectivity.
- Production database access requires explicit kiosk UID authorization.
- Full kiosk enforcement requires correct Android Device Owner provisioning.
- Direct client-side inventory updates provide less protection than a trusted backend.
