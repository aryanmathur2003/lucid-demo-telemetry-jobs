# telemetry-jobs

Demo repo for a telemetry pipeline that is moving from Scala to Rust. It
mirrors the starting point of a real migration: the Rust skeleton exists and
one small job has been ported.

Everything here is invented for a demo. The VINs and events are not real and
this is not Lucid code.

## Layout

| Path | What it is |
| --- | --- |
| `scala/` | The existing jobs. `BatteryHealthJob` and `ChargingSessionJob`. |
| `rust/telemetry-core` | Shared event model, duplicate filter and ordering rule. |
| `rust/battery-health` | The one job already ported, with its replay test. This is the pattern to follow. |
| `fixtures/events.jsonl` | 205 recorded events in arrival order, with duplicates and late events. |
| `fixtures/expected/` | The expected output of each job for those events. |
| `tools/gen_fixtures.py` | Regenerates the events and expected outputs. |

`ChargingSessionJob` has not been ported yet.

## Guarantees every job must keep

- Duplicates are dropped by `event_id` before anything else.
- Events are processed in event-time order and not arrival order.
- Replaying the same events again must not change the output.

The replay tests check all three.

## Test

```
cd scala && sbt test
cd rust && cargo test --workspace
```

In production the Scala transforms run inside Spark Streaming with a Kafka
source and sink. That wiring is left out so the job logic runs on one
machine.

## Open finding

`security/FINDING-003.md`.

## Conventions

- Never push to `main`. Open a pull request.
- A pull request description lists what changed, the test results and the
  re-scan result.
- Development and tests use only the invented data in `fixtures/`.
