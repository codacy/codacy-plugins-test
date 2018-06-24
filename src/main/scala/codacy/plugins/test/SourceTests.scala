package codacy.plugins.test

import java.nio.file.Path

import codacy.plugins.docker.DockerPlugin
import codacy.utils.{FileHelper, Printer}

import scala.util.Properties

object SourceTests extends ITest {
  override val opt: String = "source"

  override def run(plugin: DockerPlugin,
                   testSources: Seq[Path],
                   dockerImageName: String,
                   optArgs: Seq[String]): Boolean = {
    Printer.green("Running SourceTests:")
    DockerHelpers.withDocsDirectory(dockerImageName) { baseDocDir =>
      plugin.spec match {
        case Some(spec) =>
          val patterns = DockerHelpers.toPatterns(spec)
          patterns.forall { pattern =>
            readFile(baseDocDir, s"patterns/${pattern.patternIdentifier}.scala") match {
              case Some(_) =>
                Printer.green(s"found source for pattern ${pattern.patternIdentifier}")
                true
              case _ =>
                Printer.red(s"did not find source for pattern ${pattern.patternIdentifier}")
                false
            }
          }
        case _ =>
          Printer.red("no valid spec")
          false
      }
    }
  }

  private def readFile(baseDir: Path, filename: String): Option[String] = {
    FileHelper.read(baseDir.resolve(filename).toFile).map(_.mkString(Properties.lineSeparator))
  }
}
