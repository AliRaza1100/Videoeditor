# Legacy Firebase reminder example — not connected

This directory is a reference example, not the application's active backend. The current app uses local opt-in reminders and the Node/SQLite API in `admin-dashboard`. Its Gradle configuration deliberately rejects `-PenableFirebase=true`; adding `google-services.json` does not integrate Firebase. Do not deploy this example expecting the current Android build to subscribe to it. A separate FCM implementation, privacy update, billing review and device testing would be required.

The older notes below describe a proposed Firebase edition, not implemented current behavior.

## Original proposed three-hour backend reminders

The default Android build uses local, inexact, opt-in reminders. No server is contacted until Firebase support is explicitly enabled and configured.

To enable remote delivery:
1. Create a Firebase project and register Android package `com.lumen.studio`.
2. Download its non-secret client configuration to `app/google-services.json`.
3. Build with `-PenableFirebase=true`. This adds FCM and language-topic subscriptions; local reminders are disabled to prevent duplicates.
4. Enable billing for scheduled Cloud Functions/Cloud Scheduler. Review current pricing before deployment.
5. In this directory, install Firebase CLI and functions dependencies, select the Firebase project, and deploy `functions:editingReminder`.
6. Enable reminders in app Settings and grant Android notification permission. Test on a Google Play services device.

The schedule sends every three hours at 00:00, 03:00, … in Asia/Karachi, including overnight. Delivery is not exact: device sleep, connectivity, permission denial and force-stop can delay or prevent it. Data-only messages honor app opt-out at display time and avoid duplicate foreground/background notifications. Public language topics contain generic editing reminders; never send private user data to topics. Firebase Admin credentials stay in the managed backend and must not be bundled into Android or shared in chat.

Optional Firebase files and deployment are not runtime-tested without a project, dependency download, and a device. No cloud resources have been created or deployed.
