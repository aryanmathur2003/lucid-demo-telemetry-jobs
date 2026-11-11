package telemetry

import scala.collection.mutable

/** Rules every job applies before it aggregates. */
object Pipeline {

  /** Keeps the first delivery of each event id. The ingest topic delivers at
    * least once, so repeats must be dropped.
    */
  def dedupe(events: Seq[Event]): Vector[Event] = {
    val seen = mutable.HashSet.empty[String]
    events.iterator.filter(e => seen.add(e.eventId)).toVector
  }

  /** Processing order: event time first, then event id so that events with
    * the same timestamp are always handled in the same order.
    */
  def ordered(events: Seq[Event]): Vector[Event] =
    events.sortBy(e => (e.eventTimeMs, e.eventId)).toVector

  def num(value: Long): ujson.Value = ujson.Num(value.toDouble)
}
