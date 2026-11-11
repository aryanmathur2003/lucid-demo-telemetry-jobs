package telemetry

import telemetry.Pipeline.num

/** Battery health per vehicle. Already ported to Rust in
  * `rust/battery-health`.
  */
object BatteryHealthJob {

  val name = "battery_health"

  def transform(events: Seq[Event]): Vector[ujson.Value] = {
    val samples = Pipeline
      .ordered(Pipeline.dedupe(events))
      .filter(e => e.kind == "battery_health" && e.socPct.isDefined && e.packTempDc.isDefined)

    samples
      .groupBy(_.vin)
      .toVector
      .sortBy(_._1)
      .map { case (vin, es) =>
        ujson.Obj(
          "vin" -> vin,
          "samples" -> num(es.size.toLong),
          "min_soc_pct" -> num(es.map(_.socPct.get).min),
          "max_pack_temp_dc" -> num(es.map(_.packTempDc.get).max),
          "last_soc_pct" -> num(es.last.socPct.get),
          "last_event_time_ms" -> num(es.last.eventTimeMs)
        ): ujson.Value
      }
  }
}
