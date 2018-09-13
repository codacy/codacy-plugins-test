package codacy.plugins.test

import java.nio.file.Path

import codacy.utils.{FileHelper, Printer}
import com.codacy.plugins.api.results

import scala.util.Properties

object SourceTests extends ITest {
  override val opt: String = "source"


  override def run(specOpt: Option[results.Tool.Specification], testSources: Seq[Path],
                   dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    Printer.green(s"Running SourceTests:")
    DockerHelpers.withDocsDirectory(dockerImage.toString) { baseDocDir =>
      specOpt match {
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
          Printer.red(s"no valid spec")
          false
      }
    }
  }

  private def readFile(baseDir: Path, filename: String): Option[String] = {
    FileHelper.read(baseDir.resolve(filename).toFile).map(_.mkString(Properties.lineSeparator))
  }
}
