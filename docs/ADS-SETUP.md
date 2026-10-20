# Export rules, backups and deployment update

See [HOSTINGER-DEPLOYMENT.md](HOSTINGER-DEPLOYMENT.md) for deployment files and the current pre-export flow. The update adds admin-selected export resolutions (including real 1080p), optional pre-export rewards and explicit primary/backup routes. It supersedes older statements below about adding these features later. The previously distributed 1.15 APK does not contain this update.

# Advertising setup — Lumen Studio

This supersedes the older launch-pack statements that all advertising work is only a plan. Source now includes an optional, real SDK integration. Previously distributed APKs remain SDK-free. No live network account or successful device ad impression has been verified.

## What the administrator controls

Open **Ad networks**, select the routes you will use, and save. In **Ad controls**, select a network route for each placement, enter its **mediated AdMob ad-unit ID**, enable the placement, save the draft and publish it. Each route retains its own unit ID when you switch networks. The format is fixed by the location; a banner cannot be changed into a fullscreen ad over an editor.

All routes use Google AdMob mediation: Google, Meta Audience Network, ironSource Ads, Unity Ads and AppLovin. This is not Unity LevelPlay mediation or AppLovin MAX. Partner placement IDs, game IDs and keys are mapped in the AdMob account, not pasted into the AdMob unit field. Never put account passwords, reporting credentials or private API keys into public ad configuration.

For a single selected network, create a dedicated AdMob unit and a matching mediation group containing that network. For multiple sources, configure a group containing the desired sources. The network selector chooses the corresponding unit; it cannot change AdMob's auction or remove a source from a mediation group. Account mappings determine the actual serving source. Meta uses bidding, so partnership approval and a bidding mapping are required.

## Placements and actual user actions

| Placement | Format | Trigger / location |
| --- | --- | --- |
| home_banner | Banner | Reserved footer above Home navigation, visible only when loaded. |
| projects_banner | Banner | Reserved footer above Projects navigation. |
| export_complete | Interstitial | Successful video export while its result panel is still visible. Skipped if the user moves on, load fails, frequency/cooldown/session checks fail, or configuration changes. |
| export_reward | Rewarded | Explicit “Watch ad · pause ads for 30 minutes” button in the completed-video-export panel. |
| preview_reward | Rewarded | Same explicit optional action in the video editor's export-options sheet. Playback, seeking and applying effects never trigger an ad. |
| app_open | App-open | Returning cold-launch splash only, with AdMob route. Skips onboarding and any ad that arrives after the loading screen is gone. No forced waiting period and no ad over loaded content. |

The reward is 30 minutes without new ad requests in the current process/session. It is granted only by the SDK reward callback. It ends on process restart. All editing and export resolutions remain available without watching an ad. Rewarded requests share fullscreen frequency limits. Photo exporting currently has no fullscreen advertising hook.

## What you must provide once

1. Host this Node/SQLite API on persistent storage behind HTTPS; point the Android build to its public origin, without `/api`. Firebase is not required.
2. Create/register the production app in AdMob and the partner accounts you want. Supply the real AdMob **application ID**, containing `~`, for the build. The application ID is in the Android manifest and cannot be replaced remotely. Ad-unit IDs contain `/` and can change remotely.
3. Create same-format AdMob units, mediation groups and partner mappings. Add the relevant partners to the AdMob privacy-message settings and configure required UMP messages, test devices, partner test mode and app-ads.txt on your developer website.
4. Review the new `privacy-ads.json`, publish the real public policy with your identity/contact details, and update Play's Ads, Advertising ID, Data safety and audience declarations. The SDK-free policy no longer describes an ads build. Audience/age treatment and every participating provider's regional obligations require review before launch.
5. Run device acceptance checks with test ads, including Ad Inspector's single-source tests for each partner. A compiled adapter alone does not prove a configured partner can serve an ad.

## Build configuration (no APK created by these examples until you run an assemble/bundle task)

SDK-free remains the default. To compile the integration using the Google sample app ID:

```powershell
./gradlew.bat :app:compileDebugKotlin -PenableAds=true -PadminApiUrl=https://admin.your-domain.com
```

The API requires a valid unit-shaped ID even in test mode. Before you have account IDs, use the matching [Google demo unit IDs](https://developers.google.com/admob/android/test-ads) in the draft: banner `ca-app-pub-3940256099942544/6300978111`, interstitial `ca-app-pub-3940256099942544/1033173712`, rewarded `ca-app-pub-3940256099942544/5224354917`, app-open `ca-app-pub-3940256099942544/9257395921`. Keep test mode enabled.

Debug builds, the sample application ID and dashboard test mode always use Google's sample ad-unit IDs. That safely tests the rendering path, but does **not** test third-party mediation; use registered test devices, partner test modes and Ad Inspector in an appropriately configured production-ID QA build for partner tests. Switching test mode off cannot force debug builds to request live units.

For a later USB-connected local phone test, use `adb reverse tcp:4173 tcp:4173` and build the debug edition with `-PenableAds=true -PadminApiUrl=http://127.0.0.1:4173`. The API must be running on this computer. The standard emulator uses `http://10.0.2.2:4173`. These HTTP addresses are debug-only; public releases require HTTPS. No device is currently connected and this mobile networking check has not been performed.

When explicitly ready for a signed release, the release helper accepts:

```powershell
./release/Build-PlayRelease.ps1 -StoreFile <existing-upload-keystore> -KeyAlias <alias> -AdminApiUrl https://admin.your-domain.com -EnableAds -AdmobAppId <real-admob-application-id> -AdsPolicyReviewed
```

The helper prompts locally for signing secrets. The last switch records your review of the policy and declarations; it does not perform that review or publish anything. No signing key has been generated.

## Confirming settings reach the app

The app fetches only **published** settings on foreground entry and every 60 seconds while foregrounded. It validates schema/version, allowed networks, placement formats, unit syntax and limits. Failed/malformed refreshes stop new ad requests; in-memory settings expire after 15 minutes. Off switches destroy banner slots on refresh and prevent new fullscreen requests. An ad already showing is allowed to finish normally. Network response time and background suspension mean updates are not instantaneous.

Reopen Settings in an ads build to see its last received version. **Ad controls → App configuration receipts** shows the version, app version, SDK-build flag and server receipt time for installations that opt into usage analytics. Opting out deletes these receipts; installations without analytics do not send them. A receipt proves settings delivery, not SDK initialization, an impression or revenue. Existing manually entered revenue reports remain manual; no network earnings API is connected.

## SDK and lifecycle behavior

Optional dependencies: Google Mobile Ads 25.5.0, UMP 4.0.0, Meta adapter 6.22.0.1, ironSource adapter 9.6.1.0, Unity adapter 4.21.0.0, AppLovin adapter 13.6.4.2. Kotlin 2.3.10 and AGP 8.13.2 support the current SDK metadata. No dynamic code downloads or remote SDK installation is used.

Consent is requested on Home/Main before SDK initialization, not inside the editing workflow. Required privacy options are available in Settings. UMP consent signals are read by current partner adapters; Google requests additionally use non-personalized/restricted-data-processing settings. Conservative partner US defaults set AppLovin do-not-sell, Meta LDU, ironSource do-not-sell and Unity privacy consent false. These do not eliminate the need for correct account messages and privacy declarations. Banner views are destroyed when detached. SDK loads use Activity context; fullscreen callbacks recheck the current configuration and visible destination.

Official references, checked 20 October 2026:

- [AdMob setup](https://developers.google.com/admob/android/quick-start)
- [UMP](https://developers.google.com/admob/android/privacy)
- [Meta bidding](https://developers.google.com/admob/android/mediation/meta)
- [ironSource](https://developers.google.com/admob/android/mediation/ironsource)
- [Unity Ads](https://developers.google.com/admob/android/mediation/unity)
- [AppLovin](https://developers.google.com/admob/android/mediation/applovin)
