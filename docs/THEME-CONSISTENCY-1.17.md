# Lumen Studio 1.17 — theme consistency

Settings, Projects, Theme and Language all use their shared native view implementations under Gold, Light, Dark and Violet. Structure, actions, filters, selection cards, radio indicators, scroll access and Apply controls do not branch by theme. Gold-specific constants affect colors only. Light retains its white background and black primary text; Dark uses dark surfaces; Violet uses its existing semantic palette.

Checked 32 text/surface color combinations derived from the actual ThemeColors source and Settings/ReferenceSurfaces colors. All passed 4.5:1 contrast. Minimum ratios: Gold 4.74, Light 11.59, Dark 5.11, Violet 5.39. These numerical checks do not prove device layout, gesture behavior, accessibility traversal or rendering quality.

Notification switch tracks were adjusted to keep the white thumb clear in both states. Eight additional thumb/track combinations across the four themes passed 3:1 non-text contrast (minimum 5.11:1).

Ads-enabled assembleDebug and compileDebugAndroidTestKotlin passed. Final assembly after the switch change passed (1m43s); compiled Settings bytecode was checked for the new dark track colors. The APK package/version and v2 signature were verified; the signer matches previous testing versions. Instrumentation tests were compiled but not executed. Existing compiler deprecation warnings remain.

The testing release is version 1.17, versionCode 18, package com.lumen.studio. It retains demo advertising, the existing testing signing identity and the local USB admin endpoint http://127.0.0.1:4173. It is not the signed Play Store production artifact. There is no new hosted backend deployment.

No phone/emulator execution is claimed. The user declined reconnecting the phone after the interrupted screenshot session. Physical-device visual/navigation checks, mediation delivery and store screenshot capture remain pending. Phone display restoration remains documented in SETTINGS-REDESIGN-2026-10-20.md.
