# Security Policy

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting for security issues. Do not include credentials, customer records, Firebase exports, or exploit details in a public issue.

## Deployment requirements

### Firebase

- Enable Anonymous Authentication and approve each kiosk UID explicitly.
- Deploy reviewed Realtime Database Rules from the web management project before installing a release build; it is the canonical production rule source.
- Keep kiosk permissions scoped to menu/settings/inventory reads, stock decreases, and immutable self-owned orders. Manager permissions are defined separately in the canonical web policy.
- Keep menu and application settings read-only for kiosk clients.
- Permit stock values to decrease only and reject negative or nonnumeric values.
- Keep orders immutable after creation and limit each kiosk to its own order records.
- Remove a kiosk UID when its device is retired, reset, reinstalled, or lost.

The committed rules contain placeholder UIDs. Test deployment-specific rules locally without committing real identifiers:

```bash
cd firebase-tests
npm ci --ignore-scripts
npm audit --audit-level=high
KIOSK_UID_1='first-uid' KIOSK_UID_2='second-uid' npm run test:emulator
```

### Device configuration

- Provision release devices as Android device owners.
- Confirm that lock task is permitted and active before accepting orders.
- Confirm that backups, screenshots, overlays, system dialogs, and unrestricted navigation are blocked.
- Distribute release builds only. Debug builds allow development behavior and trust user-installed certificate authorities.
- Replace the default maintenance PIN before public deployment.

### Payments

The bundled QR is static. The application cannot verify whether a transfer succeeded, so `CUSTOMER_REPORTED_PAID` must be confirmed by staff before fulfillment.

Secret payment-provider keys must never be stored in Android source code, resources, build configuration, APKs, or Git history. Payment APIs that require secret credentials must be called from a trusted backend.

### Customer data

Order records may contain a customer name, kiosk UID, line items, total, payment method, payment status, and timestamp. Operators are responsible for:

- limiting Firebase console and IAM access;
- selecting a retention period;
- deleting expired records;
- handling valid deletion requests; and
- keeping customer data out of logs, screenshots, and GitHub issues.

Firebase Analytics and Crashlytics are not included in the application.

## Release checks

Before publishing a release:

```bash
bash scripts/security_scan.sh
./gradlew testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleRelease
```

Also run the Firebase Rules tests, inspect the merged release manifest, verify the merchant QR, and confirm that the APK contains no private API key or unexpected exported component.

App Check is not currently enabled. UID allowlisting restricts database access but does not prove that requests came from an untampered application. A trusted backend remains the recommended boundary for verified payments and authoritative inventory changes.

References:

- [Firebase Realtime Database Security Rules](https://firebase.google.com/docs/database/security)
- [Firebase Anonymous Authentication](https://firebase.google.com/docs/auth/android/anonymous-auth)
- [Firebase App Check for Android](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
