# Deploying Lumen Studio on Hostinger

Deploy branch **backend**, directory **admin-dashboard** from:
https://github.com/AliRaza1100/Videoeditor/tree/backend

Do not upload an APK or the Android source to a web host. The `backend/` Firebase
example directory is legacy and is not the running dashboard/API.

## Hosting requirements

The backend needs Node.js 24, a continuously running Node process, HTTPS, and a
writable persistent local disk for SQLite. It has no npm runtime dependencies.
Hostinger Business/Cloud Node.js web-app hosting supports Node deployments;
confirm Node 24 and database-file persistence across deploys/restarts for the
specific plan. A static-only/PHP-only upload cannot run this API.

For this SQLite implementation, a Hostinger Linux VPS with a persistent disk is
the predictable deployment route. The supplied systemd and Nginx files target a
Linux VPS. They are templates, not an actual completed deployment. No hosting or
domain has been purchased, and these Linux deployment files have not been run on
a VPS. Use one backend process; do not scale SQLite replicas across multiple hosts.

## VPS deployment

1. Point `admin.your-domain.com` DNS at the VPS. Install Node.js 24 from its
   official distribution, Git, Nginx and Certbot using your operating system's
   supported package instructions. Verify `node --version` and `node -p
   "require('node:sqlite').DatabaseSync.name"`. The service template expects Node
   at `/usr/bin/node`; update ExecStart if your installation uses another path.
2. Clone **backend** under `/opt/lumen`:

   ```sh
   git clone --branch backend --single-branch https://github.com/AliRaza1100/Videoeditor.git /opt/lumen
   ```

3. Create a system user/group named `lumen`, without an interactive login. Keep
   source owned by the deployment/root account and readable by `lumen`.
4. Copy `deploy/production.env.example` to `/etc/lumen-admin.env`. Replace the
   origin with the exact HTTPS origin, with no trailing slash, choose your actual
   administrator email and a unique long random password (minimum 14 characters).
   Make the real environment file root-owned, mode 0600. Never commit it. This
   creates the first administrator only; changing the environment does not reset
   an existing database's administrator password. Do not upload the local test DB.
5. Install `deploy/lumen-admin.service` into `/etc/systemd/system/`, run
   `systemctl daemon-reload`, then `systemctl enable --now lumen-admin`. systemd
   creates `/var/lib/lumen-admin` for SQLite outside the application checkout.
6. Install `deploy/nginx.conf` into the Nginx site configuration, replace the
   example hostname, enable the site and validate with `nginx -t`. Obtain a valid
   certificate using `certbot --nginx -d admin.your-domain.com` and enable HTTPS
   redirection. Sign in only through HTTPS. Expose ports 80/443, not backend 4173.
   Check your distribution's existing default-site configuration to avoid overlap.
7. Verify `https://admin.your-domain.com/api/health` returns `ok: true`, sign in
   and run the API tests from the source directory. Publish a test-mode ad draft.
   Set the real in-app/public privacy policy before a public application release.

`TRUST_LOOPBACK_PROXY=true` is for the supplied Nginx topology: Node listens only
on loopback and Nginx overwrites `X-Real-IP`. The API trusts that header only from
a loopback peer and only if it contains a valid IP. Leave this switch false for
other proxy topologies until their trusted-peer rules are explicitly configured.
`PUBLIC_ORIGIN` pins administrator write requests to your configured origin.

Back up SQLite consistently using its online backup facilities or a stopped-
service copy; a live WAL database must not be backed up by copying the main file
alone. Test restores. Keep backups private. Updates use `git pull --ff-only` and
`systemctl restart lumen-admin`; they must preserve `/var/lib/lumen-admin`.

## Managed Node.js web-app alternative

Select repository `AliRaza1100/Videoeditor`, branch `backend`, app root
`admin-dashboard`, Node 24, start command `npm start` (`node server.mjs`), with no
frontend build required. Use the host-provided port and bind host required by
that platform (often `HOST=0.0.0.0` behind its proxy). Set the same production
environment fields using hPanel's environment settings, with `LUMEN_DATABASE`
pointing at a **confirmed persistent writable path outside ephemeral release
files**. The bundled VPS systemd/Nginx files are not used on managed hosting.

Do not assume file persistence or the platform's proxy-IP behavior. Confirm them
with Hostinger first. If the plan cannot supply persistent SQLite storage or
Node 24, choose a suitable VPS or migrate the database implementation before
deploying; creating a MySQL/Firebase account alone does not convert this SQLite API.

## Connect the Android app once

Build the ads edition with `-PenableAds=true`,
`-PadminApiUrl=https://admin.your-domain.com`, and
`-PadmobAppId=YOUR_REAL_ADMOB_APPLICATION_ID`. Signing, real privacy details and
disclosure review are required for Play release. The already shared 1.15 APK
uses a local USB-only API address and Google demo IDs; hosting the backend does
not remotely rewrite that APK's API origin or application ID.

After the hosted build is installed, publish unit IDs and switches from the
dashboard. Foreground apps refresh about every minute, subject to connectivity;
analytics-enabled installations report their received configuration version.
No new APK is required for those supported placement/unit/rule changes. New ad
SDKs, unsupported formats or new screen hooks still require an app update.

## Advertising accounts and fallback

Create AdMob and at least one partner account, such as Meta or ironSource.
Complete app/placement approval, payment setup, mediation/bidding agreements,
partner mappings, applicable consent messages and app-ads.txt. The AdMob
application ID is fixed in the Android build. **Mediated AdMob unit IDs** go in
the dashboard; partner placement IDs/game IDs/keys go in their AdMob mappings.
Never put private reporting tokens or account passwords in public ad config.

For banners, export interstitials and rewards, select different primary and
backup network routes, with separate same-format AdMob units. Configure the
matching source groups in AdMob; route labels alone cannot change its auction.
The app tries the backup once on no-fill/network-load errors, and rechecks current
config and lifecycle state. It does not retry invalid requests or replace a
rewarded ad with an interstitial. Both can fail: no ad or earnings is guaranteed.
Google demo units serve Google test ads, so actual partner fallback must be
tested with real account mappings, registered test devices and partner test modes.

App-open uses the appropriate launch format while the launch screen is visible,
skips first onboarding and late arrivals, and cannot become a surprise
interstitial over Home. Its available demand sources are format-dependent;
unsupported partners cannot be used merely to meet a two-network minimum.

## Administrator-controlled export rules

In Ad controls → Video export screens, enable the optional pre-export reward
and select resolutions, initially 480p, 1080p and 2000p. Configure and enable the
Export reward placement and both routes; save and publish. The app offers:

- Watch ad & export: earn 30 ad-free minutes in the current session if the SDK
  reward callback occurs, then choose the export destination.
- Export normally: continue without watching or earning a reward.
- Back: return without exporting.

No-fill, cancellation and skipped ads never remove normal editing/export access.
No new ad interrupts active encoding. A separate frequency-limited interstitial
may show after a successful video export if that placement is enabled and the
result panel is still visible. Export settings are applied at the next export
menu opening. An already earned reward is honored even if config changes later.

Sources checked 20 October 2026:
- https://www.hostinger.com/support/node-js-hosting-options-at-hostinger/
- https://support.google.com/admob/answer/7313578?hl=en
- https://support.google.com/admob/answer/6201362?hl=en
