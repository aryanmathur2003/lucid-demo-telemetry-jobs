//! Battery health job, ported from `scala/.../BatteryHealthJob.scala`.
//!
//! This is the reference port. New ports should follow the same shape: a pure
//! `transform` over events, and a replay test in `tests/` that compares the
//! output with the recorded expected output in `fixtures/expected`.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use telemetry_core::{dedupe, order, Event};

/// One output row per vehicle.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct BatteryHealthSummary {
    pub vin: String,
    pub samples: i64,
    pub min_soc_pct: i64,
    pub max_pack_temp_dc: i64,
    pub last_soc_pct: i64,
    pub last_event_time_ms: i64,
}

/// Aggregates battery health events per vehicle. Rows are sorted by VIN.
pub fn transform(events: Vec<Event>) -> Vec<BatteryHealthSummary> {
    let mut by_vin: BTreeMap<String, BatteryHealthSummary> = BTreeMap::new();
    for e in order(dedupe(events)) {
        if e.kind != "battery_health" {
            continue;
        }
        let (Some(soc), Some(temp)) = (e.soc_pct, e.pack_temp_dc) else {
            continue;
        };
        by_vin
            .entry(e.vin.clone())
            .and_modify(|s| {
                s.samples += 1;
                s.min_soc_pct = s.min_soc_pct.min(soc);
                s.max_pack_temp_dc = s.max_pack_temp_dc.max(temp);
                s.last_soc_pct = soc;
                s.last_event_time_ms = e.event_time_ms;
            })
            .or_insert(BatteryHealthSummary {
                vin: e.vin.clone(),
                samples: 1,
                min_soc_pct: soc,
                max_pack_temp_dc: temp,
                last_soc_pct: soc,
                last_event_time_ms: e.event_time_ms,
            });
    }
    by_vin.into_values().collect()
}
