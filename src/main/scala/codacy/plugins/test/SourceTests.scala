package codacy.plugins.test

import java.nio.file.{Path, Paths}

import codacy.utils.{FileHelper, Printer}
import com.codacy.plugins.results.traits.DockerToolDocumentation

import scala.util.Properties

object SourceTests extends ITest {
  override val opt: String = "source"

  override def run(testSources: Seq[Path], dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    Printer.green(s"Running SourceTests:")
    val languages = findLanguages(testSources, dockerImage)
    val dockerTool = createDockerTool(languages, dockerImage)
    val dockerToolDocumentation = new DockerToolDocumentation(dockerTool)

    dockerToolDocumentation.spec match {
      case Some(spec) => spec.patterns.forall { pattern =>
        readFile(Paths.get(dockerToolDocumentation.docsRoot), s"patterns/${pattern.patternId.value}.scala") match {
          case Some(_) =>
            Printer.green(s"found source for pattern ${pattern.patternId.value}")
            true
          case _ =>
            Printer.red(s"did not find source for pattern ${pattern.patternId.value}")
            false
        }
      }
      case _ =>
        Printer.red(s"no valid spec")
        false
    }
  }

  private def readFile(baseDir: Path, filename: String): Option[String] = {
    FileHelper.read(baseDir.resolve(filename).toFile).map(_.mkString(Properties.lineSeparator))
  }
}
