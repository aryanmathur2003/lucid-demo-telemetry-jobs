package telemetry

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

/** Runs one job over a file of events.
  *
  * In production these transforms run inside Spark Streaming with a Kafka
  * source and sink. That wiring is left out of this sample so the job logic
  * runs on one machine.
  *
  * Usage: sbt "run battery_health ../fixtures/events.jsonl"
  */
object JobRunner {

  private val jobs: Map[String, Seq[Event] => Vector[ujson.Value]] = Map(
    BatteryHealthJob.name -> BatteryHealthJob.transform,
    ChargingSessionJob.name -> ChargingSessionJob.transform
  )

  def main(args: Array[String]): Unit = {
    if (args.length != 2 || !jobs.contains(args(0))) {
      Console.err.println(s"usage: run <${jobs.keys.toSeq.sorted.mkString("|")}> <events.jsonl>")
      sys.exit(2)
    }
    val job = args(0)
    val rows = jobs(job)(Event.readAll(Paths.get(args(1))))
    val template = OutputPath.templateFrom(Paths.get("conf", "jobs.properties"))
    val out = Paths.get(OutputPath.resolve(template, job))
    Option(out.getParent).foreach(Files.createDirectories(_))
    val body = rows.map(ujson.write(_)).mkString("", "\n", "\n")
    Files.write(out, body.getBytes(StandardCharsets.UTF_8))
    println(s"$job: wrote ${rows.size} rows to $out")
  }
}
