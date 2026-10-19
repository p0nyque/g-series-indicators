# Spec: CAN extended addressing (BMW F/G modules)

2026-10-19 · Status: implemented 2026-10-19 (ObdMetrics + app, ObdMetrics branch `feat/bmw-extended-addressing`),
not yet tried on a car

## Summary

**Problem.** BMW F/G cars reach their ECUs through the OBD gateway with CAN id `6F1` and the ECU's address as the first
data byte (`12` = DDE engine, `18` = EGS gearbox); the ECU answers on `600 + address` with the tester address `F1` as
its first byte. ObdMetrics only sent `ATSH<header>` per module, so no UDS Mode 22 value of a BMW module could be read.

**Scope.** ObdMetrics (`Init.Header` fields, header switching, reply decoding, STN batch header), `:common` (header table
keys), `:datalogger` (header table → `Init.Header`, DTC scan targets). No UI.

## Behaviour changes

| Area | Before | After |
|---|---|---|
| Header table entry with an extended address | Not possible | `ATSH6F1`, `ATCEA12`, `ATCRA612`, `ATFCSH6F1`, `ATFCSD12300000`, `ATFCSM1` before the module's requests |
| Normal entry after an extended one | — | `ATCEA`, `ATCRA`, `ATFCSM0`, then `ATSH`. Only when the table has an extended entry |
| Tables without extended entries | `ATSH` only | Unchanged |
| Reply with `F1` first while `ATCEA<hh>` is on | Did not decode | `F1` removed (also a shown PCI byte / padding, and `F1` after each `N:` of a multi-frame reply) |
| DTC scan of modules sharing `6F1` | — | One target per header + extended address (was one per header) |
| STN batch (`STPX H:`) for an extended entry | — | No `H:`; relies on the `ATSH` / `ATCEA` already sent |

## Settings / keys touched

New per-entry profile keys next to `id_value` / `header_value` (no XML default, no UI):

- `pref.adapter.init.mode.extended_address_value.mode_N` — ECU address, e.g. `"12"`.
- `pref.adapter.init.mode.receive_address_value.mode_N` — CAN id the ECU answers on, e.g. `"612"`; optional.

Quote numeric values in a `.properties` profile: the loader restores an unquoted `12` as an `Int`. The app reads both keys
as whatever was stored (`Prefs.all`), so an unquoted value still works.

## Implementation

- ObdMetrics `Init.Header`: `extendedAddress`, `receiveAddress`, `isExtendedAddressing()`, `commands(clear)` (the AT
  sequence), `Init.CLEAR_EXTENDED_ADDRESSING`. Every path that sets a header uses `commands()`: live data
  (`CANMessageHeaderMultiplexer.switchHeader`), the VIN read of a DTC action (`headerFor`), DTC module scans, follow-ups,
  routines; module discovery clears extended addressing first.
- The multiplexer remembers whether it left extended addressing on; unknown (after `reset()`) counts as on.
- Reply side: `transport/ExtendedAddressing` follows the transmitted `ATCEA` / `ATZ` and strips the tester address in
  `StreamingConnector.receive()`, so every handler (live, batch, DTC, routines) sees normal-addressing replies.
- App: `DiagnosticMappingItem.extendedAddress` / `receiveAddress`; `initHeaders()` / `toInitHeader()`
  (`datalogger/.../InitHeaders.kt`) build the header table; `dtcScanHeaders` carries both fields.

## Backward compatibility

Entries without the new keys produce exactly the old `ATSH` sequence; nothing extra is sent unless an entry has an
extended address.

## Tests

- ObdMetrics `ExtendedAddressingTest` (mock adapter): a Mode 22 PID `(A*256+B)/100` behind `6F1` / `CEA 12` decodes from
  four reply shapes; switching between it and a `7DF` header emits the set-up and the clear; normal tables send no
  extended commands. `InitHeaderCommandsTest`, `ExtendedAddressingStripTest` (reply shapes, multi-frame, offset).
- App: `InitHeadersTest`, `DtcListItemTest` (modules sharing `6F1` stay separate scan targets).

## Risks and verification checklist

- [ ] vLinker answers `OK` to `ATCEA12`, `ATCRA612`, `ATFCSH6F1`, `ATFCSD12300000`, `ATFCSM1`, bare `ATCEA` / `ATCRA`.
- [ ] A DDE single-frame reply decodes (check the `RX:` log line for its shape).
- [ ] A multi-frame reply decodes (e.g. `22F190` behind `6F1` / `12`).
- [ ] Switching DDE ↔ EGS ↔ a plain OBD-II header in one query keeps every value updating.
- [ ] Which reply shape the vLinker uses is unconfirmed: the ELM327 datasheet was not readable; the stripping covers
      the shapes it might use.

## Out of scope

UI to edit the two keys; a BMW profile and DID list; STN-specific extended addressing in `STPX`.
