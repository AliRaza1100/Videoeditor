# Multi-network advertising update

See [ADS-SETUP.md](ADS-SETUP.md) for the current optional SDK build, network routing, receipts and account setup. It supersedes older statements below describing ads as planning-only. Previously delivered APKs still have no ad SDK.

# Lumen Studio admin — local edition

A working local web dashboard and Node.js API backed by SQLite. No npm dependencies, paid hosting, domain, Firebase project or media storage account is needed to run it. Node.js 24+ is required (the current Node built-in SQLite module emits an experimental API warning).

## Start

From PowerShell:

```powershell
cd D:\CV\Editor\admin-dashboard
.\Start-LumenAdmin.ps1
```

Open <http://127.0.0.1:4173>. On this computer, the administrator email supplied in the conversation is already seeded with the supplied local test password. The database stores a random salt and scrypt hash, not the plaintext password. The email is also configured as the app support address in `release/public.properties`. This does not create or sign in to a Google or Firebase account.

The first run on another computer prompts for an email and password. Existing installations can simply run `npm start` or `node server.mjs`. Keep the process running while using the local dashboard. Stop with Ctrl+C. The database is `data/lumen.sqlite`; that directory is excluded from source control. Back up the database using SQLite's supported online-backup tooling or a consistent stopped-server copy; do not copy only the main file during a WAL write.

## What works

- Administrator sign-in/sign-out with expiring HttpOnly/SameSite cookies, salted password hashes, origin checks and request rate limits.
- Empty-by-default overview: consenting installations, activity within seven days, completed exports today, daily export chart, feature counters and support totals. Dates/reporting use UTC.
- Searchable installation inventory with server pagination, model, brand, API level, density, configured locale/region, app version and first/last seen. IDs identify installations, not people or permanent hardware.
- Ad configuration drafts, validation, publish, history, rollback and administrator audit records. The API limits each placement to a supported format and validates AdMob unit IDs.
- Manual daily AdMob revenue-report entries. Estimated earnings and impressions are report values entered by an administrator, not fabricated estimates based on app actions. No live reporting credentials or AdMob integration are configured.
- Text-only support ticket inbox, administrator replies and status changes. App installations can retrieve only their own conversations. No SMS service or media upload endpoint exists.
- Optional installation analytics and a bounded Android offline event queue. Event IDs prevent duplicate counts on retry. Analytics opt-out removes analytics/profile data; online-data deletion removes the installation and its associated support/usage records.

## Connect an Android test build

The already shared 1.11 APK has no admin API URL and remains unchanged. Source integration lives in `AdminConnection.kt`, `SupportInboxActivity.kt`, Settings and Contact Us. No APK was generated for this dashboard request.

For an emulator, configure `-PadminApiUrl=http://10.0.2.2:4173` when compiling a debug build. For a USB-connected physical phone:

```powershell
adb reverse tcp:4173 tcp:4173
```

Configure `-PadminApiUrl=http://127.0.0.1:4173` for that debug build. When packaging is explicitly requested, use the normal app build command with this property. These loopback HTTP exceptions exist only in debug resources; public releases require HTTPS. The server binds loopback, so this does not expose the local test admin password on the LAN.

In the connected app:

1. Accept the connected edition's updated terms.
2. Open Settings > Contact Us; send a reason and description.
3. Open Support inbox for replies. No analytics opt-in is required for support.
4. Enable Share usage analytics if you want the installation and editing counters to appear. It is off by default and separately disclosed.
5. Complete an export; its event is queued only after the file was successfully saved. Image edit statistics count edit starts, not completed image exports.

Network sync runs off the rendering/UI threads. Offline usage is capped at 1,000 queued events and retried on app opens or subsequent recorded events. Request batches contain at most 100 events. Remote config is fetched on activity creation and cached; it is not a realtime push channel.

## Ads: configuration versus delivery

| Placement | Allowed format / behavior |
| --- | --- |
| Home, Projects | Banner, placed away from editing controls by a future SDK adapter |
| Completed export | Interstitial at the natural completion break, subject to frequency limits |
| Export reward | Explicit rewarded-ad button with the reward described before viewing |
| Preview reward | Explicit rewarded-ad button; never interrupt preview playback or editing |
| Returning app launch | App-open ad on an eligible loading experience, not a launch interstitial |

The dashboard stores and publishes real configuration, but the current Android app has **no AdMob SDK**. The API truthfully returns `effective_ads_enabled: false`. Installing or enabling a network from the dashboard cannot add its SDK to an APK. AdMob delivery, consent handling, reward verification and actual UI placements require a separate ad-enabled Android integration and tests. This edition does not pretend ads are serving. Live reporting requires a server-side AdMob reporting integration; until then Revenue supports clearly labelled manual reports.

## Why a database?

It remembers installation IDs, counters, support text, replies and published settings after the server or browser closes. It contains **no videos, photos, audio files or editing projects**. SQLite has no licence/subscription charge; hosting a public always-on API is a separate infrastructure decision.

Firestore is an alternative hosted database with a no-cost quota. Firebase Authentication and Hosting may also fit a small project's free-tier usage. A Firebase project alone does not run this Node/SQLite API or migrate its tables. A Firestore persistence adapter plus Firebase Authentication migration would be required if choosing that architecture. Firebase Functions deployment requires the billing-enabled Blaze plan, even where its usage remains within no-cost allowances. No Firebase credentials are embedded here and no account was created. A paid custom domain is optional; hosting providers may provide their own subdomain.

Sources checked 20 October 2026: [Firestore free quota/pricing](https://firebase.google.com/docs/firestore/pricing), [Firebase dynamic hosting and billing](https://firebase.google.com/docs/hosting/serverless-overview), [AdMob interstitial placement rules](https://support.google.com/admob/answer/6201362), [app-open guidance](https://support.google.com/admob/answer/9341964), [rewarded format](https://support.google.com/admob/answer/7372450).

## Data retention and release preparation

Server cleanup runs while the server is running: detailed events expire after 90 days, daily totals after 365 days, support conversations after 180 days without an update, inactive installations after 365 days without an authenticated request. Those are explicit defaults, not a claim that every business must use these periods. Completed deletions remove live database records; backup retention must be defined separately when backups are enabled.

Connected privacy/terms assets are separate from the offline edition and selected by `adminApiUrl`. Publishing documents with `preparePlayDocuments` selects the matching policy. Keep the hosted policy and Data safety declarations aligned with the final build and hosting logs. Real developer identity/public policy hosting remain unfinished. The current test email does not establish a verified public developer identity.

Before public deployment: replace the weak local test account; the production server rejects the supplied test password. Configure HTTPS/reverse proxy, a strong administrator account and preferably an identity provider with MFA, stable persistent storage, a documented backup/deletion policy, edge rate limiting and appropriate logging. Do not expose SQLite files or service-account/reporting secrets to the browser or APK. The legacy `backend/functions` reminders example is separate and is not part of this dashboard.

For a fresh production account, point `LUMEN_DATABASE` at a new production database path and set a strong `LUMEN_ADMIN_PASSWORD` plus `LUMEN_ADMIN_EMAIL` before its first start. Changing the bootstrap environment variables does not change an existing account. Preserve the development database separately; do not overwrite or expose it as a production account database.

## Checks

```powershell
node --test tests/api.test.mjs
```

The integration suite exercises auth/origin checks, anonymous credentials, analytics consent, deduplication, installation isolation, support replies, draft conflicts, config publishing/rollback, report entries, opt-out and deletion using an in-memory test database. It never seeds fake production installs or earnings.

## Ad sources and placement map

Ad networks saves requested integrations for AdMob, AppLovin, Unity Ads and Meta Audience Network in the versioned draft. These are integration plans, not SDK switches. Native SDKs, adapters, accounts and consent handling are still required. AdMob controls partner bidding/waterfalls; this dashboard does not set their auction priority.

Placement map lists each proposed screen, user action, location and display rule, alongside draft/published status. Every native hook is currently pending. Installation means one app copy on a device; counts include only installations that consent to usage analytics.
