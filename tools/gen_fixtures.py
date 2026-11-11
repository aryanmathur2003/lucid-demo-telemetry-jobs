#!/usr/bin/env python3
"""Generate invented telemetry events and the expected job outputs.

Run from the repo root:  python3 tools/gen_fixtures.py

The events are written in arrival order, which is not event-time order, and
some events are delivered twice. All numbers are integers so the Scala and
Rust jobs can be compared byte for byte after JSON parsing.
"""
import json
import random
from pathlib import Path

SEED = 20261112
ROOT = Path(__file__).resolve().parent.parent
VINS = [f"5LDEMAA{n:010d}" for n in range(1, 9)]
T0 = 1794500000000  # milliseconds since the epoch


def build_events(rng):
    events = []
    next_id = 0

    def add(vin, kind, t, **payload):
        nonlocal next_id
        next_id += 1
        events.append({"event_id": f"e-{next_id:06d}", "vin": vin, "type": kind,
                       "event_time_ms": t, **payload})

    session_no = 0
    for vin in VINS:
        t = T0 + rng.randrange(0, 600_000)
        soc = rng.randrange(35, 80)
        for _ in range(rng.randrange(8, 14)):
            t += rng.randrange(30_000, 300_000)
            soc = max(5, soc - rng.randrange(0, 3))
            add(vin, "battery_health", t, soc_pct=soc,
                pack_temp_dc=rng.randrange(180, 420))
        for _ in range(rng.randrange(1, 4)):
            session_no += 1
            sid = f"s-{session_no:04d}"
            t += rng.randrange(600_000, 3_600_000)
            start_soc = rng.randrange(10, 55)
            add(vin, "charging_started", t, session_id=sid, soc_pct=start_soc)
            energy, soc_now = 0, start_soc
            for _ in range(rng.randrange(3, 7)):
                t += rng.randrange(120_000, 600_000)
                energy += rng.randrange(1500, 9000)
                soc_now = min(100, soc_now + rng.randrange(3, 12))
                add(vin, "charging_progress", t, session_id=sid,
                    soc_pct=soc_now, energy_wh=energy)
            if rng.random() < 0.85:  # some sessions never report a stop
                t += rng.randrange(60_000, 300_000)
                energy += rng.randrange(200, 1500)
                soc_now = min(100, soc_now + rng.randrange(0, 3))
                add(vin, "charging_stopped", t, session_id=sid,
                    soc_pct=soc_now, energy_wh=energy)
    return events


def arrival_order(rng, events):
    """Shuffle locally and deliver some events twice."""
    out = list(events)
    rng.shuffle(out)
    out.sort(key=lambda e: e["event_time_ms"] // 900_000 + rng.random())
    dupes = rng.sample(events, k=len(events) // 8)
    for d in dupes:
        out.insert(rng.randrange(0, len(out)), dict(d))
    return out


def dedupe(events):
    seen, out = set(), []
    for e in events:
        if e["event_id"] not in seen:
            seen.add(e["event_id"])
            out.append(e)
    return out


def ordered(events):
    return sorted(events, key=lambda e: (e["event_time_ms"], e["event_id"]))


def battery_health(events):
    by_vin = {}
    for e in ordered(dedupe(events)):
        if e["type"] == "battery_health":
            by_vin.setdefault(e["vin"], []).append(e)
    rows = []
    for vin in sorted(by_vin):
        es = by_vin[vin]
        rows.append({
            "vin": vin,
            "samples": len(es),
            "min_soc_pct": min(e["soc_pct"] for e in es),
            "max_pack_temp_dc": max(e["pack_temp_dc"] for e in es),
            "last_soc_pct": es[-1]["soc_pct"],
            "last_event_time_ms": es[-1]["event_time_ms"],
        })
    return rows


def charging_sessions(events):
    by_session = {}
    for e in ordered(dedupe(events)):
        if e["type"].startswith("charging_"):
            by_session.setdefault(e["session_id"], []).append(e)
    rows = []
    for sid in sorted(by_session):
        es = by_session[sid]
        started = next((e for e in es if e["type"] == "charging_started"), None)
        stopped = next((e for e in es if e["type"] == "charging_stopped"), None)
        if started is None:
            continue  # a session with no start event is dropped
        last = es[-1]
        end_ms = stopped["event_time_ms"] if stopped else last["event_time_ms"]
        rows.append({
            "session_id": sid,
            "vin": started["vin"],
            "started_ms": started["event_time_ms"],
            "ended_ms": end_ms,
            "duration_s": (end_ms - started["event_time_ms"]) // 1000,
            "energy_wh": max((e.get("energy_wh", 0) for e in es), default=0),
            "start_soc_pct": started["soc_pct"],
            "end_soc_pct": last["soc_pct"],
            "complete": stopped is not None,
        })
    return rows


def write_jsonl(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w") as f:
        for r in rows:
            f.write(json.dumps(r, separators=(",", ":")) + "\n")


def main():
    rng = random.Random(SEED)
    events = arrival_order(rng, build_events(rng))
    write_jsonl(ROOT / "fixtures" / "events.jsonl", events)
    write_jsonl(ROOT / "fixtures" / "expected" / "battery_health.jsonl", battery_health(events))
    write_jsonl(ROOT / "fixtures" / "expected" / "charging_sessions.jsonl", charging_sessions(events))
    print(f"{len(events)} events, {len(dedupe(events))} unique")


if __name__ == "__main__":
    main()
