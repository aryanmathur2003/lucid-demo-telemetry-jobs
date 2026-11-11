package telemetry

class OutputPathSpec extends munit.FunSuite {

  test("fills in the job name and the environment default") {
    val path = OutputPath.resolve("out/${env:TELEMETRY_ENV_NOT_SET_IN_TESTS:-dev}/{job}.jsonl", "battery_health")
    assertEquals(path, "out/dev/battery_health.jsonl")
  }

  test("resolves the date lookup") {
    val path = OutputPath.resolve("out/${date:yyyy}/{job}.jsonl", "charging_sessions")
    assert(path.matches("""out/\d{4}/charging_sessions\.jsonl"""), path)
  }
}
