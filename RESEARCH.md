# G-Series Indicators — research notes

Car: BMW G31 LCI 520d MHEV (B47 diesel, 48 V belt starter-generator, ZF 8HP)
Goal: replace BimmerLink's dashboard/widget with a custom-designed gauge display.
Date: 2026-10-19

## Key constraint found

BimmerLink does not publish an API, broadcast intents or a plugin interface for other apps
(nothing found in docs, store listings or forums). A custom UI therefore **cannot reskin
BimmerLink's data** — it needs its own connection to the car through the OBD adapter.
Most Bluetooth/Wi-Fi adapters serve one app at a time, so the new app replaces BimmerLink
while driving (BimmerLink stays useful for coding, DPF/battery services, fault codes).

Data comes from two kinds of parameters:

- **Standard OBD-II (Mode 01)** — any app can read these, no BMW knowledge needed.
- **BMW-specific (UDS Mode 22 on the DDE, gearbox, 48 V modules)** — needs the DID
  (identifier), ECU address and scaling formula. On F/G cars over ELM-type adapters this is
  usually request header `6F1` with the target ECU address as the first byte
  (`12` = DDE engine, `18` = EGS gearbox), responses on `612`/`618`. The B47 DIDs are not
  publicly documented; they have to be discovered (see "Getting the BMW parameters").

## Decisions (2026-10-19)

- **Adapter:** Vgate vLinker over Bluetooth, the one BimmerLink already uses. BimmerLink reads
  DDE values on this car through it, so the adapter can reach the ECUs with BMW addressing.
  Still to confirm: the exact model (MC+ / BM+) and that it accepts `ATCEA`.
- **Display:** car screen through Android Auto.
- **Base:** fork [tzebrowski/ObdGraphs](https://github.com/tzebrowski/ObdGraphs) + [ObdMetrics](https://github.com/tzebrowski/ObdMetrics).

### What the ObdGraphs code showed (checked 2026-10-19)

- **Android Auto drawing:** the `automotive` module uses the Car App Library's
  `NAVIGATION` category with a `NavigationTemplate` surface
  (`automotive/.../aa/screen/nav/SurfaceRendererScreen.kt`). That gives a free canvas, so
  any gauge style can be drawn, not just Android Auto list templates.
- **Gauge code is isolated:** `screen_renderer/.../renderer/gauge/` (`GaugeDrawer.kt`,
  `GaugeGeometry.kt`, `GaugeGrid.kt`). Restyling means a new drawer here; the data
  pipeline stays untouched.
- **Bluetooth Classic is supported:**
  `datalogger/.../connectors/BluetoothClassicConnection.kt`.
- **Vehicle profiles** are `.properties` files (`profile/src/test/assets/alfa_2_0_gme.properties`)
  that map a "mode id" to a CAN header, e.g. `header_value.mode_2=DA10F1` for `"22"`.
  PID JSON files pick a header with `"overrides": {"canMode": "<mode id>"}`.
- **Gap: no BMW extended addressing.** `ObdMetrics/.../api/CANMessageHeaderMultiplexer.java`
  only sends `ATSH<header>` when switching ECU. BMW needs per-ECU
  `ATSH6F1` + `ATCEA<ecu>` + `ATCRA6<ecu>` (plus flow-control setup for multi-frame
  replies). Now added, see "BMW extended addressing" below.
- **Install:** a self-built APK only shows up in Android Auto with Android Auto developer
  settings → "Unknown sources" enabled. The same is true for any sideloaded app,
  aa-torque included.
- **Build prerequisites on this Mac:** no JDK or Android Studio installed yet.

### BMW extended addressing (added 2026-10-19, not yet tried on the car)

ObdMetrics change: local clone `../ObdMetrics`, branch `feat/bmw-extended-addressing`
(uncommitted), installed into `~/.m2` as `11.26.1-SNAPSHOT`, which ObdGraphs picks up first.

Profile keys: each entry of the header table (`mode_N`) takes two optional keys next to
`id_value` / `header_value`. Quote numeric values: an unquoted `12` is restored as a number.

```properties
profile_N.pref.adapter.init.mode.id_value.mode_2="22"
profile_N.pref.adapter.init.mode.header_value.mode_2=6F1
profile_N.pref.adapter.init.mode.extended_address_value.mode_2="12"
profile_N.pref.adapter.init.mode.receive_address_value.mode_2="612"
profile_N.pref.adapter.init.mode.description_value.mode_2="DDE"

profile_N.pref.adapter.init.mode.id_value.mode_3="EGS"
profile_N.pref.adapter.init.mode.header_value.mode_3=6F1
profile_N.pref.adapter.init.mode.extended_address_value.mode_3="18"
profile_N.pref.adapter.init.mode.receive_address_value.mode_3="618"
```

- `extended_address_value` = the ECU address (`12` DDE, `18` EGS). Empty or missing = normal
  addressing, as before. `receive_address_value` = the CAN id the ECU answers on (`ATCRA`);
  optional, but set it, otherwise the adapter keeps its default receive filter.
- PIDs choose the ECU as before: mode `22` uses the entry whose `id_value` is `"22"`;
  another ECU needs `"overrides": {"canMode": "EGS"}` in the PID JSON.
- What is sent when switching to such an entry: `ATSH6F1`, `ATCEA12`, `ATCRA612`,
  `ATFCSH6F1`, `ATFCSD12300000`, `ATFCSM1`. Switching back to a normal header sends
  `ATCEA`, `ATCRA`, `ATFCSM0` before its `ATSH` (only when the table has an extended entry).
- Replies: the `F1` (tester address) byte is stripped while `ATCEA<ecu>` is active, also with
  the PCI byte / padding or after every `N:` of a multi-frame reply, in case the vLinker shows
  them. Replies without it pass unchanged.
- No UI for the two keys yet: they come from the profile, and editing a table entry in the
  app keeps them.
- To check on the car (debug logs, `TX:` / `RX:` lines): that the vLinker accepts each AT
  command with `OK`, what a DDE reply looks like, and that a multi-frame reply (e.g. VIN
  `22F190`) decodes. STN batch queries (`STPX H:`) leave out the header for these entries
  and rely on the `ATSH`/`ATCEA` already sent; not tested on an STN chip.

## 1. Ten indicators for a 520d MHEV

> Superseded for the app by `doc/specs/classic-dashboard.md` (2026-10-19): the user dropped DPF soot
> and added intake air temp and oil level. The table below is kept as the original research.

Bands are rough starting points to tune against your own car, not BMW specs.

| # | Indicator | Why it matters on this car | Source | Suggested colour bands |
|---|-----------|----------------------------|--------|------------------------|
| 1 | **Engine oil temperature** | The real "is it safe to push" signal. Diesel oil warms much slower than coolant. | BMW (DDE) | blue < 70 °C · green 70–115 · amber 115–125 · red > 125 |
| 2 | **Coolant temperature** | Thermostat is map-controlled, so the iDrive needle hides real swings. | Standard PID `05` | blue < 70 · green 70–105 · red > 110 |
| 3 | **Gearbox (8HP) oil temperature** | Towing, mountain roads, hard driving. | BMW (EGS) | blue < 50 · green 50–100 · amber 100–115 · red > 115 |
| 4 | **Boost pressure (actual vs target)** | Showing both reveals leaks, sticky VNT or limp mode early. | Actual: standard PID `0B` (absolute); target: BMW (DDE) | needle + target marker |
| 5 | **DPF soot loading** | Diesel essential: tells you a regeneration is coming. | BMW (DDE) | green < 70 % · amber 70–100 % · red > 100 % |
| 6 | **DPF regeneration active + km since last regen** | Don't switch off mid-regen; short regen intervals point to a problem. | BMW (DDE) | lamp + counter |
| 7 | **Exhaust gas temperature before DPF** | Confirms regeneration (≈ 550–650 °C) and post-load cool-down before switching off. | BMW (DDE), sometimes standard PID `78` | grey < 300 · green 300–550 · orange "regen" > 550 |
| 8 | **48 V battery state of charge** | MHEV-specific: shows whether the mild hybrid has energy to assist. | BMW (48 V battery / DC-DC module) | bar 0–100 % |
| 9 | **Starter-generator power (+assist / −recuperation)** | Makes the mild hybrid visible: assist when pulling away, recuperation when coasting/braking. | BMW (starter-generator / DDE) | centre-zero bar, kW |
| 10 | **12 V system voltage** | DC-DC charging health; low values point to a tired AGM battery. | Standard PID `42` | red < 12.0 V · amber 12.0–12.4 · green 12.4–14.8 |

Bench (swap in if a top-10 parameter can't be read): intake air temp (PID `0F`),
AdBlue level/range, fuel rail pressure, instant fuel consumption, oil level.

## 2. Existing projects to build on

GitHub stats checked 2026-10-19.

| Project | Stack / licence | Activity | Where it shows | Data source | Fit |
|---------|-----------------|----------|----------------|-------------|-----|
| [agronick/aa-torque](https://github.com/agronick/aa-torque) | Kotlin, GPL-3.0, 556★ | last push 2025-03 | Android Auto (unofficial, sideloaded) | **Torque Pro** + its custom PIDs | Best gauge visuals out of the box, but depends on Torque Pro (closed source, paid, barely maintained), needs a sideload and Android Auto developer mode. |
| [pagrey/aa-torque](https://github.com/pagrey/aa-torque) | — | — | — | — | **Identical to agronick/aa-torque** (0 commits ahead). Fork upstream instead. |
| [tzebrowski/ObdGraphs](https://github.com/tzebrowski/ObdGraphs) + [ObdMetrics](https://github.com/tzebrowski/ObdMetrics) | Kotlin, Apache-2.0, 63★ | pushed today | Phone + **Android Auto gauge renderer**, on Google Play | Own OBD stack (ELM327/STN). JSON PID files support **Mode 22**, custom CAN headers, multiple ECUs and JS formulas | **Strongest base.** No Torque dependency, permissive licence, built as a "template for other vehicles" through vehicle profiles. Currently Alfa Romeo-focused; one maintainer. |
| [uholeschak/ediabaslib](https://github.com/uholeschak/ediabaslib) (Deep OBD app) | C#/.NET, GPL-3.0, 933★ | pushed today | Phone + Android Auto (list-style live data) | Real BMW EDIABAS ECU descriptions: knows oil temp, DPF, etc. **without PID hunting** | Ideal for *discovering* B47 values and as a fallback. Its pages are XML-configured, so heavy visual customisation is limited. G-series support depends on the adapter (ENET or DoIP). |
| [fr3ts0n/AndrOBD](https://github.com/fr3ts0n/AndrOBD) | Java, GPL-3.0, 2.1k★ | active | Phone dashboard/HUD, no Android Auto | ELM327, plugin system | Only if you want a phone-only display. |
| [jilleb/mqb-pm](https://github.com/jilleb/mqb-pm) | Java, GPL-3.0 | dead since 2022 | Android Auto | VAG-specific | aa-torque's ancestor; history only. |
| [BimmerGestalt/AAIdrive](https://github.com/BimmerGestalt/AAIdrive) | Kotlin, MIT | maintenance only | iDrive Connected Apps | Car's own data service | **Not usable**: targets NBT/ID4/ID5, not ID7 (G31 LCI). |

### Recommendation

1. **Fork ObdGraphs/ObdMetrics** as the base: write a "BMW B47 G-series" vehicle profile
   (JSON PIDs for the 10 indicators) and restyle the gauge renderer to the look you want.
   It's actively maintained, Apache-licensed, already draws gauges on Android Auto, and
   doesn't need Torque.
2. Use **Deep OBD** (or BimmerLink's own value list) to find which values exist on your
   DDE/EGS/48 V modules and their real-world ranges.
3. Keep **aa-torque** as plan B if ObdMetrics can't handle BMW's extended addressing
   (`6F1` + target byte). Torque Pro is known to work with that pattern on F-series cars.

### First spike (before any UI work)

1. Install Android Studio (bundles a JDK), fork and build ObdGraphs' Android Auto
   variant as-is, and enable Android Auto "Unknown sources".
2. Connect it to the vLinker and read the standard PIDs (coolant `05`, MAP `0B`,
   voltage `42`, IAT `0F`) on the car screen. This proves the Bluetooth link and the
   Android Auto path.
3. Add BMW extended addressing to ObdMetrics (see the gap above) and read one DDE value
   (oil temperature) with a hand-written PID entry.
4. If all three work, the rest is finding the DIDs, building the B47 profile and
   designing the gauges.

## Getting the BMW parameters (DIDs)

- Deep OBD's configuration generator lists ECU measurement jobs from EDIABAS descriptions.
- Community PID lists for F-series N47/B47 (BimmerPost, e90post) are a starting point;
  many DDE DIDs carry over between DDE generations, but verify each one on the car.
- Fallback: log what BimmerLink requests with a CAN sniffer on the OBD port.

## Sources

- [agronick/aa-torque](https://github.com/agronick/aa-torque), [pagrey/aa-torque](https://github.com/pagrey/aa-torque)
- [tzebrowski/ObdGraphs](https://github.com/tzebrowski/ObdGraphs), [tzebrowski/ObdMetrics](https://github.com/tzebrowski/ObdMetrics), [simieng/ObdGraphsGiulia](https://github.com/simieng/ObdGraphsGiulia)
- [uholeschak/ediabaslib](https://github.com/uholeschak/ediabaslib), [Deep OBD adapter types](https://github.com/uholeschak/ediabaslib/blob/master/docs/AdapterTypes.md), [Deep OBD on Google Play](https://play.google.com/store/apps/details?id=de.holeschak.bmw_deep_obd)
- [fr3ts0n/AndrOBD](https://github.com/fr3ts0n/AndrOBD), [BimmerGestalt/AAIdrive](https://github.com/BimmerGestalt/AAIdrive), [jilleb/mqb-pm](https://github.com/jilleb/mqb-pm)
- [BMW custom PIDs and Torque Pro (F30 BimmerPost)](https://f30.bimmerpost.com/forums/showthread.php?t=1483707), [Torque Pro + BMW PIDs (bimmersport.co.nz)](https://bimmersport.co.nz/topic/62104-torque-pro-bmw-pids/)
- [BimmerLink App Store listing](https://apps.apple.com/gb/app/id1065360416)
- [Auto Insights Dashboard](https://aid.cs.ucy.ac.cy/) (MIT, Android Auto OBD; site certificate failed, not reviewed)
