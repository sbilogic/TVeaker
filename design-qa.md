# Design QA

- Source visual truth: `C:\Users\Sahil\.codex\generated_images\01a04a41-c447-7071-9da6-cb0ef43b2da1\exec-5350f5f3-0a5c-4b08-b651-b76b6e093fba.png`
- Web implementation evidence: `design-qa/web-desktop-light.png`, `design-qa/web-desktop-dark.png`, `design-qa/web-mobile-light.png`, and `design-qa/web-mobile-dark.png`
- Android implementation evidence: `design-qa/android-final-v7-home-light.png`, `design-qa/android-final-v7-home-dark.png`, `design-qa/android-final-v7-shows-light.png`, `design-qa/android-final-v7-shows-dark.png`, `design-qa/android-final-v7-discover-light.png`, `design-qa/android-final-v7-discover-dark.png`, `design-qa/android-final-v7-settings-light.png`, and `design-qa/android-final-v7-settings-dark.png`
- Source pixels: 864 x 1856, normalized to 393 x 844 for comparison.
- Android pixels: 1080 x 2400 on Medium Phone API 36.
- State: populated dashboard, light and dark themes; OTA, sync, resume, recommendation, navigation, gateway, and theme controls present.
- Exact release artifact: `design-qa/tveaker-build7.apk` — version code 7, version name 1.5.0, SHA-256 `23080F967098C54F475448992BCBCDB59EDF03291FF95BA78A3BC0FA602C1DA0`.
- Latest compact-mode verification artifact: `design-qa/tveaker-build8.apk` — version code 7, version name 1.5.0, SHA-256 `23F62E8CFCC61C15FECD0106E4DFA91900B08AD58B48EFBADB2416F8B9F0B186`.
- Latest dense-compact verification artifact: `design-qa/tveaker-build9.apk` — version code 7, version name 1.5.0, SHA-256 `C15917FF7317FDB1CA8228ED5481D7AA31377B85A3C0E1CE7A364B40597BC092`.
- OTA evidence: `ota-final-build6-update-available.png`, `ota-final-build6-progress-final.png`, `ota-final-build6-installer-final.png`, `ota-final-build6-playprotect-final.png`, `ota-final-build6-install-result.png`, and `ota-final-build7-installed-home.png`.

## Findings

No P0, P1, or P2 findings remain for the verified scope.

- Typography: condensed display hierarchy, serif editorial copy, compact labels, and optical weights follow the selected reference.
- Spacing: mobile headline, chapter rule, two-column focus spread, compact recommendation, indexed desktop rail, and persistent phone navigation follow the reference rhythm.
- Colors: warm paper/near-black themes and cobalt signal color are consistent across web and Android, including system status/navigation bars.
- Shared frame: Home, Library, Discover, and Settings now use the same editorial page language on web and Android. Legacy web controls were retokenized so light mode does not render white-on-paper text.
- Imagery: production uses live provider artwork at original resolution. The exact face crop differs from the generated mock because the mock contains bespoke poster art; this is an intentional P3 data-asset constraint, not a placeholder.
- Copy: core reference hierarchy is preserved. Live forecast dates replace mock-only labels.

Focused comparison covered the headline, focus spread, recommendation ledger, Library table/cards, Discover filters/cards, Settings/OTA surfaces, and bottom navigation.

## Bug pass — 2026-08-30

- Android Home no longer nests a second `Scaffold`, removing the duplicate top system inset. Measured UI bounds moved from approximately y=205 to y=142 for the wordmark and y=304 to y=241 for `TONIGHT`.
- The Android bottom bar now consumes the system navigation inset. In three-button navigation mode, the app bar ends at y=2248 while the system bar begins at y=2274, leaving the labels readable.
- Android tab selection is derived from the navigation back stack, so system Back returns the selected state to Home instead of leaving the previous tab highlighted.
- Android status, quick-action, dismiss, filter, gateway, and sync controls now meet a 48dp minimum touch target where the prior implementation used 28–42dp controls.
- Web Library switches to labeled cards at narrow widths; Discover and Settings stack their controls and banners. At both 382px and 320px viewports, Home, Library, Discover, and Settings measured `scrollWidth == innerWidth` with no horizontal overflow.
- Web episode drawers and pace dialogs now use the same light/dark editorial tokens instead of hard-coded dark inline styles. Fresh browser checks completed with zero console errors.
- Android startup now prioritizes show estimates before recommendations and version metadata, so Home becomes useful as soon as its primary data arrives instead of waiting for the full batch. The launch preview also uses a branded TVeaker mark rather than the default Android robot.
- Compact mode is now a persisted display-density preference on web and Android. It tightens page rhythm, cards, imagery, tables, and dashboard spacing while preserving accessible touch targets; web checks held at 382px with no horizontal overflow, and Android Settings/Home were visually checked before and after relaunch.
- Dense compact refinement makes the mode materially tighter: desktop/mobile mastheads, focus artwork, forecast blocks, recommendation ledgers, queue rows, and secondary-route cards now reduce visual footprint while interactive rows remain usable. Web responsive review at 320px and 382px measured no horizontal overflow; Android Home and Settings were visually checked with the new artifact, including the persisted switch state and a passing connected UI test.

Bug-pass evidence: `design-qa/bug-pass-android-splash-branded.png`, `design-qa/bug-pass-android-home-priority-4_5s.png`, `design-qa/bug-pass-android-home-final.png`, `design-qa/bug-pass-android-home-light-final.png`, `design-qa/bug-pass-android-shows-final.png`, `design-qa/bug-pass-android-discover-light-final.png`, `design-qa/bug-pass-android-settings-light-final.png`, `design-qa/bug-pass-android-home-density-tight.png`, `design-qa/bug-pass-android-settings-density-tight.png`, `design-qa/bug-pass-web-home-mobile-final.png`, `design-qa/bug-pass-web-shows-mobile-final.png`, `design-qa/bug-pass-web-recommendations-mobile-final.png`, and `design-qa/bug-pass-web-settings-mobile-final.png`.

## Comparison history

1. Initial implementation: P1 generic percentage dashboard, short poster, recommendation below fold, and five-item numeric navigation.
2. First correction: selected editorial hierarchy, tall two-column feature, compact recommendation, four-item icon navigation, and responsive light/dark themes.
3. Precision pass: narrowed and repositioned the display headline, aligned the poster/data grid, corrected navigation icon weight, fixed native Android headline clipping/title truncation/date formatting, and restored the OTA entry point.
4. Consistency pass: migrated Android secondary routes to the shared editorial frame, removed duplicate startup fetches, parallelized home requests, added emulator gateway detection, and aligned the web secondary routes to the same tokens.
5. Final evidence: source, web, and Android surfaces were visually inspected in light/dark states; the exact final APK passed the OTA upgrade path and the release test suite.

## Interaction checks

- Theme toggle: passed in light and dark modes on web and Android.
- Density toggle: passed on web and Android; compact state persisted across Android relaunch and can be returned to comfortable mode from Settings.
- Primary navigation links/routes: present and correctly targeted.
- Resume, add-to-watchlist, sync, status/filter controls, gateway presets, and OTA controls: present and enabled.
- Browser console: no warnings or errors during responsive checks.
- Startup behavior: Android view models are lazy per route, duplicate initial fetches are removed, and the home data requests run in parallel.
- Emulator gateway: fresh emulator installs default to `10.0.2.2:8000`; physical devices retain the LAN preset.
- OTA: build 6 was installed, the running backend offered build 7, download reached completion, Android's installer and first-run Play Protect scan were exercised, the app was installed successfully, and the final installed package reports version code 7 / version name 1.5.0.
- Test suite: web `80 passed`; Android unit tests passed; connected Android UI test passed on `Medium_Phone_API_36.0(AVD) - 16`.

## Follow-up polish

- P3: provider poster crops vary by title; a future artwork-art-direction service could select portrait crops per show.

## Secondary-page redesign — 2026-08-30

- Replaced the remaining legacy web surfaces with one shared editorial archive system: poster-led Library rows, a featured Discover pick plus compact recommendation ledger, a readable History ledger, and a rule-based Settings control room.
- Added History to the primary web navigation and fixed its light-theme title contrast.
- Rebuilt Android Library, Discover, and Settings around the same borderless editorial rhythm, materially reduced top padding and card height, and preserved 48dp action targets.
- Added a trusted-host artwork relay through the LAN backend so Android artwork remains visible even when the emulator or phone cannot reach TVMaze directly. The relay only accepts HTTPS images from `static.tvmaze.com`.
- Verified populated Library and Discover states on the Medium Phone API 36 emulator in light, dark, compact, and comfortable modes. Verified web Library, Discover, History, and Settings in light and dark themes against the approved Home visual language.
- Final evidence: `design-qa/old-ui-audit-2026-08-30/final-web-library-light.png`, `final-web-library-dark.png`, `final-web-discover-light.png`, `final-web-history-light.png`, `final-web-settings-light.png`, `after-android-library-art.png`, `after-android-discover-art.png`, `final-android-library-dark.png`, and `final-android-discover-dark.png`.
- Published OTA artifact: `design-qa/tveaker-build10.apk` — version code 10, version name 1.6.0, 19,353,338 bytes, SHA-256 `C9A6D8D95E2796C3A60C3859BF055682B27426D85D8A424C9F9E07E98B3AC1E4`.
- Verification: backend `85 passed`; Ruff passed; Android unit tests and debug assembly passed. Live OTA metadata reports v1.6.0 build 10.

final result: passed
