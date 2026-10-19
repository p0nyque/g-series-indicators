import json, sys
# Generates datalogger/src/main/resources/bmw_b47_mhev.json from the candidates in doc/specs/classic-dashboard-tech.md §4.3.
# Run from the repo root: python3 scripts/gen_bmw_pids.py datalogger/src/main/resources/bmw_b47_mhev.json
U16 = "(A*256+B)"
S16 = lambda hi, lo: f"(({hi}*256+{lo}) << 16 >> 16)"
U32 = "(A*16777216+B*65536+C*256+D)"
def pid(id, did, length, desc, mn, mx, units, prio, formula, can=None, int_type=False):
    p = {"id": str(id), "mode": "22", "pid": did, "length": length, "description": desc,
         "min": mn, "max": mx, "units": units, "priority": prio, "formula": formula}
    if int_type: p["type"] = "INT"
    o = {}
    if can: o["canMode"] = can
    o["batchEnabled"] = False
    p["overrides"] = o
    return p
r3 = lambda e: f"parseFloat(({e}).toFixed(3))"
r1 = lambda e: f"parseFloat(({e}).toFixed(1))"
main = [
  pid(40001, "4517", 2, "Engine Oil\nTemp", 50, 150, "C", 1, r1(f"{U16}*0.01-100")),
  pid(40002, "4841", 2, "Boost\nActual", 1.0, 3.0, "bar", 0, r3(f"{U16}*0.15259022/1000")),
  pid(40003, "42C8", 2, "Boost\nTarget", 1.0, 3.0, "bar", 0, r3(f"{U16}*0.09155413/1000")),
  pid(40004, "434A", 2, "Exhaust Temp\nbefore DPF", 0, 900, "C", 1, r1(f"{U16}*0.1-3276.7")),
  pid(40005, "4521", 1, "Engine Oil\nLevel", 0, 75, "mm", 2, r1("A*0.29296875")),
  pid(40006, "D08C", 28, "48V Battery\nPower", -10, 10, "kW", 0,
      r3(f"{S16('A','B')}/100*{S16('C','D')}/10/1000"), can="BAT48"),
  pid(40007, "44BB", 1, "DPF Regeneration\nRequest", 0, 1, "", 2, "A", int_type=True),
  pid(40008, "44BF", 4, "Distance since\nDPF Regeneration", 0, 1000, "km", 2, r1(f"{U32}/1000")),
  pid(40020, "DA12", 2, "Gearbox Oil\nTemp", 40, 140, "C", 1, f"{S16('A','B')}-48", can="EGS", int_type=True),
  pid(40030, "D08C", 28, "48V Battery\nState of Charge", 0, 100, "%", 1, "O", can="BAT48", int_type=True),
]
probe = lambda id, did, l, d, mn, mx, u, f, can=None, it=False: pid(id, did, l, "Probe: " + d, mn, mx, u, 2, f, can, it)
probes = [
  probe(40101, "4B60", 2, "Oil Temp\n(Oil_tSwmp)", 50, 150, "C", r1(f"{U16}*0.01-100")),
  probe(40102, "428E", 2, "Oil Temp\n(Toel)", 50, 150, "C", r1(f"{U16}*0.00541695-100")),
  probe(40103, "47F0", 2, "Boost Actual\n(Air_pSensPIntkVUs)", 1.0, 3.0, "bar", r3(f"({U16}-32767)/1000")),
  probe(40104, "42C5", 2, "Boost Target\n(PCR_pDesValMdl)", 1.0, 3.0, "bar", r3(f"({U16}-32767)/1000")),
  probe(40105, "4502", 2, "Exhaust Temp\n(Exh_tSensTPFltUs)", 0, 900, "C", r1(f"{U16}*0.03128099-50")),
  probe(40106, "4523", 1, "Oil Level\n(short-term)", 0, 75, "mm", r1("A*0.29296875")),
  probe(40107, "422C", 1, "Oil Level\n(relative)", 0, 255, "", "A", it=True),
  probe(40108, "4B9E", 2, "Starter-Generator\nTorque", -100, 100, "Nm", r1(f"{U16}*0.5")),
  probe(40109, "4BA3", 2, "Starter-Generator\nCurrent", -300, 300, "A", r1(f"{U16}*0.2")),
  probe(40110, "4B9F", 2, "Starter-Generator\nVoltage", 0, 60, "V", r1(f"{U16}*0.1")),
  probe(40111, "467E", 4, "Operating Mode\n(CoEOM_stOpModeAct)", 0, 4294967295, "", U32, it=True),
  probe(40112, "6000", 1, "Gearbox Oil Temp\n(EGS 6000)", 40, 140, "C", "A-40", can="EGS", it=True),
  probe(40113, "4F7B", 1, "Gearbox Oil Temp\n(DDE Tra_tOil)", 40, 140, "C", "A-40", it=True),
]
json.dump({"metadata": [], "livedata": main + probes}, open(sys.argv[1], "w"), indent=2, ensure_ascii=False)
open(sys.argv[1], "a").write("\n")
