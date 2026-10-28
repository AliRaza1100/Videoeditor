# Both QA lists — implementation record, 28 October 2026

This record supersedes the incomplete suggestion findings in verification-audit-2026-10-28.md. It covers the supplied suggestion sheet and all 12 bug rows, including both Bug_04 entries. Implemented means present in source; device sign-off is tracked separately.

| QA area | Implementation |
| --- | --- |
| Home | Full-width 16:9 bundled silent editing video, looping with pause and reduced-motion poster; plus/right-arrow New Project; selected navigation; cyan dark accent. |
| Projects | Correct photo/video badges; clean sort text; photo-only selections open Photo Editor. |
| Support/legal | Shared back toolbar; inbox list/status/replies/new request/refresh/empty state; Delete online data moved into inbox. Offline state explains unavailable service. |
| Photo editing | Aligned Undo/Redo, white crop guides/handles, fixed Done, selected crop ratios; remove final photo, Undo and add again; tappable plus on empty collage cells. |
| Photo text | Separated short-text handles; tight adjustable background padding; unified Size/Opacity, Outline/Shadow, Spacing/Rotation and alignment controls; font/style previews, bold/italic/underline, checkmarked selection; text/background swatches and custom HEX/RGB mixer with live preview. |
| Photo filters/canvas | Categorized preview cards with readable names/checkmarks; grouped ratio, position and background options; gradient previews and selection. |
| Video controls | Fixed Done on editing panels; selected animation/filter/ratio/crop cards; seven adjustments with values and individual Reset, including preview/export sharpness. |
| Video sound | Source mute in left video rail; soundtrack mute in music rail; playback seekbar row cleaned up; Add sound stays available with existing soundtracks. |
| Speed | Coordinated Media3 audio/video speed effects; rate presets/preview; preserve current source frame across rate edits; regression checks 4x duration and first/last source content with and without audio. |
| Reminders | Interactive row/switch and persistent preference. |
| Themes | Shared semantic colors and selection styling support gold, light, dark and violet; existing sound/export reference design retained. |

## Validation

- QA app and instrumentation APKs: build successful with offline Gradle, qaBuild=true, enableAds=false.
- Support API tests: 5 passed against an isolated local test database. No real user support messages or deletions submitted.
- Earlier isolated text background/padding instrumentation test: 1 passed.
- Latest ten-test Android run aborted before the first assertion completed: runner reported Process crashed. Android logs also show system-app startup ANRs and severe resource pressure. This is not a passing result.
- Initial retries exposed two test-harness issues: a nullable contentDescription assertion and an accessibility query that could not locate the removal dialog. Both were corrected; removal now inspects the app's dialog window directly.
- Final ten-test suite: 9 passed, 1 confirmation-query failure. After correcting that test query, the isolated crop/handles/removal/Undo regression PASSED (1 test, 13.07 seconds). All 10 regression checks have therefore passed across the suite and isolated retest; no single clean ten-test rerun was performed after the test-only correction.
- Passed coverage: four-theme color selection/custom mixer; unified photo controls/fixed Done/collage shortcuts; video mute rail and seven adjustment resets; 4x export duration and retained first/last content with and without audio (also exercises sharpness); Home highlight/sort/project badges across themes; Add sound; crop/short-text handles/last-photo removal/Undo; photo-only routing/reminder persistence; tight highlight/padding; combined formatting persistence/rendering.
- Home visual review completed in gold theme. Fixed the Pause label contrast and rebuilt successfully. A full manual visual/device QA pass across every editing screen and theme remains recommended.
- Evidence: qa-final-device-results-2026-10-28.txt (9/10), qa-photo-final-results-2026-10-28.txt (isolated pass), support-api-test-results-2026-10-28.txt (5/5).
- Live support requires the deployment ADMIN_API_BASE_URL; the default QA build intentionally has no production endpoint.

## Reproduction

Build: `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain -PqaBuild=true -PenableAds=false --max-workers=2`

Device suite: `adb shell am instrument -w -r -e class com.lumen.studio.QACompletionTest,com.lumen.studio.BugSheetRegressionTest,com.lumen.studio.PhotoWorkspaceTest#testCombinedTextFormattingSurvivesDraftAndChangesRenderedPixels com.lumen.studio.qa.test/android.test.InstrumentationTestRunner`
