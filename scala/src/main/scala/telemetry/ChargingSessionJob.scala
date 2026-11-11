package telemetry

import telemetry.Pipeline.num

/** One summary row per charging session. Not yet ported to Rust.
  *
  * Rules that downstream service, warranty and billing consumers rely on:
  *   - duplicates are dropped by event id before anything else
  *   - events are processed in event-time order, not arrival order
  *   - a session with no start event is dropped
  *   - a session with no stop event is still emitted, marked incomplete, and
  *     ends at its last progress event
  *   - energy is the highest meter reading seen, because the meter only
  *     counts up within a session
  */
object ChargingSessionJob {

  val name = "charging_sessions"

  def transform(events: Seq[Event]): Vector[ujson.Value] = {
    val charging = Pipeline
      .ordered(Pipeline.dedupe(events))
      .filter(e => e.kind.startsWith("charging_") && e.sessionId.isDefined)

    charging
      .groupBy(_.sessionId.get)
      .toVector
      .sortBy(_._1)
      .flatMap { case (sessionId, es) =>
        es.find(_.kind == "charging_started").map { started =>
          val stopped = es.find(_.kind == "charging_stopped")
          val last = es.last
          val endedMs = stopped.map(_.eventTimeMs).getOrElse(last.eventTimeMs)
          ujson.Obj(
            "session_id" -> sessionId,
            "vin" -> started.vin,
            "started_ms" -> num(started.eventTimeMs),
            "ended_ms" -> num(endedMs),
            "duration_s" -> num((endedMs - started.eventTimeMs) / 1000),
            "energy_wh" -> num(es.flatMap(_.energyWh).maxOption.getOrElse(0L)),
            "start_soc_pct" -> num(started.socPct.getOrElse(0L)),
            "end_soc_pct" -> num(last.socPct.getOrElse(0L)),
            "complete" -> stopped.isDefined
          ): ujson.Value
        }
      }
  }
}
