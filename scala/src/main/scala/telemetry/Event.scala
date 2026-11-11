package telemetry

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/** One vehicle-to-cloud telemetry event as it arrives from the ingest topic. */
final case class Event(
    eventId: String,
    vin: String,
    kind: String,
    eventTimeMs: Long,
    sessionId: Option[String],
    socPct: Option[Long],
    packTempDc: Option[Long],
    energyWh: Option[Long]
)

object Event {

  def parse(line: String): Event = {
    val fields = ujson.read(line).obj
    def long(key: String): Option[Long] = fields.get(key).map(_.num.toLong)
    Event(
      eventId = fields("event_id").str,
      vin = fields("vin").str,
      kind = fields("type").str,
      eventTimeMs = fields("event_time_ms").num.toLong,
      sessionId = fields.get("session_id").map(_.str),
      socPct = long("soc_pct"),
      packTempDc = long("pack_temp_dc"),
      energyWh = long("energy_wh")
    )
  }

  /** Reads newline-delimited JSON events in arrival order. */
  def readAll(path: Path): Vector[Event] =
    Files.readAllLines(path).asScala.iterator.filter(_.trim.nonEmpty).map(parse).toVector
}
