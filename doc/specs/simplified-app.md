# Spec: Simplified app (phone = settings, Android Auto = Classic dashboard only)

2026-10-21 · Status: implemented on branch `simplify-app`, verified on the emulator and with unit tests; not yet
driven on the car.

## Summary

**Problem.** The first drive with the real car showed two things: the app connects and projects to Android Auto,
but the phone UI (upstream ObdGraphs' seven live-data screens, nav drawer, bottom bar, speed-dial FAB, wizard, DTC,
trips, readiness, Google Drive) looked broken, and the adapter connection lagged. None of that UI is needed for a
single-purpose app whose dashboard lives on the car screen. Every extra feature also kept its own query, broadcast
receivers and background work (trip recording, DTC scan on connect, GPS emitter) running next to the Classic screen.

**Scope.** Cut the app down to what the G-Series Indicators use case needs:

- **Phone:** a settings screen only. Adapter connection (type, device, timeouts, auto-connect), adapter init
  (protocol, delays, CAN header table with ECU discovery), advanced adapter settings (batching, STN, retries),
  PID registry (browse / edit PIDs, module list, filters) and the vehicle profile. A floating button connects to or
  disconnects from the adapter, and the toolbar subtitle shows the connection type, profile and state.
- **Android Auto:** the Classic dashboard and nothing else. One screen, an action strip with connect / disconnect
  and exit. No screen list, no routines, no DTC screen, no virtual screens, no settings shortcut.

User's choices (2026-10-21): make it "much more simple", keep only connection settings and ID management on the phone,
only the Classic layout on the car, and do it on a branch.

## Behaviour changes

| Area | Before | After |
|---|---|---|
| Phone start | Splash → language dialog → permission onboarding → setup wizard → Gauge screen with drawer, bottom bar, FAB speed dial | Splash → permission onboarding (if anything is missing) → settings list |
| Phone screens | Gauge, Giulia, Dashboard, Graph, Trip Info, Performance, Drag Racing, GPS status, DTC, readiness, trips, metadata, alerts, backup, Google Drive | Settings only: Connection, Adapter init, Adapter advanced, PIDs, Profile, Debug (debug builds) |
| Phone connect | FAB on each screen, query of that screen | FAB on the settings screen, Classic query |
| Android Auto | Giulia screen by default, screen list (Drag, Gauge, Giulia, Classic, Trip Info, Performance, Routines, DTC), virtual-screen strip, settings and exit actions | Classic screen only; connect / disconnect and exit actions |
| On connect | DTC module scan when modules are configured (gauges froze for its length), trip recording, GPS emitter | Nothing but the live query |
| Profiles | Alfa / Giulia profiles plus BMW; the BMW profile (`profile_21`) was outside the picker's 1..20 range | Only `default` and BMW; the picker lists ids up to 25 and BMW is the default profile of the flavor |
| Build flavors | `giulia`, `giuliaAA`, `giuliaPerformanceMonitor` | `giuliaAA` only (same applicationId) |
| Firebase | Crashlytics / Analytics plugins, needed a placeholder `google-services.json` (crashed on a malformed key) | Removed; no placeholder file needed |

## Settings / keys touched

- Removed from the UI, still read with their code defaults by `:datalogger`: `pref.adapter.query.individual.enabled`,
  `pref.pids.generic.high/low`, `pref.vehicle_settings.vehicle_status_panel_enabled` (field removed), `pref.alerting.*`,
  every `pref.aa.*` key except `pref.aa.surface.fps`, every screen's `pref.<screen>.*` key.
- `pref.aa.surface.fps` stays (set by the BMW profile to 15); `CarSettings` reads only this key.
- `pref.adapter.init.fetchDTC`, `pref.adapter.init.cleanDTC`, `pref.dtc.*`, `pref.adapter.gps.collect.enabled`,
  `pref.drag_race.*`: removed with their code.
- Profile ids: `DEFAULT_MAX_PROFILES` 20 → 25 so `profile_21` is listed. `DEFAULT_PROFILE` = `profile_21`.
- Removed `localeConfig` / in-app language and theme pickers: the app follows the system locale (EN, PL) and theme.

## Implementation

- `:automotive`: `CarSession` → `ClassicCarScreen` (`screen/ClassicCarScreen.kt`), which owns `SurfaceRendererController`
  (moved to `screen/`), the rendering thread, the `DATA_LOGGER_*` / surface / profile-reload receivers and the two-action
  strip. `CarSettings` is a plain class with `getSurfaceFrameRate()`. Deleted: `CarScreen`, `CarScreenFactory`,
  `nav/*`, `iot/*`, `DynamicSelectorModeEventBroadcaster`, `Colors`, the Alfa AA profiles and screen drawables.
- `:screen_renderer`: only `renderer/classic/*` and `api/SurfaceRenderer` (`SurfaceRenderer.classic(context, collector)`).
  `ClassicFrameStates` (ex `gauge/GaugeFrameStates`, with `easeNeedle` folded in) replaces the gauge package.
  `ScreenSettings`, `SurfaceRendererType`, `Identity`, `Fps`, `AbstractDrawer`, `AbstractSurfaceRenderer`, `Units` are gone.
- `:datalogger`: `QueryStrategyType` has `CLASSIC_QUERY` only; `QueryStrategyOrchestrator` keeps the vehicle-status
  injection (for disconnect-when-off). `WorkflowOrchestrator` no longer schedules DTC scans, routines, trips or GPS;
  `AdjustmentsStrategy` disables DTC / VIN reading outright. `VehicleCapabilitiesManager` keeps supported PIDs and
  metadata. Packages `dtc`, `trip`, `gps`, `readiness` and the other query strategies are deleted with their tests.
  The GiuliaQV codec dependency is gone.
- `:app`: `MainActivity` hosts `PreferencesFragment` in a `FragmentContainerView`, implements
  `OnPreferenceStartFragmentCallback` for the CAN header table fragment and `showPreferences(rootKey)` for nested
  screens (`navigateToPreferencesScreen(key)` keeps its old name for the preferences that call it after a change).
  `preferences.xml` is rewritten (5 categories). The PID dialog keeps only the registry mode (`source="edit"`).
  Deleted: `ui/*` screens, `wizard`, nav/menu/navigation resources, DTC / readiness / trips / metadata / profile
  backup preferences, power receiver, device admin, GPS activity, shortcuts, the other flavors, Google Drive and
  Firebase dependencies, ~550 unreferenced strings.
- Modules `:dragracing`, `:integrations`, `:screen_behavior`, `:ext` are removed from the build.
- `:common`: language / theme managers, `LocalizedStringProvider`, `Cache` and the drag-race drawable are gone.

## Backward compatibility

- applicationId, profile ids and every surviving preference key are unchanged, so an installed app keeps its adapter,
  profile and PID settings. Preferences of removed features stay in the file unread.
- `SurfaceRendererType` ids are no longer persisted anywhere (`pref.aa.screen.last_used` is unread).
- The BMW PID file and profile are unchanged; the car spike (confirming DIDs) is still open.

## Tests

- `:datalogger`: `QueryStrategyOrchestratorTest` (Classic only, vehicle-status injection), `AdjustmentsStrategyTest`
  (DTC off, metadata off), `VehicleCapabilitiesManagerTest` (supported PIDs / metadata), `VehicleStatusMetricsProcessorTest`
  (disconnect-when-off flag).
- `:screen_renderer`: `ClassicFrameStatesTest` (easing, staleness; the eviction tests went with the gauge drawer),
  `ClassicRenderTest` with the new constructor.
- `:profile`: `ProfileServiceTest` lists 25 profiles.
- `:app`: `PidDefinitionDialogModeTest`.
- `:automotive` (new test source set, Car App testing library + Robolectric): `ClassicCarScreenTest` builds the
  template: a `NavigationTemplate` with a two-action strip and no map strip; a loading indicator while connecting.
  `ClassicCarScreen` takes the renderer as a constructor parameter so the test passes a no-op one.

## Risks and verification checklist

- [x] `./gradlew spotlessCheck`
- [x] Unit tests of `:datalogger`, `:screen_renderer`, `:profile`, `:app`
- [x] `:app:lintGiuliaAADebug`, `:app:assembleGiuliaAADebug`
- [x] Emulator (`oldschool_api35`, fresh install): BMW profile is the default, settings open (connection type → mock,
      PIDs dialog, CAN header table with the four BMW entries), the button connects (`CLASSIC_QUERY` with 27 PIDs,
      status "connected"), no crash in logcat. Found and fixed on the way: with targetSdk 35 the preference list drew
      under the action bar (edge-to-edge), so the toolbar is now part of the layout (`NoActionBar` theme).
- [ ] DHU: not checked. The AVD only has Google's Android Auto *stub* (`AndroidAutoStubPrebuilt`), nothing listens on
      port 5277; install Android Auto from Play in the emulator and sign in first (CLAUDE.md, Build Environment).
- [ ] Car: connection lag. Out of scope here; the levers are in `classic-dashboard-tech.md` §6 (batching, header
      switches cost 6 AT commands per extended entry, the 13 probe PIDs in `ClassicPids.PROBES`).

## Out of scope

- Fixing the adapter lag itself (separate investigation on the car with a logcat).
- Confirming the BMW DIDs.
- Any new phone-side display of live values.
