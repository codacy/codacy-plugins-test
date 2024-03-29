package codacy.plugins.test

import better.files._
import codacy.plugins.test.Utils.exceptionToString
import com.codacy.analysis.core.model.MetricsToolSpec
import com.codacy.analysis.core.tools.MetricsTool
import com.codacy.plugins.api.Source
import com.codacy.plugins.api.metrics.{FileMetrics, LineComplexity}

import java.io.{File => JFile}
import scala.util.{Failure, Success, Try}

import Utils._

object MetricsTests extends ITest with CustomMatchers {

  val opt = "metrics"

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    val testsDirectory = docsDirectory.toScala / DockerHelpers.testsDirectoryName
    debug(s"Running MetricsTests:")

    val languages = findLanguages(testsDirectory.toJava)
    val metricsTool = MetricsToolSpec(dockerImage.toString(), languages)
    val tools = languages.map(language => new MetricsTool(metricsTool, language))

    val testFiles = new MetricsTestFilesParser(testsDirectory.toJava).getTestFiles

    testFiles
      .map {
        case (fileMetrics, language) =>
          tools
            .filter(_.languageToRun.name.equalsIgnoreCase(language.toString))
            .exists { tool =>
              val result = analyseFile(testsDirectory, fileMetrics, tool)
              result match {
                case Success(res) => res
                case Failure(e) =>
                  error(exceptionToString(e))
                  false
              }
            }
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

  private def analyseFile(rootDirectory: File, testFile: FileMetrics, tool: MetricsTool): Try[Boolean] = {
    val filename = testFile.filename
    debug(s"  - $filename should have: ${metricsMessage(testFile)}")
    val testFiles: Set[Source.File] = Set(Source.File(filename))
    val resultTry = tool
      .run(rootDirectory, testFiles)
      .map(_.map(toCodacyPluginsApiMetricsFileMetrics))
    resultTry.map { result =>
      val comparison = result == Iterable(testFile)
      if (!comparison) {
        val errorMessage = result.headOption.map(metricsMessage) match {
          case Some(message) =>
            s"  $message did not match expected result."
          case None =>
            "  No result received."
        }
        error(errorMessage)
      } else debug("  Test passed")

      comparison
    }
  }
}
