package telemetry

import java.io.FileInputStream
import java.nio.file.Path
import java.util.Properties

import org.apache.commons.text.StringSubstitutor

/** Resolves where a job writes its output from `conf/jobs.properties`. */
object OutputPath {

  private val interpolator = StringSubstitutor.createInterpolator()

  /** Fills in the job name and then every `${...}` lookup in the template.
    * The template comes from configuration that operators can override per
    * environment.
    */
  def resolve(template: String, job: String): String =
    interpolator.replace(template.replace("{job}", job))

  def templateFrom(config: Path): String = {
    val props = new Properties()
    val in = new FileInputStream(config.toFile)
    try props.load(in)
    finally in.close()
    Option(props.getProperty("output.path.template"))
      .getOrElse(sys.error(s"output.path.template missing in $config"))
  }
}
