package codacy.plugins.test

import java.io.File
import java.nio.file.Path

import codacy.utils.Printer
import com.codacy.analysis.core.tools.MetricsTool
import com.codacy.plugins.api.Source
import com.codacy.plugins.metrics.traits
import com.codacy.plugins.api.metrics.{FileMetrics, LineComplexity}

import scala.util.{Failure, Success}

import Utils._

object MetricsTests extends ITest with CustomMatchers {

  val opt = "metrics"

  def run(testSources: Seq[Path], dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    Printer.green(s"Running MetricsTests:")

    val languages = findLanguages(testSources, dockerImage)
    val metricsTool = new traits.MetricsTool(languages.toList, dockerImage.name, dockerImage.version) {}
    val tools = languages.map(language => new MetricsTool(metricsTool, language))

    testSources
      .map { sourcePath =>
        val testFiles = new MetricsTestFilesParser(sourcePath.toFile).getTestFiles

        testFiles
          .map {
            case (fileMetrics, language) =>
              tools
                .filter(_.languageToRun.name.equalsIgnoreCase(language.toString))
                .exists(analyseFile(sourcePath.toFile, fileMetrics, _))
          }
          .forall(identity)
      }
      .forall(identity)
  }

  private def metricsMessage(testFile: FileMetrics) = {
    val fileResults = Seq("complexity" -> testFile.complexity,
                          "loc" -> testFile.loc,
                          "cloc" -> testFile.cloc,
                          "nrMethods" -> testFile.nrMethods,
                          "nrClasses" -> testFile.nrClasses).collect {
      case (key, Some(value)) =>
        s"$key == $value"
    }
    val lineComplexitiesResult = testFile.lineComplexities.map {
      case LineComplexity(line, value) => s"complexity $value in line $line"
    }
    val res = (fileResults ++ lineComplexitiesResult)
    val start = res.dropRight(1).mkString(", ")
    res.lastOption match {
      case Some(last) =>
        s"$start and $last"
      case None => start
    }
  }

  private def analyseFile(rootDirectory: File, testFile: FileMetrics, tool: MetricsTool): Boolean = {
    val filename = toRelativePath(rootDirectory.getAbsolutePath, testFile.filename)
    Printer.green(s"  - $filename should have: ${metricsMessage(testFile)}")
    val testFiles: Set[Source.File] = Set(Source.File(filename))
    val resultTry = tool
      .run(better.files.File(rootDirectory.getAbsolutePath), Option(testFiles))
      .map(_.map(toCodacyPluginsApiMetricsFileMetrics))
    val result = resultTry match {
      case Failure(e) =>
        Printer.red((e.getMessage :: e.getStackTrace.toList).mkString("\n"))
        Set.empty
      case Success(res) =>
        res
    }
    val comparison = result == Iterable(testFile)
    if (!comparison) {
      val errorMessage = result.headOption.map(metricsMessage) match {
        case Some(message) =>
          s"  $message did not match expected result."
        case None =>
          "  No result received."
      }
      Printer.red(errorMessage)
    } else Printer.green("  Test passed")

    comparison
  }

  private def toRelativePath(rootPath: String, absolutePath: String) = {
    absolutePath.stripPrefix(rootPath).stripPrefix(File.separator)
  }
}
