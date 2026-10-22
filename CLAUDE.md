# CLAUDE.md (org.obd.graphs)

Project-specific knowledge for agents. **Maintained incrementally — when you
learn something non-obvious (a finding, a pitfall, an architecture decision
you made), add it here as part of the same change/PR.** Keep entries terse:
a rule, its reason, and the file it lives in. Cut narrative history; keep the
lesson. Update or delete entries that turn out to be wrong.

## 🤖 AI Assistant Directives (Token & Context Management)
* **Aggressive Context Management:**
    * You MUST monitor context size. Prompt the user to use `/compact` mid-task if the conversation history grows too long (to prevent >150k token context bloat and expensive cache reads).
    * Remind the user to use `/clear` when switching to a completely new task or a different module. Do not carry stale context.
* **Subagent & Fork Efficiency:** When spawning subagents or using "forks", keep instructions strictly scoped to prevent runaway loops. If performing simple file-system reads, prefer cheaper models (like Haiku) if the environment allows it.
* **Update this file after every feature or fix — it is part of "done".** Before handing the
  change over, add what the next session would otherwise have to rediscover: the architecture
  decision and why, pitfalls hit, wrong turns and what proved them wrong (lessons learned).
  Correct or delete entries the work disproved. Stage the `CLAUDE.md` edit with the change itself.
* **Brevity is required:** Provide code solutions directly. Omit preamble, conversational filler, and lengthy explanations unless explicitly requested.
* **Progressive Disclosure:** Do not assume the entire architecture up front. If deep context is needed for a specific module (e.g., `:datalogger`), read its local `README.md` before writing code.
* **Targeted Fixes:** When fixing existing files, output only the modified blocks or reference specific line numbers rather than re-writing the entire file.
* **Ignored Paths:** Do not ingest, search, or read files in any `build/`, `.gradle/`, or generated code directories (e.g., KAPT/KSP outputs). Avoid reading large binary assets in `res/drawable-*` or `.apk`/`.aab` files.
* **Tooling Reliance:** Do not act as a syntax linter or formatter. Rely on the user running Spotless and Android Studio's native linting.

---

## 🛠 Build & Command Line Cheat Sheet

### Key Gradle Commands
* **Clean Project:** `./gradlew clean`
* **Assemble Debug APK:** `./gradlew assembleDebug`
* **Run Lint Check:** `./gradlew lint`
* **Code Formatting (Spotless):** `./gradlew spotlessApply`

### Testing Commands
* **Run All Instrumented Tests:** `./gradlew connectedAndroidTest`
* **Run Unit Tests (if added):** `./gradlew test`
* **Run Tests for a Specific Flavor:** e.g., `./gradlew connectedGiuliaAADebugAndroidTest`

### Build Variants & Target Flavors
One flavor is left, `giuliaAA` (dimension `version`); combine it with the build type.
* **Debug APK:** `./gradlew :app:assembleGiuliaAADebug`
* **Unit tests of the app module:** `./gradlew :app:testGiuliaAADebugUnitTest`
* **Lint (errors fail CI):** `./gradlew :app:lintGiuliaAADebug`

---

## 🧪 Testing & Quality Assurance

### Test Configuration
The project is set up to use the standard AndroidX test runner, with some specific configuration flags disabled:
* **Test Runner:** `androidx.test.runner.AndroidJUnitRunner`
* **Functional Testing Flag:** Disabled (`testFunctionalTest false`)
* **Profiling Flag:** Disabled (`testHandleProfiling false`)

### Included Testing Frameworks
When writing new tests, use the natively provided libraries within the `androidTest` source set:
* **UI Testing:** Espresso Core (v3.5.1)
* **Test Execution & Rules:** AndroidX Test Runner (v1.5.2) and Rules (v1.5.0)
* **Kotlin Extensions:** AndroidX Core KTX (v1.5.0) and JUnit KTX (v1.1.5)

> **Note on Unit Tests:** `:app` has a plain JUnit suite for Android-free logic (e.g.
> `PidDefinitionDialogModeTest`, `./gradlew :app:testGiuliaAADebugUnitTest`). `:datalogger` has a local JVM suite under `src/test` running on
> Robolectric (`./gradlew :datalogger:testDebugUnitTest`, suite `DataLoggerTestSuite` lists the classes). `:screen_renderer` has plain JUnit
> tests for pure layout logic (`./gradlew :screen_renderer:testDebugUnitTest`) — keep Canvas-free
> logic in testable functions such as `ClassicLayout` / `ClassicReadings` — plus `ClassicRenderTest`, which draws a
> whole screen on a real canvas (Robolectric `@GraphicsMode(NATIVE)`, `@Config(sdk = [34])`) into
> PNGs under `screen_renderer/build/classic-render/`: the way to look at a screen without a DHU. `:profile` has its own
> Robolectric suite. `:automotive` has `ClassicCarScreenTest` (`androidx.car.app:app-testing` `TestCarContext` +
> Robolectric; `DataLoggerRepository` / `AutoConnect` mocked with mockk, which needs the module on JVM target 11).
> `:app` has no instrumented tests any more (the old ones drove the removed screens).

**Every new feature ships with a spec** in `doc/specs/<feature>.md`, staged with the change — not
done until it exists. Follow the existing specs' structure: Summary (problem, scope), Behaviour
changes (before/after table), Settings/keys touched, Implementation, Backward compatibility, Tests,
Risks and verification checklist, Out of scope. Update the spec when the feature changes.
A spec may be written before the code (`Status: proposed, not implemented` in its header); update
the status and the sections when it is implemented. Screenshots go in `doc/specs/img/`.

**Every new feature and bug fix ships with test coverage** — not done until it does. Extend the
existing test class for the code under change before creating a new one. For a bug fix, the test
must reproduce the bug: check that it fails against the unfixed code, otherwise it pins nothing.

---

## 🏗 Codebase Architecture

### Internal Submodules / Projects
Four library modules plus the app (spec `doc/specs/simplified-app.md`, 2026-10-21):
* `:common` - Prefs, broadcasts, permissions, `Network`, `DiagnosticRequestIDManager`, `Modules`, notifications.
* `:profile` - vehicle profile persistence (`assets/*.properties` → Prefs).
* `:datalogger` - ObdMetrics workflow, adapter connections, the Classic query, the metrics collector; BMW PIDs in
  `src/main/resources/bmw_b47_mhev.json`.
* `:screen_renderer` - the Classic dashboard drawer (`renderer/classic/`) and the `SurfaceRenderer` interface.
* `:automotive` - the Android Auto session and its single screen (`ClassicCarScreen`); the BMW profile asset.
* `:app` - the phone settings activity, connection / PID / CAN-header preferences.
`:dragracing`, `:integrations`, `:screen_behavior` and `:ext` were deleted with the upstream screens; do not resurrect
them for a new feature, add to the modules above.

### Static Code Quality
This project uses Spotless for automatic code style enforcement. Ensure you format before submitting PRs:
```bash
./gradlew spotlessApply
```
The build runs `spotlessCheck` and fails on any violation, so run `spotlessCheck` before handing a
change over. Its Kotlin indentation is not the IDE's: a multi-line value after `name =` stays at
the argument's own indent level instead of being indented one step further.

---

## 🔀 Git Workflow

This checkout is the user's own fork, not upstream ObdGraphs. The rules below replace upstream's.

**Repos and remotes.** App: `origin` = `p0nyque/g-series-indicators` (public; was `oldschool-indicators`), `upstream` =
`tzebrowski/ObdGraphs`. Library: `../ObdMetrics`, `origin` = `p0nyque/ObdMetrics` (public, default branch
`feat/bmw-extended-addressing`), `upstream` = `tzebrowski/ObdMetrics`. The APK (`archivesBaseName` `GSeriesIndicators`)
and launcher label (`app_name`, "G-Series Indicators") carry the new name. The applicationIds stay `org.obd.graphs.my.giulia*`
by the user's choice: a new id installs as a separate app, without the old one's settings, profiles or DTC history. The app builds against that branch
installed into `~/.m2`. GitHub Actions are off on both repos: upstream's workflows need secrets and the
published ObdMetrics snapshot, so they would only fail.

**READMEs describe the fork, not upstream.** The app's `README.md` has a Status table (what runs on mock data vs. on
the car): update it when a phase lands or a DID is confirmed. ObdMetrics' `README.md` is a fork section ("What this
fork adds") above upstream's untouched library docs; add each new fork change to its table, and expect README
conflicts when merging upstream.

**`main` is the working branch.** Commit to `main` unless the user asks for a branch. Run
`git branch --show-current` before staging, since the user may switch branches outside your view.

**Commit and push only when the user asks.** Stage files explicitly and leave out unrelated local edits.
`local.properties` is `skip-worktree` (`scripts/setup-local.sh` writes it); never commit it. Push over SSH (`git@github.com:...`): the gh OAuth token
has no `workflow` scope, and HTTPS pushes of history containing `.github/workflows` are rejected.

**No force-push, `commit --amend`, `rebase` or `reset --hard` without asking.** A commit may already be
on the remote. Check `git status` and stash before any resetting command, or use `git reset --keep`,
which refuses rather than discards.

**Pulling from upstream:** `git fetch upstream`, then merge `upstream/master` into the app's `main`, and
`upstream/v11.x` (ObdMetrics' default branch) into `feat/bmw-extended-addressing`. Resolve conflicts here;
upstream does not know about the BMW changes.

## ☕ Build Environment

Gradle needs JDK 17+ (Crashlytics plugin); the shell default may be JDK 11 and fails at configuration. Use e.g. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew ...`. Quick compile check for a module: `./gradlew :screen_renderer:compileDebugKotlin`.

Emulator on the dev Mac (arm64): AVD `oldschool_api35` (Pixel 7, `android-35;google_apis_playstore`) plus the
DHU in `extras/google/auto`. `avdmanager` needs `JAVA_HOME` set (JDK 17 in `~/Library/Android/jdk`). Launch with
`adb shell am start -n org.obd.graphs.my.giulia.aa/org.obd.graphs.activity.MainActivity`; `monkey` silently
did nothing. The AA screens need Android Auto from Play inside the emulator (the user signs in), then
Developer settings → Start head unit server, `adb forward tcp:5277 tcp:5277`, `desktop-head-unit`.
**"connected" then "Failed to read from transport" from the DHU = nothing listens on 5277**: the AVD ships only Google's
Android Auto *stub* (`pm path com.google.android.projection.gearhead` → `AndroidAutoStubPrebuilt`); the real app has to be
installed from Play and signed in before the head unit server exists. `adb logcat` shows `adbd: failed to connect to
socket 'tcp:5277'`.
* Firebase / Crashlytics are gone from the build (2026-10-21): no `google-services.json` is needed any more. An old
  checkout's placeholder file is harmless and gitignored.

CI "Unresolved reference" to a new ObdMetrics class while it builds locally = stale snapshot, not code.
Locally `mavenLocal()` serves your own ObdMetrics install; CI restores a Gradle cache (`setup-java`
`cache: gradle`) that trusts a cached `-SNAPSHOT` for 24 h. Hence `--refresh-dependencies` in
`.github/workflows/build.yml`. Check the published jar first: the Sonatype `maven-metadata.xml`
gives the timestamped jar name; a CI run started before that timestamp cannot see it.

---

## 📚 Findings & Architecture Decisions






### App shape after the simplification (2026-10-21)
* Spec `doc/specs/simplified-app.md`. **Phone = settings, car = the Classic dashboard, nothing else** (user's choice after
  the first drive: the upstream phone UI looked broken and the connection lagged). Do not add live-data screens to the phone
  or a second AA screen without asking.
* AA: `CarSession` → `ClassicCarScreen` (`automotive/.../screen/ClassicCarScreen.kt`): NavigationTemplate, action strip
  with connect / disconnect + exit, `SurfaceRendererController` (same file's package) draws `SurfaceRenderer.classic(...)`.
  The query is `Query.instance(CLASSIC_QUERY)`; `classicQuery()` applies the collector filter before every start / update.
  `CarSettings` reads one key, `pref.aa.surface.fps`. Registry reloads (`PROFILE_CHANGED_EVENT`, `WORKFLOW_RELOAD_EVENT`)
  invalidate the collector and update the query.
* Phone: targetSdk 35 is edge-to-edge, so the theme is `NoActionBar` and the toolbar lives in `activity_main.xml`
  (`AppBarLayout` with `fitsSystemWindows`); with the decor action bar the preference list drew under it.
  `MainActivity` hosts `PreferencesFragment` in `settings_container`; nested `PreferenceScreen`s open through
  `showPreferences(rootKey)` (back stack per key), `app:fragment` preferences (the CAN header table) through
  `OnPreferenceStartFragmentCallback`. Preferences that call `navigateToPreferencesScreen(key)` after a change get their
  screen reloaded in place (same key = no new back-stack entry). The FAB starts / stops the Classic query; the toolbar
  subtitle shows connection type · profile · state.
* `QueryStrategyType` has `CLASSIC_QUERY` only. `QueryStrategyOrchestrator` still injects `VEHICLE_STATUS` when
  `pref.vehicle_settings.disconnect_when_off` is on (the only consumer left, `VehicleStatusMetricsProcessor`).
* `AdjustmentsStrategy` turns DTC / VIN reading off unconditionally: there is no DTC code left in the app. If DTCs come
  back, start from upstream's `datalogger/.../dtc` on `main` before this branch, not from scratch.
* `DEFAULT_MAX_PROFILES` is 25: the BMW profile is `profile_21` and the picker enumerates ids, so 20 hid it (it was still
  loaded and applied; only the list was wrong). Only `default*.properties` (app) and the BMW profile (automotive) ship.
* The PID dialog has one mode, `edit` (`PidDefinitionDialogMode.Edit`); `PidDefinitionListPreferences` with another
  `source` gets a plain selection dialog persisting to its key, nothing reads those keys today.
* Removed ~550 unreferenced strings with a script (`@string/`, `R.string.` and `string.` lookups over all sources). When
  deleting a feature, rerun that kind of sweep: lint does not fail on unused strings, they just rot.
* Wrong turn avoided: keeping the other renderers "just in case" would have kept `ScreenSettings`, `screen_behavior`
  and the virtual-screen prefs alive for nothing; `ClassicDrawer` never used them (tech design D2 / D8).

### ObdMetrics lessons kept from the removed features
These came out of DTC / readiness work that is gone from the app, but they describe ObdMetrics (the fork on
`feat/bmw-extended-addressing`) and still bite any query switch or header change:
* **"Nothing decoded any more" = check logcat for `ConnectorResponseDecoder ... Unexpected error` / `Completed decoder
  thread`.** Every `updateQuery` queues UDS `10 01` (no PID); a reply that throws used to end the decoder thread, so after
  a query switch nothing was decoded until the next connect while TX/RX looked healthy. Fixed in ObdMetrics
  (`ConnectorResponseDecoder` skips PID-less replies, one failing reply no longer ends the thread).
* **Commands queued straight into the buffer must reset the header multiplexer** (`CommandProducer.resetHeader()`), or
  with a single-mode query live data stays on the last module an `ATSH` was sent to. A test must wait for live data
  *before* such a command, or the one-time injection happens after it and hides the bug.
* ObdMetrics drops query ids missing from the registry without an error: a screen with a fixed query must check its PIDs'
  resource module is selected, or it waits forever (Classic: `ClassicQueryStrategy` filters by registry presence).
* `DataLoggerRepository.observe()` conflates (LiveData `postValue`): of several metrics decoded from one batch reply only
  the last reaches the observer. `observeEach()` delivers every one.
* A context reply that decodes to `null` is never published; codecs return an error response instead.
* Formula letters stop at `Z`: ObdMetrics threw on replies longer than 26 data bytes; fixed in
  `ScriptEngineParameterBinder`, bytes past `Z` stay unreachable to a formula.
* One PID per request id per query: the registry keeps every PID registered for a query and runs each codec, so two
  files defining the same request double the traffic (count repeated requests in a logcat to spot it).

### Classic dashboard (AA; phase 1 on mock data)
* Spec `doc/specs/classic-dashboard.md`. 11 fixed indicators on one AA page: 4 main round dials, 6 sub-dials and a
  status strip. They are styled in the G31 LCI's Live Cockpit colours: graphite faces, orange glow and progress line
  up to the value, an orange pointer bar with no pivot. User's choices: one page, no alerts of any kind for now, fixed
  slots (no PID dialogs).
* Wrong turns, both rejected by the user. First, E30/E39-style dials (black face, needle on a pivot). Second, copying
  the cluster's layout too (open polygon scales, corner gauges, centre tiles). The keeper is the first layout with
  the cluster's styling.
* Mockups `doc/specs/img/classic-dashboard*.svg` are generated by `scripts/mockups/classic_dashboard.py`. Edit the
  spec first, then the script, and regenerate. Preview with headless Chrome (the command is in the script).
  `qlmanage` crops SVGs to a square, so its thumbnails mislead.
* Own `ClassicDrawer`, standalone: it never used upstream's `AbstractDrawer` / `ScreenSettings`, which is why the other
  renderers could be deleted without touching it.
* Printed red / blue zones (oil, coolant, gearbox, EGT) are static styling, never alerts (user's choice). They must
  not change with the value.
* Red / blue are the BMW M stripe colours (`#F11A22` / `#008AC9`, BMW Blog's RGB; BMW publishes no hex), user's
  choice; recuperation uses the same blue. `ClassicRenderTest` pins them by exact pixel colour. Copy render PNGs into
  `doc/specs/img/` only from a run on the final code: a run with stashed code leaves old colours in `build/`.
* Tech design (files, phases, open questions Q1–Q10): `doc/specs/classic-dashboard-tech.md`. Facts it rests on:
  AA frames default to **5 fps** (`pref.aa.surface.fps`), too slow for easing, so the BMW profile sets 15. The canvas is
  hardware (`lockHardwareCanvas`) and `BlurMaskFilter` is accelerated only from API 28 (minSdk 26), so the glow must
  look acceptable without blur. Fonts are bundled Roboto v2.137 (`assets/fonts/`); never use system `sans-serif-light`,
  which Samsung's font setting replaces. Another weight comes from `google/fonts` commit `724bf98e` (`apache/roboto/`),
  the release of the bundled ones (same glyphs, only `cmap` / `name` differ).
* Draw Classic by slot id (`getMetric(id)`), never by iterating `getMetrics()`: injected `VEHICLE_STATUS` then cannot
  show, and a PID missing from the registry leaves its slot at rest instead of shifting the others.
* ObdMetrics priorities apply only with `pref.adapter.batch.enabled`; header switches are not grouped per ECU, and each
  entry into an extended entry costs 6 AT commands. Refresh-rate levers are listed in the tech design §6.
* The debug `mock` connection answers every PID in the registry: a PID file with placeholder DIDs lets the whole screen
  run on the DHU before any real DID is known. `01 78` (EGT) is not defined in ObdMetrics' `mode01.json`.
* Code: `renderer/classic/` (pure `ClassicDials` / `ClassicReadings` / `ClassicLayout`, `ClassicDrawer`,
  `ClassicStaticLayer`, `ClassicSurfaceRenderer`), ids in `:datalogger` `ClassicPids` (one copy; `ClassicDialsTest`
  fails if a drawn id is not queried), `CLASSIC_QUERY`, `SurfaceRenderer.classic()` (the only renderer; `SurfaceRendererType`
  is gone), profile `profile_21` (`automotive/src/main/assets/bmw_g31_520d_mhev.properties`). Pointer easing / staleness:
  `ClassicFrameStates` (ex `GaugeFrameStates`).
* **`bmw_b47_mhev.json` lives in `datalogger/src/main/resources/`, not ObdMetrics.** ObdMetrics finds PID files with the
  context class loader, and any module's Java resources land at the APK root next to `mode01.json`; no jar rebuild per
  DID. Its DIDs are SGBD candidates (tech design §4.3), not yet seen on the car; ids `40101`–`40113` are probe PIDs
  (`ClassicPids.PROBES`: queried, never drawn) for the alternates. Generated by `scripts/gen_bmw_pids.py`; edit the script.
* Formula letters stop at `Z`: ObdMetrics threw on a reply longer than 26 data bytes (`D08C`, 28) and the PID decoded
  `null`. Fixed in ObdMetrics `ScriptEngineParameterBinder`; bytes past `Z` stay unreachable to a formula.
* `ClassicPidsTest` decodes every BMW formula with the app's engine (`rhino`). rhino-android needs
  `--add-exports java.base/sun.security.action=ALL-UNNAMED` on JDK 17+, set in `datalogger/build.gradle` `testOptions`.
* **DID source = BMW's SGBDs, published as Markdown in `github.com/emdzej/ediabasx-docs-sgbd`** (`docs/sgbd/*.md`).
  Each ECU's `SG_FUNKTIONEN` table maps a value to its DID, type and scaling (`22`). Composite DIDs have their byte
  layout in `RES_0x<DID>_D`. Used: `D83BX7C0` (DDE 8.32, B47/B57, mirrors the 48 V starter-generator), `BATT48`,
  `PCU48`, `GSZFB1` / `GSZFBCE` (8HP). Addresses are in `ZGW_SP18` `TABDIAGADRESSEN`: Batt48 `02`, PCU48 `54`.
  Candidates and formulas: tech design §4.3. Search GitHub code for a value's BMW name (e.g. `STAT_..._WERT`) to find
  more such tables. The 520d MHEV may run DDE 8.02, which is not in the repo, so verify on the car.
  Wrong turns: forum threads (none for B47; VerticalScope forums refuse WebFetch and the browser pane); OBDb's DDE-side
  DIDs (`4205`, `4402` …) are from US *petrol* DMEs and are absent from the DDE table. The N57 DDE7 table
  (`kmalinich/dieslg8`) uses `2C10 <id>` and other ids.
* The glow must sit *between* two static bitmaps (faces below, ticks / numerals above): over one flat bitmap it tinted
  the ticks and numerals orange. Draw order is the mockup script's.
* Staleness: only boost and the starter-generator are fast (10 s). `mode01.json` reads coolant and IAT at priority 2
  (every 16th cycle), which a 10 s limit greys out, so every other slot uses 60 s.
* Profile loader: only `-?\d+` values become `Int`, so headers like `7E0` / `6F1` stay strings unquoted; ECU addresses
  such as `12` / `612` still need quotes.

### CAN extended addressing (BMW F/G)
* Spec `doc/specs/bmw-extended-addressing.md`. A header table entry may carry `extended_address_value` /
  `receive_address_value` (profile keys only, no UI); ObdMetrics `Init.Header.commands()` turns it into
  `ATSH`/`ATCEA`/`ATCRA`/`ATFCSH`/`ATFCSD..300000`/`ATFCSM1`. Any new code path that sets a header must use
  `commands()`, never `new ATCommand("SH" + ..)`, or the adapter stays on the last module's extended address.
* BMW modules share header `6F1`: anything keyed by header alone (e.g. `dtcScanTargets`) must add the extended address.
* The `F1` reply byte is stripped in ObdMetrics' `StreamingConnector` while `ATCEA<hh>` is on, not in the codecs.
* Profile loader stores an unquoted numeric value as `Int`; `Prefs.getString` on it throws. Quote `"12"` in profiles,
  read such keys via `Prefs.all[key]?.toString()`.
* ObdMetrics on this Mac: `mvn` with JDK 11 (`~/Library/Android/jdk/jdk-11*`), `-Dlombok.delombok.skip=true`. On JDK 17
  delombok fails and ~800 tests fail (no Nashorn JS engine for formulas) — not a code problem.


### Connectors (`:datalogger/.../connectors`)
* One `AdapterConnection` per transport, chosen by `ConnectionManager.obtain()` on
  `pref.adapter.connection.type`. Two Bluetooth transports, deliberately separate:
  `BluetoothClassicConnection` (RFCOMM/SPP, bonded devices only) and `BleConnection`
  (GATT). A BLE-only dongle has no SPP record, so Classic can never reach it.
* **The persisted value `"bluetooth"` means Classic and must never be repurposed** — it is on
  users' devices. BLE is the separate value `"ble"` and keeps its MAC/UUIDs under new
  `pref.adapter.connection.ble.*` keys, so switching type never disturbs `pref.adapter.id`.
* **Stream contract:** ObdMetrics' `StreamingConnector.receive()` reads **byte by byte** via
  `in.read()` and stops on `'>'` or `-1`. A transport's `InputStream` must therefore return `-1`
  (on a read timeout or close) rather than block forever; framing on `'>'` is the connector's job,
  not the stream's. `BleInputStream`/`UsbInputStream` both work this way.
* BLE GATT profiles (`BleProfiles.kt`) are **probed after connecting**, never used to filter the
  device scan: OBD adapters advertise a local name and expose their services only once connected,
  so scanning with a service filter matches nothing. Same reason `Network.scanBleDevices()` scans
  unfiltered.
* BLE writes are capped at the negotiated MTU and GATT allows one outstanding operation, so
  `BleOutputStream` chunks (20-byte floor) and waits for each `onCharacteristicWrite`.
* `Network.startBondedDeviceMonitor()` does **no** scanning despite what its old name said — it
  checks bonded devices and registers a broadcast receiver. The real scan is `scanBleDevices()`.

### BLE / GATT (`:datalogger/.../connectors/BleConnection.kt`)
* **The GATT callback is the only authority on whether *this* client connected.**
  `BluetoothManager.getConnectionState(device, GATT)` is device-global and updated
  asynchronously: it reports CONNECTED whenever anything on the phone holds a link to that MAC,
  so for a dual-mode adapter connected from system settings it made every *failed* attempt look
  successful. Success is `newState == STATE_CONNECTED && status == GATT_SUCCESS`, nothing else —
  the stack also reports CONNECTED with a failure status.
* `onConnectionStateChange` fires with `STATE_DISCONNECTED` on a failed connect, so a latch that
  is counted down by both states cannot on its own distinguish success from failure.
* **Everything the callback touches must be `@Volatile`.** Callbacks arrive on a binder thread,
  `connect()` runs on the caller's, and there is no happens-before edge otherwise: a stale latch
  looks like a connect timeout, a stale `input` silently drops notifications.
* One `BluetoothGattCallback` **per attempt**. A shared instance let a late event from a client
  already given up on count down the current attempt's latch.
* `close()` must not follow `disconnect()` immediately — wait for the DISCONNECTED callback (or
  ~600 ms). Closing early is the usual reason the *next* `connectGatt` returns status 133.
* Create the streams and assign `gatt` **before** enabling notifications: adapters push their
  banner the moment the CCCD is written.
* `connectGatt` does **not** need the main thread. Posting to the main looper and blocking on the
  result deadlocks whenever `connect()` is itself called from the main thread —
  `DataLoggerService.onStartCommand` dispatches on the main thread, so anything it calls that
  opens a transport (`executeRoutine`, `start`) must go through `runAsync` first.
* **"Connected, zero services" is a cache problem, not a link problem.** The platform keeps a
  per-device GATT service database, and for an adapter bonded over Classic it can be empty or from
  the wrong transport — discovery then completes with `GATT_SUCCESS` and no services. `awaitServices`
  retries and calls the hidden `BluetoothGatt.refresh()` between attempts to drop that cache.
* **`BluetoothAdapter.getRemoteDevice(mac)` always labels the address PUBLIC.** An adapter using a
  random address is then connected as the wrong address type — the link comes up and exposes
  nothing. `Network.bluetoothDeviceByAddress()` therefore prefers a `BluetoothDevice` from the
  bonded/connected lists, which carries its real address type, and only synthesises one as a
  fallback.
* **The adapter's banner answers no command.** Many adapters push their version string the moment
  the CCCD is written, i.e. before anything was transmitted. Left in the queue it is read as the
  reply to the *first* command and every response after that is matched against the wrong request —
  an adapter that connects and never initialises. `connect()` calls `BleInputStream.discardPending()`
  after enabling notifications for exactly this reason.
* **The read timeout has to cover the slowest ELM327 command, not a typical PID reply.** `ATZ` and
  `AT SP 0` routinely take many seconds; a read that gives up first truncates the reply and shifts
  every later one. Nothing pays for a long timeout in the steady state, because replies end in `'>'`
  and the read returns on it — the timeout only fires on genuine silence.
* **A UUID match is not a profile match.** Clones reuse well-known UUIDs on characteristics that
  cannot notify or cannot be written. `resolveProfile` checks `properties` as well, and falls back
  to `discoverSerialProfile` — any non-generic service exposing a notifiable characteristic plus a
  writable one — so an unlisted module connects instead of being refused. Its UUIDs are logged so
  the pair can be promoted into `BLE_PROFILES`.
* **A characteristic that only INDICATES needs `ENABLE_INDICATION_VALUE`.** Writing the notify
  value subscribes to nothing: the CCCD write reports success and not one byte is ever delivered.
* **Resolve the device by scanning for its MAC when it is neither bonded nor connected.**
  `Network.findAdvertisingBleDevice()` returns the scanner's own `BluetoothDevice`, which carries
  the real address type, and proves the adapter is in range before ~100s are spent connecting
  blind. A scan finding nothing is *not* a verdict — an adapter the phone is already connected to
  does not advertise — so a synthesised device stays the fallback.
* **`BleGatt.refresh()` only on a discovery RETRY.** On a healthy first attempt it throws away a
  valid service database for nothing, and discovery started in the same breath as `refresh()` fails.
* `requestConnectionPriority(CONNECTION_PRIORITY_HIGH)` right after connecting: an ELM327 is
  strictly request/response, so the default interval costs a full round trip per command.
* **Override `OutputStream.write(b, off, len)`.** The inherited one calls `write(int)` per byte, and
  each of those is a GATT write awaiting its own completion callback.
* **Stream contract, BLE specifics:** a quiet line returns `-1` (framing is the connector's job),
  but a *dropped link* must throw `IOException` — `StreamingConnector.receive()` reads `-1` as an
  ordinary end-of-message and would never reconnect. `BleOutputStream` throws on a failed chunk
  for the same reason: a silent return leaves a partial command in the adapter and mis-frames
  every later response.
* A write that times out must be reported as failed; `writeStatus` is pre-set to `GATT_SUCCESS`,
  so `latch.await()`'s result has to be checked too.
* **A dual-mode adapter has TWO addresses.** CCY STN-2120: bonded Classic record `…:34:38:35`
  (type=1) and an unbonded advertising LE device `…:35:38:35` ("CCY STN-2120 4.0"). Connecting the
  Classic record over TRANSPORT_AUTO lands on BR/EDR in ~20ms and discovery returns zero services,
  which `refresh()` cannot fix. Only the LE address works, so `BleAdaptersListPreferences` hides
  `DEVICE_TYPE_CLASSIC` devices and keeps the stored address in its entries, otherwise the summary
  reads "Not set" once the scan results are gone.
* **Failures are invisible unless broadcast.** ObdMetrics' `Lifecycle.onConnecting()` catches and
  logs whatever `connect()` throws, leaving `connector` null and `CommandLoop` spinning — the UI
  shows "connecting" forever. `BleConnection` sends `DATA_LOGGER_BLE_NOT_REACHABLE` (no adapter
  answered) or `DATA_LOGGER_ERROR_CONNECT_EVENT` (discovery/profile failure) itself.

### DataLoggerService (`:datalogger/.../DataLoggerService.kt`)
* **A null intent in `onStartCommand` is a system restart, not a command — stop, don't go
  foreground.** From the background Android 12+ refuses `startForeground()` with
  `ForegroundServiceStartNotAllowedException` (an `IllegalStateException`, not a
  `SecurityException`), which crashed production. Commands always arrive as explicit intents, so
  the service returns `START_NOT_STICKY`; `START_STICKY` only ever bought those null restarts.

### Preferences & localization
* Preference UI: `app/src/main/res/xml/preferences.xml` (Connection, Adapter init, Adapter advanced, PIDs, Profile, Debug). Code defaults in `Prefs.getBoolean(key, default)` should match the XML `android:defaultValue` — the XML value gets persisted once the settings screen is opened.
* `preferences.xml` references custom preference classes by **fully-qualified name**, so renaming
  one and missing the XML fails at *runtime*, not compile time. `PreferencesXmlTest` (`:app` unit test) now loads
  every such class; opening the settings screen is still the final check.
* **One adapter path: generic ELM327** (spec `doc/specs/generic-elm327-adapter.md`). The "Adapter type" (VGate vLinker
  MS) and "Init Sequence" settings were upstream's Alfa MS-CAN preset, not the user's vLinker; its ObdMetrics classes
  (`org.obd.metrics.init.VgateVlinkerMs*`) do not exist in the fork, so it only ever changed protocol / error settings.
  `adapterInit()` (`InitHeaders.kt`) builds the `Init` with ObdMetrics' default registry and sequence. The user's
  vLinker = connection type `bluetooth` (Classic) + "Default"/generic path; don't reintroduce an adapter-type picker.
* The connection type uses **two** arrays: `pref.connection_type_array` (persisted values, never
  localized or reordered) and `pref.connection_type_entries` (display labels). They are
  index-aligned, and `ConnectionTypeListPreference` drops `mock` in release builds by *index* to
  keep them so.
* Strings exist only in `values/strings.xml` (EN) and `values-pl/strings.xml` (PL). Every new user-facing string must be added to **both**.
  The app follows the system locale and theme; the in-app language / theme pickers were removed with the wizard.
