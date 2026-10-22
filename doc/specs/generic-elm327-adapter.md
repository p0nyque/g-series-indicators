# Spec: Generic ELM327 as the only adapter path (vLinker MS preset removed)

2026-10-22 · Status: implemented on branch `simplify-app`, unit-tested; not yet driven on the car.

## Summary

**Problem.** Adapter → General settings showed **Adapter type** with "Default Adapter" and "VGate vLinker MS". The
user's adapter is a Vgate vLinker over Bluetooth Classic, so "Default Adapter" looked like the wrong default. It was
not: "VGate vLinker MS" is upstream's preset for the Alfa medium-speed CAN climate module. Picking it set the protocol
to `CAN_USER1`, the init sequence to `HS_CAN`, turned "continue on errors" on and added a `TRW_CLIMATE_CONTROL`
(`18DA98F1`) CAN header, none of which the G31 has. Its CAN registry and init sequences
(`org.obd.metrics.init.VgateVlinkerMs*`) are not in the ObdMetrics fork, so the reflection behind it always fell back
to the defaults: the preset only ever changed the side settings.

**Scope.** One adapter path, the generic ELM327 one, which is what a vLinker MC+ / FS / BM+ speaks. The connection
default stays Bluetooth Classic (`bluetooth`), with no adapter preselected.

## Behaviour changes

| Area | Before | After |
|---|---|---|
| Adapter → General settings | "Adapter type" list (Default Adapter / VGate vLinker MS) | Setting removed |
| Adapter init | "Init Sequence" list (DEFAULT / MS_CAN / HS_CAN / CH_CAN) | Setting removed; ObdMetrics' `DefaultCommandGroup.INIT` always |
| CAN network registry | `DefaultCanNetworkRegistry`, or a reflective lookup that always failed | ObdMetrics' default (`DefaultCanNetworkRegistry`) |
| Code default of `pref.adapter.id` | `"OBDII"` (not a MAC; same "adapter not set" outcome as empty) | `""`, matching `preferences.xml` and the profiles |

## Settings / keys touched

- `pref.adapter.type`, `pref.adapter.init.sequence`: no longer shown or read. A value already persisted on a device is
  ignored.
- `pref.adapter.id`: code default `""`.
- Not touched: `pref.adapter.connection.type` (default `bluetooth` in `preferences.xml`, `DataLoggerSettings` and both
  profiles), `pref.adapter.init.protocol`, `pref.adapter.error.continue_on_error`.

## Implementation

- Deleted `app/.../preferences/AdapterTypeListPreference.kt`, its `preferences.xml` entry, the Init Sequence
  `ListPreference`, the arrays `pref.adapter_type_entries` / `_values` / `pref.init_sequence` and their strings (EN, PL).
- `:datalogger`: `Adapter.type` and `Adapter.initSequence` removed. The `Init` is built by `adapterInit(adapter,
  mappings)` in `InitHeaders.kt` (delays, protocol, header table), leaving ObdMetrics' defaults for registry and
  sequence. `WorkflowOrchestrator.init()` calls it.

## Backward compatibility

- Stale `pref.adapter.type` / `pref.adapter.init.sequence` values are ignored, so nothing to migrate for them.
- Someone who once picked vLinker MS still has its side settings (`CAN_USER1`, continue on errors, the
  `TRW_CLIMATE_CONTROL` header) in their prefs. Re-applying the BMW profile (Profile → load) sets the protocol back to
  `CAN_11`; the extra header row can be deleted from the CAN header table. The BMW profile never set any of them.

## Tests

- `InitHeadersTest` (`:datalogger`): the adapter init carries the adapter's delays, protocol and headers, with the
  default CAN registry and init sequence; `Adapter()` defaults to `bluetooth` with no device address.
- `PreferencesXmlTest` (`:app`, plain JUnit, parses `src/main/res/xml/preferences.xml`): every `org.obd.graphs.*`
  preference class named in the XML exists (a removed class otherwise fails only when the screen opens), and the
  adapter-type / init-sequence keys stay gone.

## Risks and verification checklist

- [x] `:app:testGiuliaAADebugUnitTest`, `InitHeadersTest`, `:app:lintGiuliaAADebug`, `spotlessCheck`.
- [ ] Settings → Adapter general settings opens without the Adapter type row; Adapter init without Init Sequence.
- [ ] On the car: the vLinker connects and initialises (`TX:`/`RX:` show `ATZ`, `ATSP6`, header commands).

## Out of scope

- Other Alfa-specific leftovers in the generic settings (e.g. `CAN_USER1` in the protocol list, which is a standard
  ELM327 protocol and stays).
