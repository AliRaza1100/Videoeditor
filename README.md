# Video Editor — Lumen Studio

Native Kotlin Android video/photo editor, with an optional web administration dashboard.

## Repository branches

| Branch | Contents |
| --- | --- |
| `main` | Repository guide and shared ignore rules. |
| `android-app` | Complete Android source, Gradle wrapper, tests and release configuration. |
| `backend` | Node/SQLite administration dashboard/API and clearly labelled legacy Firebase examples. |

Clone the branches into separate folders so the Android app and backend can run together:

```sh
git clone --branch android-app https://github.com/AliRaza1100/Videoeditor.git lumen-android
git clone --branch backend https://github.com/AliRaza1100/Videoeditor.git lumen-backend
```

## Android app

Current testing source: version 1.16 (version code 17), package `com.lumen.studio`.
Requires JDK 17+, Android SDK 36 and Android 8.0+ devices. Kotlin 2.3.10,
Android Gradle Plugin 8.13.2, Media3 1.8.0. Set your local Android SDK path in
`local.properties` or your environment; that machine-specific file is not committed.

```sh
./gradlew :app:assembleDebug
```

The default build excludes ad SDKs. To include the test advertising integration:

```sh
./gradlew :app:assembleDebug -PenableAds=true -PadminApiUrl=http://127.0.0.1:4173
```

For a USB phone, run `adb reverse tcp:4173 tcp:4173` while the local API is running.
The emulator uses `http://10.0.2.2:4173`. Debug builds use Google demo ad units.
Production requires a public HTTPS API, real AdMob application ID, network-account
mappings, correct consent messages, privacy disclosures and release signing.
See `docs/ADS-SETUP.md` in the Android branch or `admin-dashboard/ADS-SETUP.md`
in the backend branch. Adding a partner ID alone cannot configure AdMob mediation.

## Backend

Use Node.js 24+. The active backend has no npm runtime dependencies. From the
backend branch, run `admin-dashboard/Start-LumenAdmin.ps1` on Windows for first-run
credentials, or set `LUMEN_ADMIN_EMAIL` and `LUMEN_ADMIN_PASSWORD` in your local
environment, then run `node admin-dashboard/server.mjs`. Open http://127.0.0.1:4173.
Subsequent runs use the existing local SQLite administrator. Set a strong private
password; no administrator account or password is committed to this repository.

```sh
node --test admin-dashboard/tests/api.test.mjs
```

The server stores support text, opted-in installation counters and ad settings.
Videos, photos and projects stay on each user's device. Revenue reports are manual;
live earnings-reporting APIs are not connected. `backend/` contains legacy Firebase
reference files, not an active Firebase integration. Do not deploy them expecting
the current Android build to use FCM.

## Release and verification limits

No APKs, databases, local logs, real environment files, private keys, signing secrets
or generated build outputs are committed. Example public policy details must be
replaced before publishing. APK signing keys are configured privately using the
release helper. Source/API compilation and configuration tests passed; real-device
advertising, partner account readiness and production publishing require validation.
No license has been selected; no open-source license is implied.

Latest source: optional pre-export rewards at admin-selected resolutions, real 1080p, same-format backup routes and Hostinger deployment guidance in `docs/HOSTINGER-DEPLOYMENT.md`. This requires a new app build; the prior 1.15 APK is unchanged.
