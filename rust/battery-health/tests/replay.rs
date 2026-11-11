//! Replay test: feed the recorded events to the Rust job and compare every
//! output row with the expected output the Scala job is tested against.

use std::fs;
use std::path::PathBuf;

use battery_health::{transform, BatteryHealthSummary};
use telemetry_core::read_events;

fn fixtures() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../fixtures")
}

#[test]
fn matches_recorded_expected_output() {
    let events = read_events(&fixtures().join("events.jsonl")).expect("read events");
    let actual = transform(events);

    let expected: Vec<BatteryHealthSummary> =
        fs::read_to_string(fixtures().join("expected/battery_health.jsonl"))
            .expect("read expected output")
            .lines()
            .filter(|l| !l.trim().is_empty())
            .map(|l| serde_json::from_str(l).expect("parse expected row"))
            .collect();

    assert_eq!(actual.len(), expected.len(), "row count differs");
    for (a, e) in actual.iter().zip(&expected) {
        assert_eq!(a, e, "row differs for {}", e.vin);
    }
}

#[test]
fn replaying_events_twice_changes_nothing() {
    let events = read_events(&fixtures().join("events.jsonl")).expect("read events");
    let mut doubled = events.clone();
    doubled.extend(events.clone());
    assert_eq!(transform(events), transform(doubled));
}

#[test]
fn arrival_order_does_not_matter() {
    let events = read_events(&fixtures().join("events.jsonl")).expect("read events");
    let mut reversed = events.clone();
    reversed.reverse();
    assert_eq!(transform(events), transform(reversed));
}
