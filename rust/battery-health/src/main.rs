use std::env;
use std::io;
use std::path::Path;
use std::process::ExitCode;

use telemetry_core::{read_events, write_jsonl};

fn main() -> ExitCode {
    let Some(input) = env::args().nth(1) else {
        eprintln!("usage: battery-health <events.jsonl>");
        return ExitCode::from(2);
    };
    match read_events(Path::new(&input)) {
        Ok(events) => {
            let rows = battery_health::transform(events);
            if let Err(e) = write_jsonl(io::stdout().lock(), &rows) {
                eprintln!("write failed: {e}");
                return ExitCode::FAILURE;
            }
            ExitCode::SUCCESS
        }
        Err(e) => {
            eprintln!("read failed: {e}");
            ExitCode::FAILURE
        }
    }
}
