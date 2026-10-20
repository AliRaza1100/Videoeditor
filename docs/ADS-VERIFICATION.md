# Multi-network advertising verification — 20 October 2026

## Implemented

- Optional SDK-enabled build with Google AdMob, Meta, ironSource, Unity Ads and AppLovin mediation adapters, UMP and native banner/interstitial/rewarded/app-open rendering paths. SDK-free build remains default.
- Published versioned configuration, per-placement selected network and retained per-network AdMob unit IDs; strict server and Android model validation. No arbitrary formats over editing controls.
- Foreground refresh every 60 seconds; failure disables new requests; 15-minute in-memory expiry; no stale persisted configuration automatically used for serving ads.
- Optional analytics-gated receipts, authenticated per installation; unknown versions rejected; analytics opt-out deletes receipts; installation deletion cascades.
- Banner detach/destroy, stale callback guards, fullscreen frequency/cooldown/session limits, SDK impression callbacks, optional 30-minute session reward, no ad interruption during editing/export processing.
- Advertising policy variant, Settings privacy choices, and release helper's explicit advertising configuration and disclosure-review checks.

## Verification completed

- Three isolated Node API tests passed, covering existing authentication/support isolation, network validation, route/unit round-trip, publish/rollback, receipt authentication, unknown-version rejection and opt-out deletion.
- The actual running dashboard's public configuration v2 was parsed by the compiled Android `AdsConfiguration` model in a JVM contract test. The valid five-network fixture and eleven malformed configuration cases passed (`tests/AdsConfigurationChecks.java`).
- Browser inspection confirmed five network cards, per-placement network selectors, ID fields, format restrictions, frequency controls and the receipt panel. No production configuration was published or changed for the UI inspection.
- SDK-enabled Kotlin source and Android test source compiled. Added instrumentation parser cases were compiled, not executed on a device.
- Release PowerShell syntax, advertising manifest XML and policy JSON parsed successfully.
- Final ads-enabled Kotlin compile passed after the callback/banner fixes. SDK-free Kotlin compile passed.
- Ads-enabled lint passed with zero errors (265 warnings, including existing application-wide warnings).
- SDK-free merged manifest excludes the AdMob application metadata and Advertising ID permission.

## Limits

No phone is connected. No physical-device/emulator ad load, impression, reward, consent form or third-party single-source test has been performed. SDK integration and API contract verification do not establish live account readiness or ad fill. Existing distributed APKs remain unchanged; no APK/AAB/signing key was generated. Live ad delivery requires a newly requested ads-enabled build, public HTTPS API, real app ID, network accounts/mappings, consent configuration, policy/declarations and device acceptance checks. Network switches select mediated units; actual ad sources are controlled by the associated AdMob mediation groups.

See `admin-dashboard/ADS-SETUP.md` for the owner setup steps and exact placement behavior.

Ads-enabled lint: passed (0 errors, 265 warnings). The report includes existing application-wide warnings. No device instrumentation execution or APK generation was performed.
