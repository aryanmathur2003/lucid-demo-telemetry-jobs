//! Shared pieces for the Rust stream processors: the event model, the
//! duplicate filter and the ordering rule every job must follow.

use std::collections::HashSet;
use std::fs::File;
use std::io::{self, BufRead, BufReader, Write};
use std::path::Path;

use serde::{Deserialize, Serialize};

/// One vehicle-to-cloud telemetry event as it arrives from the ingest topic.
#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
pub struct Event {
    pub event_id: String,
    pub vin: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub event_time_ms: i64,
    #[serde(default)]
    pub session_id: Option<String>,
    #[serde(default)]
    pub soc_pct: Option<i64>,
    #[serde(default)]
    pub pack_temp_dc: Option<i64>,
    #[serde(default)]
    pub energy_wh: Option<i64>,
}

/// Reads newline-delimited JSON events in arrival order.
pub fn read_events(path: &Path) -> io::Result<Vec<Event>> {
    let reader = BufReader::new(File::open(path)?);
    let mut events = Vec::new();
    for line in reader.lines() {
        let line = line?;
        if line.trim().is_empty() {
            continue;
        }
        let event: Event = serde_json::from_str(&line)
            .map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))?;
        events.push(event);
    }
    Ok(events)
}

/// Keeps the first delivery of each `event_id`. The ingest topic delivers at
/// least once, so every job must drop repeats before it aggregates.
pub fn dedupe(events: Vec<Event>) -> Vec<Event> {
    let mut seen = HashSet::with_capacity(events.len());
    events
        .into_iter()
        .filter(|e| seen.insert(e.event_id.clone()))
        .collect()
}

/// Sorts into processing order: event time first, then `event_id` so that
/// events with the same timestamp are always handled in the same order.
pub fn order(mut events: Vec<Event>) -> Vec<Event> {
    events.sort_by(|a, b| {
        a.event_time_ms
            .cmp(&b.event_time_ms)
            .then_with(|| a.event_id.cmp(&b.event_id))
    });
    events
}

/// Writes rows as newline-delimited JSON.
pub fn write_jsonl<T: Serialize, W: Write>(mut out: W, rows: &[T]) -> io::Result<()> {
    for row in rows {
        serde_json::to_writer(&mut out, row)?;
        out.write_all(b"\n")?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn event(id: &str, t: i64) -> Event {
        Event {
            event_id: id.to_string(),
            vin: "5LDEMAA0000000001".to_string(),
            kind: "battery_health".to_string(),
            event_time_ms: t,
            session_id: None,
            soc_pct: Some(50),
            pack_temp_dc: Some(250),
            energy_wh: None,
        }
    }

    #[test]
    fn dedupe_keeps_first_delivery() {
        let out = dedupe(vec![event("a", 1), event("b", 2), event("a", 1)]);
        assert_eq!(out.len(), 2);
        assert_eq!(out[0].event_id, "a");
    }

    #[test]
    fn order_breaks_ties_by_event_id() {
        let out = order(vec![event("b", 5), event("a", 5), event("c", 1)]);
        let ids: Vec<_> = out.iter().map(|e| e.event_id.as_str()).collect();
        assert_eq!(ids, ["c", "a", "b"]);
    }
}
