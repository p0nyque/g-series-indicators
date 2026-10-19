# Tech design: Classic dashboard

2026-10-19 · Status: phase 1 implemented 2026-10-19 (mock data; checked by Robolectric renders, not yet on a DHU
or the car). Phases 2–3 open. Companion to [classic-dashboard.md](classic-dashboard.md), which owns
*what* is drawn (indicators, scales, colours, layout). This file owns *how* it is built: files, data flow, phases,
tests, and what is still unknown. When the two disagree, fix both in the same change.

## 1. Research findings (code read 2026-10-19)

What the existing code gives us, and what it means for this screen.

| # | Finding | Where | Consequence |
|---|---|---|---|
| F1 | A screen = `SurfaceRendererType` + `ScreenBehavior` + `SurfaceRenderer`. Two exhaustive `when`s allocate them. | `SurfaceRenderer.allocate`, `ScreenBehaviorController.getScreenBehavior` | Adding `CLASSIC` fails to compile until both have a branch. Nothing else in `:app` switches on the type. |
| F2 | AA screen list = `SurfaceRendererScreen.getFeatureDescription()` (icon + title), shown by `AvailableFeaturesScreen`. The last screen is persisted as its code in `pref.aa.screen.last_used`. | `SurfaceRendererScreen.kt`, `CarSettings.getLastVisitedScreen` | Code `6` must never change once shipped. |
| F3 | Frames come from the AA rendering thread at `pref.aa.surface.fps`, **default 5 fps** (1–25). Frames run while connected only. | `CarSettings` `DEFAULT_FRAME_RATE`, `NavTemplateCarScreen` | Easing at 5 fps looks like stepping. The BMW profile sets 15 fps (see Q6). |
| F4 | The canvas is `lockHardwareCanvas()`. | `SurfaceRendererController.renderFrame` | `BlurMaskFilter` is only hardware-accelerated from API 28; `minSdk` is 26. On API 26/27 the blur is skipped, so the glow must still look acceptable as a flat stroke. |
| F5 | `MetricsCollector.applyFilter(ids)` builds a `Metric` for every id **found in the PID registry**. Ids not in the registry get no `Metric` (`getMetric(id)` = null), and ObdMetrics drops them from the query without an error. | `InMemoryCarMetricsCollector`, CLAUDE.md "Readiness" | A slot whose PID file is not loaded simply stays at rest. No crash, but also no hint (see Q8). |
| F6 | `Metric.value` is `Any?`; a new reading = a new `metric.source` object. | `Metric.kt`, `GaugeFrameStates.isStale` | Read values as `(value as? Number)?.toDouble()`. Staleness by source identity, as on the Gauge screen. |
| F7 | `GaugeFrameStates` (easing, staleness, eviction) is `internal` to `:screen_renderer`, pure and tested. Stale after **10 s** for all PIDs. | `renderer/gauge/GaugeFrameStates.kt` | Reusable as is. 10 s is too short for priority-2 PIDs (see F9), so Classic uses a second instance with a longer limit for slow slots. |
| F8 | Fixed-set query strategies exist (`ReadinessQueryStrategy`: `getPIDs()` returns a constant; `update()` is ignored in effect). `QueryStrategyOrchestrator.getIDs()` adds `VEHICLE_STATUS` when the status panel / disconnect-when-off is on. | `bl/query/` | Copy the readiness pattern. Classic draws by slot id, never by iterating the collector, so an injected `VEHICLE_STATUS` cannot appear on screen. |
| F9 | ObdMetrics priorities only apply when the batch policy is on (`pref.adapter.batch.enabled`, default true). Priority 0 = every cycle, 1 = every 6th, 2 = every 16th. Commands are sorted by PID and batch-encoded; the header is switched per command in buffer order. | ObdMetrics `CommandProducer`, `CommandsSuplier`, `ProducerPolicy` | Header switches are not grouped per ECU. Each switch into an extended entry costs 6 AT commands. This is the main refresh-rate risk (§6). |
| F10 | With STN extensions on, the producer does not call `switchHeader`; STN batches rely on an already-sent `ATSH`/`ATCEA`. | `CommandProducer.addCommandsToTheBuffer`, bmw-extended-addressing spec | The BMW profile must keep `stnExtensionsEnabled = false` (vLinker is not an STN chip anyway). |
| F11 | Mode 01 ids in ObdMetrics: coolant `6`, MAP `99` (bar abs, 1 byte, max 2.55), boost `12` (MAP − 1), IAT `16`, baro `52` (kPa), module voltage `67`. **`01 78` (EGT) is not defined** in `mode01.json`. | ObdMetrics `mode01.json`, `mode01_2.json` | The spec's EGT fallback `01 78` needs a new PID entry; B47s rarely answer it. Treat S2 as DDE-only. |
| F12 | A PID file is listed in the profile's `pref.pids.registry.list` and found through the context class loader (ObdMetrics `Urls.resourceToUrl`); its display name comes from `Modules.overrides`. A file from phone storage (`storage:` prefix) is also accepted. | `common/.../Modules.kt`, `WorkflowOrchestrator.pids()` | Any module's `src/main/resources/` lands at the APK root next to `mode01.json`, so `bmw_b47_mhev.json` lives in the app (`:datalogger`), not in ObdMetrics: no jar rebuild per DID change. Phone storage stays an option for quick tries on the car. |
| F13 | Debug builds have a `mock` connection: `SmartMockConnectionFactory` answers **every PID in the registry** with a sawtooth. | `ConnectionManager.mockConnection` | The whole screen, BMW slots included, can be built and checked on the DHU before any DID is known, using placeholder DIDs (§5, phase 1). |
| F14 | Bundled fonts: `Roboto-Regular.ttf`, `Roboto-LightItalic.ttf`. No upright Roboto Light. | `screen_renderer/src/main/assets/fonts/` | Add `Roboto-Light.ttf` (Apache 2.0, same family): Samsung's font setting replaces the system `sans-serif-light`. Added: v2.137 from `google/fonts` commit `724bf98e` (`apache/roboto/`), the release of the bundled Regular (same glyphs; ours has a trimmed `cmap` / `name`). No fallback. |
| F15 | Profiles: ids in use `profile_1`–`10`, `13`–`15`, `20`. AA profiles live in `automotive/src/main/assets/`. | `*.properties` | New profile `profile_21`. |
| F16 | Web search (forums, 2026-10-19): no public B47 DDE DID list. One F11 thread reports BMW DDEs wanting dynamically defined DIDs (UDS `2C`) instead of plain `22` reads. | see Sources | DIDs must be found on the car. If plain `22` is refused, ObdMetrics needs `2C` support (Q2). |

## 2. Open questions and missing info

Blocking = work that cannot finish without the answer. Each has an assumption so coding can start.

| # | Question | Blocks | Who / how | Assumption until answered |
|---|---|---|---|---|
| Q1 | **DIDs, lengths and scalings** for oil temp, boost actual/target, gearbox oil temp, EGT, oil level (+ raw MIN/MAX, "not measured" value), 48 V SOC, starter-generator power, regen active, km since regen. | Phase 2 (M1, M3, M4, S2–S5, T1) | User, on the car: Deep OBD job list for DDE `12`, EGS `18`, 48 V module; cross-check with BimmerLink. | Placeholder DIDs in a dev PID file (§5). Slots draw mock data on the DHU. |
| Q2 | Does the DDE answer a plain `22 <DID>` behind `6F1`/`CEA 12`, or only `2C`-defined DIDs? Does it accept several DIDs in one `22` request (batch)? | Phase 2 | User, on the car: send one known DID by hand (e.g. `22F190`, VIN, multi-frame) and one live value. | Plain `22` works; mode-22 PIDs set `"batchEnabled": false` until a batched reply is seen to decode. If only `2C` works: new ObdMetrics feature, separate spec. |
| Q3 | Which module carries the 48 V SOC and the starter-generator power, and its address (`ATCEA`) / reply id? | S4, S5 | Deep OBD module list (look for the 48 V battery / DC-DC / starter-generator entries). | `mode_4` `BAT48` entry left with placeholder address; both slots at rest on the car. |
| Q4 | vLinker model (MC+ Classic BT or BM+ / BLE?) and whether it answers `OK` to `ATCEA`, `ATCRA`, `ATFCSH`, `ATFCSD`, `ATFCSM`. | Phase 2 | User: label on the adapter; one session with logs (`TX:`/`RX:`). | Bluetooth Classic (`bluetooth`). |
| Q5 | Mode 01 header: functional `7DF` (spec) or physical `7E0`? With `7DF`, both DDE and EGS may answer a mode 01 request, and a two-ECU reply can mis-decode. | Phase 1 on the car | User: one log of `01 05` with each header. | **`7E0`**, receive `7E8`. Change the main spec's header table if confirmed. |
| Q6 | Frame rate for this profile: 15 fps (smooth easing, more phone heat/battery) vs the app's 5? | — | User's choice. | `pref.aa.surface.fps="15"` in the BMW profile only. |
| Q7 | Should AA open on Classic when no screen was visited yet? Today the fallback is `GIULIA`. | — | User's choice. | No: Classic is picked once from the screen list, then remembered (F2). |
| Q8 | When the BMW PID file is not loaded (or a slot's PID is missing from the registry), show a hint, or only dials at rest? | — | User's choice. | Dials at rest only, plus a `Log.w` once per missing id. No on-screen text (keeps the "cluster" look). |
| Q9 | Real refresh rate with 4 headers per cycle. Is boost (priority 0) fast enough, ≥ 4 Hz? | Phase 3 | Measure on the car (`Fps`/rate in logcat). | See §6 for the levers, in order. |
| Q10 | Oil level PID semantics: is it a level in mm / litres, or a 0–n raw index? Is "not measured" a sentinel value or a separate status DID? | S3 | Same session as Q1. | Raw value, sentinel `null` / out of `[min, max]` = rest. |

Not open (decided in the main spec, do not re-ask): no alerts, fixed slots, no phone version, one page, Live Cockpit
styling, printed zones static.

## 3. Architecture

```
ObdMetrics (BMW PID file + mode01) ──► DataLoggerRepository ──► MetricsCollector
        ▲ query ids                                                │ getMetric(id)
ClassicQueryStrategy (CLASSIC_QUERY, fixed ids)                     ▼
        ▲ queryStrategyType()                       ClassicSurfaceRenderer.onDraw (AA rendering thread)
ClassicScreenBehavior ── allocates ──►                 ├─ ClassicLayout       (pure: rects, radii, angles)
        ▲                                              ├─ ClassicReadings     (pure: metric → reading, boost − baro)
ScreenBehaviorController ◄── SurfaceRendererScreen     ├─ GaugeFrameStates ×2 (easing, staleness)
                              (CLASSIC in feature list) ├─ ClassicStaticLayer  (bitmap: faces, ticks, numerals, zones)
                                                       └─ ClassicDrawer       (Canvas: glow, lines, pointers, text)
```

### 3.1 Files

`:datalogger` — `bl/query/`

| File | Content |
|---|---|
| `ClassicPids.kt` (new) | `object ClassicPids` with every id the screen uses: the mode 01 ids from F11 and the `40001+` BMW ids. **The one place ids are written down**; the query and the slot table both read from here. `ALL: Set<Long>`. |
| `ClassicQueryStrategy.kt` (new) | `getPIDs()` = `ClassicPids.ALL` minus the boost fallback (`99`) when the DDE boost PID is in the registry (`findBy(id) != null`, the `hasReadinessPids` pattern). `getDefaultPIDs()` = same. |
| `QueryStrategyType.kt` | `+ CLASSIC_QUERY`. |
| `QueryStrategyOrchestrator.kt` | `+ CLASSIC_QUERY to ClassicQueryStrategy()`. |

`:screen_renderer` — `renderer/classic/` (all `internal`)

| File | Content | Pure? |
|---|---|---|
| `ClassicDials.kt` | `enum ClassicKind { MAIN, SUB, CENTRE_ZERO, LEVEL }`; `data class ClassicScale(min, max, numerals: List<Double>, zones: List<ClassicZone>)`; `data class ClassicSlot(key, pidId, fallbackPidId?, kind, scale, decimals, unit, labelRes, slow: Boolean)`; `CLASSIC_MAIN`, `CLASSIC_SUB` (ordered lists, M1–M4, S1–S6); `CLASSIC_REGEN` (two ids). Values from the main spec's indicator table. | yes |
| `ClassicReadings.kt` | `reading(slot, raw: Double?, stale, baroKpa)` → `ClassicReading(fraction: Float?, text)`. Boost: `abs − (baroKpa ?: 101.3) / 100`. Clamping, rest (`null` → no fraction, text `--`), centre-zero sign (`+3.2`, `−1.0`, `0.0`), typographic minus, level (no text, out of range = not measured). `regen(active, km)` → `ClassicRegen(lit, distance)`. | yes |
| `ClassicLayout.kt` | `fun layout(area: RectF): ClassicLayout` → three band rects, cell centres, main radius, sub radius, `k` factors, status strip rect. `fun angle(kind, fraction?)` → degrees, rest angle for `null`, 0 at top for centre-zero. Formulas from the main spec's Layout section. | yes |
| `ClassicStaticLayer.kt` | Two `ARGB_8888` bitmaps keyed by canvas size + layout: *under* (faces, sub-dial bands) and *over* (edges, zones, ticks, numerals, labels, units). The glow is drawn between them, as in the mockups: drawn over one flat bitmap it tinted the ticks and numerals. Recycled on a size change and in `recycle()`. | no |
| `ClassicDrawer.kt` | Per-frame Canvas work: glow, progress lines, pointers, target marker, readouts, regen lamp and text. Owns its `Paint`s and typefaces. **Does not extend `AbstractDrawer`** (see D2). | no |
| `ClassicSurfaceRenderer.kt` | `SurfaceRenderer`: area → layout (cached by area) → per slot: metric (primary, else fallback), staleness, reading, eased fraction → under layer, glow, over layer, values, strip. `isRunning` and `clock` are injectable. Not running: every slot at rest without easing, frame states cleared. `invalidate()` drops layout + bitmaps; `recycle()` also clears frame states. | no |
| `api/SurfaceRendererType.kt` | `+ CLASSIC(6)`. | |
| `api/SurfaceRenderer.kt` | `+ CLASSIC -> ClassicSurfaceRenderer(...)`. | |
| `assets/fonts/Roboto-Light.ttf` | New (F14). Done. | |

`:screen_behavior` — `ClassicScreenBehavior.kt` (TripInfo pattern, `queryStrategyType() = CLASSIC_QUERY`),
`ScreenBehaviorController` branch.

`:automotive` — `SurfaceRendererScreen`: `CLASSIC to settings` in the settings map, a `FeatureDescription(CLASSIC,
R.drawable.action_classic, …)` always listed. New vector drawable `action_classic.xml` (a round dial). Strings
`available_features_classic_screen_title` ("Classic" / PL "Klasyczny").

`:common` — `Modules.overrides` + `bmw_b47_mhev.json`. Dial label strings live in `:screen_renderer`'s `strings.xml`
(`classic_label_oil`, …, EN + PL, same text in both: cluster labels are not translated; only `Regenerating` is).

`:automotive` assets — `bmw_g31_520d_mhev.properties` (`profile_21`), §4.
`:datalogger` — `src/main/resources/bmw_b47_mhev.json` (F12), §4.

### 3.2 Decisions

- **D1. Ids live in `:datalogger` (`ClassicPids`), drawing data in `:screen_renderer` (`ClassicDials`).** The query
  must not depend on the renderer, and the renderer already depends on `:datalogger`. A test pins that every slot id is
  queried (§7).
- **D2. `ClassicDrawer` does not extend `AbstractDrawer`** (main spec said it would; corrected there). `AbstractDrawer`
  brings the colour theme, the background bitmap and Giulia-scoped settings (CLAUDE.md pitfall: global-looking
  `ScreenSettings` methods are keyed to the Giulia virtual screen). Classic ignores theme and settings by design. It
  takes `Context` only; `ScreenSettings` is accepted by the renderer for the factory signature and not read, except
  `isAA()`.
- **D3. Draw by slot, never by `getMetrics()`.** Removes the `VEHICLE_STATUS` exclusion problem (F8) and keeps slot
  positions fixed when a PID is missing.
- **D4. PID formulas output display units** (°C, bar absolute, %, kW, V, km, raw level). The renderer never converts
  units, with one exception: relative boost needs baro, which is a separate PID. Actual and target both go through
  the same `− baro`.
- **D5. Easing on the scale fraction (0..1)**, via `GaugeFrameStates.needle(id, fraction, now)`. Two instances: fast
  slots (boost, starter-generator: stale after 10 s) and slow slots (everything else, `slow = true`, the default: stale
  after 60 s). Slow includes the mode 01 temperatures: `mode01.json` gives coolant and IAT priority 2, read every
  16th cycle, which a 10 s limit would grey out.
  Rest = ease towards the rest fraction, so a dial that loses data falls back smoothly instead of jumping.
- **D6. Static layers as two bitmaps per area size** (under / over the glow, see `ClassicStaticLayer`). Faces with radial gradients, ticks and numerals are the expensive
  part; redrawing them at 15 fps is wasted. Per frame: ≤ 10 glow arcs, 10 progress arcs, 10 pointers, ~12 text runs.
- **D7. Glow** = arc stroke with `BlurMaskFilter(NORMAL)`, clipped to the face circle. If phase 3 shows frame drops,
  switch to: per main dial, render the full-sweep glow into a bitmap once (inside the static layer cache), and each
  frame draw it clipped to a pie wedge up to the value angle (`clipPath`). Decide by measurement, not up front.
- **D8. No status panel, no FPS counter, no scrollbar** on Classic, whatever the AA settings say. The layout uses the
  whole visible area.

## 4. Data: profile and PID file

### 4.1 `bmw_b47_mhev.json` (`datalogger/src/main/resources/`)

Same schema as `giulia_2.0_gme.json`, group `livedata`. Ids `40001`–`40099`. Rules:

- `mode` `"22"`, `pid` = DID hex, `length` from discovery, `formula` → display units (D4). Signed values (starter-
  generator, maybe EGT) need an explicit two's complement in the formula: `((A*256+B) << 16 >> 16) / 100`.
- `min`/`max` = the indicator's scale from the main spec (they feed nothing else here but keep PID dialogs sane).
- `priority`: 0 boost actual + target, starter-generator; 1 temperatures, SOC; 2 oil level, regen status, regen km.
- `overrides`: `{"canMode": "EGS"}` for gearbox oil, `{"canMode": "BAT48"}` for 48 V PIDs (Q3), and
  `"batchEnabled": false` on every mode-22 PID until Q2 says batching works.
- Proposed id allocation (fill DIDs from Q1):

  | Id | Indicator | ECU / canMode |
  |---|---|---|
  | 40001 | Engine oil temp | DDE (`22`) |
  | 40002 | Boost actual (bar abs) | DDE |
  | 40003 | Boost target (bar abs) | DDE |
  | 40004 | EGT before DPF | DDE |
  | 40005 | Oil level (raw) | DDE |
  | 40006 | Starter-generator power (kW, signed) | DDE or `BAT48` (Q3) |
  | 40007 | DPF regen active (0/1) | DDE |
  | 40008 | Distance since last regen (km) | DDE |
  | 40020 | Gearbox oil temp | `EGS` |
  | 40030 | 48 V SOC (%) | `BAT48` |

- **Phase 1 ships placeholder DIDs `FD01`–`FD30`** (system-supplier range, read-only, so harmless on the car) and
  placeholder scalings. JSON has no comments: the placeholders are recorded here and in CLAUDE.md. Replace them in
  phase 2; on the car they answer NO DATA or a negative response and their slots stay at rest.

### 4.2 `bmw_g31_520d_mhev.properties` (`profile_21`, `automotive/src/main/assets/`)

Header table as in the main spec, with `mode_1` per Q5 (`7E0`). Other keys that matter (everything else copied from
`alfa_2_0_gme_aa.properties` defaults):

```properties
profile_21.pref.adapter.connection.type=bluetooth
profile_21.pref.adapter.init.protocol=CAN_11
profile_21.pref.adapter.batch.enabled=true
profile_21.pref.adapter.stn.enabled=false          # F10
profile_21.pref.aa.surface.fps="15"                 # Q6
profile_21.pref.pids.registry.list=[mode01.json, mode01_2.json, bmw_b47_mhev.json]
profile_21.pref.pids.registry.filter_pids_ecu_supported=false   # mode 22 PIDs are not in the 01 00 bitmap
profile_21.pref.pids.registry.filter_pids_stable=false
pref.profile.names.profile_21=BMW G31 520d MHEV (BT,AA)
```

Quote every numeric value (CLAUDE.md: unquoted numbers load as `Int`).

## 5. Phases

Each phase ends with something that runs. Phase 1 needs nothing from the car.

| Phase | Scope | Done when |
|---|---|---|
| **0. Car spike** (user) | Q1–Q5 answered: AT commands accepted, one DDE DID and one multi-frame DID decode, Deep OBD job list exported, mode 01 header chosen. | DID table filled in the main spec. |
| **1. Screen on mock data** ✅ 2026-10-19 | All of §3.1 except the real DIDs. A *dev* `bmw_b47_mhev.json` with placeholder DIDs (`F001`…) so the mock connection (F13) feeds every slot. AA wiring, layout, dials, readings, static layer, glow, easing. | Renders at 800 × 480 and 1920 × 720 match the mockups ([800](img/classic-dashboard-render-800x480.png), [1920](img/classic-dashboard-render-1920x720.png), from `ClassicRenderTest`); tests, `spotlessCheck`, `:app:lintGiuliaDebug` pass. **Open:** a DHU run with the mock connection (no DHU or phone on the dev Mac yet). |
| **2. Real data** | Real DIDs and scalings, the profile, `Modules` name. Mode 01 slots work on the car first (coolant, IAT, voltage, MAP boost fallback). | Every checklist item in the main spec's "Risks and verification" that needs the car. |
| **3. Tuning** | Refresh rate (§6), glow cost (D7), ranges and printed zones from real readings. | Boost ≥ 4 Hz; no visible frame drops at 15 fps on the DHU and in the car. |

Phase 1 can be split in two PRs if it grows: (a) pure core + query + AA wiring with plain arcs, (b) Live Cockpit
styling, static layer, glow.

## 6. Refresh-rate budget (estimate, verify in phase 3)

Rough ELM-over-Bluetooth figures: ~40 ms per request/reply, ~25 ms per AT command. A cycle with all priority groups
due: mode 01 batch (coolant, IAT, voltage, baro: 1 request), DDE (8 PIDs unbatched: 8 requests), EGS (1), BAT48 (1),
plus up to 4 header switches × 6 AT commands ≈ 24. Worst case ≈ 11 × 40 + 24 × 25 ≈ 1.0 s. Most cycles carry only
priority 0 (boost ×2, starter-generator: one header, 3 requests) ≈ 0.15–0.3 s → 3–6 Hz. Levers, cheapest first:

1. Batch the DDE PIDs (if Q2 allows): 8 requests → 1–2.
2. Raise slow PIDs' priority number (2 → 3 or 4).
3. ObdMetrics: order the buffer by header within a cycle so each ECU is entered once (change in `CommandsSuplier`;
   separate spec, it affects every profile).
4. Skip `ATFCSH`/`ATFCSD`/`ATFCSM` when re-entering the same extended entry (ObdMetrics; only if the adapter keeps them).

## 7. Tests

Plain JUnit unless noted. Write the pure ones first; they pin the spec's numbers.

| Test | Module | Pins |
|---|---|---|
| `ClassicLayoutTest` | `:screen_renderer` | Bands 55/32/13 % at 800 × 480 and 1920 × 720; every dial inside the area; no two cells overlap; dials stay round on wide screens (height-bound); angle at min / max / beyond both ends; centre-zero 0 at the top; rest angle for `null`; no text size under 11 px at 800 × 480. |
| `ClassicReadingsTest` | `:screen_renderer` | Decimals per slot; `--` for null and stale; level slot has no text; centre-zero sign (`+3.2`, `−1.0`); boost `abs − baro` with and without baro (1.013 fallback); actual and target go through the same conversion; value beyond scale → fraction clamped, text real; regen text for active / km / null. |
| `ClassicDialsTest` | `:screen_renderer` | Every numeral on its scale; every zone inside its range; main list has 4 slots, sub list 6, in spec order; every slot id ∈ `ClassicPids.ALL` (D1). |
| `ClassicQueryStrategyTest` | `:datalogger` (plain JUnit, registry check injected) | Ids = `ClassicPids.ALL`; `99` dropped when the DDE boost PID is in the registry, kept when not; `update()` does not change the set. |
| `ScreenBehaviorControllerTest` | `:screen_behavior` (extend) | `CLASSIC` allocates a `ClassicScreenBehavior` with `CLASSIC_QUERY`. |
| `SetupProfilesTest` | `:profile` (extend) | Loads the **shipped** asset (`../automotive/src/main/assets`): four header entries; `7E0` / `6F1` and the quoted extended addresses stay strings; registry list, STN off, 15 fps. |
| `ClassicPidsTest` | `:datalogger` | ObdMetrics parses `bmw_b47_mhev.json` from the class loader; it defines exactly the Classic BMW ids; EGS / BAT48 `canMode`; no mode-22 batching; fast PIDs priority 0. |
| `ClassicRenderTest` | `:screen_renderer` (Robolectric, native graphics, SDK 34) | Draws the screen on a real canvas at 800 × 480 and 1920 × 720, regen, no data and disconnected; PNGs in `screen_renderer/build/classic-render/`. Asserts a black background, orange pointers / glow present, and the glow gone when disconnected. |
| ObdMetrics `BmwB47MhevPidsTest` | ObdMetrics (phase 2) | Each PID decodes a captured reply (from the phase 0 logs) to the expected value; the signed formula gives a negative kW. |

`ClassicRenderTest` is the Canvas check while no DHU is available; it does not replace a DHU / car look at frame rate and blur.

## 8. Definition of done (per phase PR)

- Code + tests above, `./gradlew spotlessCheck`, `:screen_renderer:testDebugUnitTest`, `:datalogger:testDebugUnitTest`,
  `:app:lintGiuliaDebug` green.
- Main spec and this file updated (status, any changed number). DHU screenshots in `doc/specs/img/`.
- CLAUDE.md "Classic dashboard" entry updated with what was learnt.

## Sources

- [BMW custom PIDs and Torque Pro app (f30.bimmerpost.com)](https://f30.bimmerpost.com/forums/showthread.php?t=1483707)
- [Extended PIDs BMW F11 (torque-bhp.com)](https://torque-bhp.com/community/main-forum/extended-pids-bmw-f11/paged/2/)
- [Extended BMW 335d PIDs for Torque (e90post.com)](https://www.e90post.com/forums/showthread.php?t=1118429&page=4)
- [How to see charge status of the 48V battery? (g05.bimmerpost.com)](https://g05.bimmerpost.com/forums/showthread.php?t=2041045)
