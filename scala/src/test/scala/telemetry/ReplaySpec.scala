package telemetry

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

/** Replays the recorded events through each job and compares every output
  * row with the expected output in `fixtures/expected`.
  */
class ReplaySpec extends munit.FunSuite {

  private val fixtures: Path = Paths.get("..", "fixtures")
  private lazy val events: Vector[Event] = Event.readAll(fixtures.resolve("events.jsonl"))

  private def expected(file: String): Vector[ujson.Value] =
    Files
      .readAllLines(fixtures.resolve("expected").resolve(file))
      .asScala
      .iterator
      .filter(_.trim.nonEmpty)
      .map(line => ujson.read(line))
      .toVector

  private def check(actual: Vector[ujson.Value], file: String): Unit = {
    val want = expected(file)
    assertEquals(actual.size, want.size, "row count differs")
    actual.zip(want).foreach { case (a, w) => assertEquals(a, w) }
  }

  test("battery health matches the expected output") {
    check(BatteryHealthJob.transform(events), "battery_health.jsonl")
  }

  test("charging sessions match the expected output") {
    check(ChargingSessionJob.transform(events), "charging_sessions.jsonl")
  }

  test("replaying every event twice changes nothing") {
    assertEquals(ChargingSessionJob.transform(events ++ events), ChargingSessionJob.transform(events))
    assertEquals(BatteryHealthJob.transform(events ++ events), BatteryHealthJob.transform(events))
  }

  test("arrival order does not matter") {
    assertEquals(ChargingSessionJob.transform(events.reverse), ChargingSessionJob.transform(events))
  }
}
