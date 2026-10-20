# Play Console worksheet — Lumen Studio local edition 1.06

Prepared for the first publication. Nothing has been submitted. The public identity/URL currently use example details at the user's request. Replace them before release; do not paste this worksheet into Play Console without reviewing the final signed build.

| Console section | Answer supported by this build | Still required |
| --- | --- | --- |
| App access | All app functionality is available without login. No test account or credentials are needed. | Give reviewers a working app and reproduction steps below. |
| Contains ads | No. No ad SDK or advertising identifier integration is included. | Reassess if monetization is added. |
| Privacy policy | Native Settings > Privacy policy, and a matching HTML document generated from the same JSON content. | Replace example developer name, email and HTTPS URL; host the HTML on a public, non-geofenced page and paste the real URL into Console. |
| Data safety: collected data | The inspected local edition does not transmit user data to a developer/SDK server. On-device media processing is outside Google's definition of collected data. | Review the final dependency/artifact inventory and any future services before declaring no collection. |
| Data safety: shared data | No automatic sharing/SDK transfer. Users may explicitly share their export through Android; user-initiated sharing can fall under Google's disclosure exception. | Reassess any automatic sharing, remote processing, crash-reporting or analytics added later. |
| Account creation/deletion | No account creation. No account-deletion flow is required for an account that does not exist. Local editing data can be cleared through Android app storage settings. | Reassess if accounts are added. Do not invent an account-deletion URL for this edition. |
| Photos/video permissions | No READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED, legacy external storage or all-files permission in the final manifest. Android Photo Picker / Open Document supplies chosen files. | Confirm this again in the uploaded bundle; do not claim or request broad-access approval. |
| Notifications | Optional generic local reminders, off by default. Android 13+ runtime permission requested only when enabled. | Verify opt-in/opt-out on the release device matrix. |
| Target audience | Not selected automatically. The source app is a general-purpose offline editor. | The owner must choose actual intended age groups and review Families obligations if children are included. Do not select a convenient age group merely to avoid a policy. |
| Content rating | Fill the IARC questionnaire truthfully. There is no public feed, in-app chat, or server-side user-content exchange; users edit their own media locally. | Complete the actual questionnaire; do not pre-claim an Everyone/PEGI rating. |
| App category | Suggested: Video Players & Editors. | Owner confirms category and relevant tags in the current Console. |
| Payments, government, health, financial features | No such features or in-app payments appear in this local build. | Answer any applicable Console declarations according to the final product; do not claim a regulated service. |
| Release artifact | SDK 36, production application ID com.lumen.studio. QA builds use com.lumen.studio.qa and must not be uploaded as the production app. | Generate an upload key later, sign the AAB and enroll in Play App Signing. No key was generated during this task. |

## Reviewer walkthrough

1. Start the app, open the Terms & Conditions in the offline WebView, check the agreement box on the first onboarding page, then skip/complete onboarding and create a project.
2. Choose media with Android's system picker; Browse files offers Open Document. Cancellation returns to the import screen without a permission wall. Chosen files can be removed or reordered before continuing.
3. Apply a filter, edit text, and observe the paused preview immediately. Press Play: edited compositions prepare a local playback file with progress and Cancel, then use standard video playback. Play/Pause, seeking, edits and retry should remain usable.
4. Export to an Android save-picker destination; play the result and reopen the saved draft.
5. Settings includes Privacy policy, Terms & Conditions, About Us and Contact Us. Delete drafts in Projects and sounds in the sound library; Android app storage settings remove all private data. Storage & data and Check video support are no longer Settings options.
6. Contact Us validates a reason and description, then opens an email draft when a real support address is configured. Example details in test editions block sending with a clear explanation. Email delivery must not be represented as successful merely because the email app opens.

## Before the first upload

Complete developer identity/account verification, set the real public privacy details, host the policy, choose audience/content rating, configure signing and run the device matrix. New personal accounts created after 13 November 2023 generally need 12 continuously opted-in testers for 14 days before applying for production access. Review any account-specific requirements displayed in Console.

Sources: [Data safety definitions and user-initiated sharing exception](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [app content and review declarations](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en), [signing](https://developer.android.com/studio/publish/app-signing), [testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en). The release owner remains responsible for accuracy.
