package codacy.plugins.test

import com.codacy.analysis.core.tools.MetricsTool
import com.codacy.plugins.api.Source
import com.codacy.plugins.metrics.traits
import com.codacy.plugins.api.metrics.{FileMetrics, LineComplexity}

import scala.util.{Failure, Success}

import Utils._
import better.files._
import java.io.{ File => JFile }
import java.nio.file.Paths

object MetricsTests extends ITest with CustomMatchers {

  val opt = "metrics"

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    val testsDirectory = docsDirectory.toScala / DockerHelpers.testsDirectoryName
    debug(s"Running MetricsTests:")

    val languages = findLanguages(testsDirectory.toJava, dockerImage)
    val metricsTool = new traits.MetricsTool(languages.toList, dockerImage.name, dockerImage.version) {}
    val tools = languages.map(language => new MetricsTool(metricsTool, language))

    val testFiles = new MetricsTestFilesParser(testsDirectory.toJava).getTestFiles

    testFiles
      .map {
        case (fileMetrics, language) =>
          tools
            .filter(_.languageToRun.name.equalsIgnoreCase(language.toString))
            .exists(analyseFile(testsDirectory, fileMetrics, _))
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
    val filename = rootDirectory.relativize(Paths.get(testFile.filename)).toString
    debug(s"  - $filename should have: ${metricsMessage(testFile)}")
    val testFiles: Set[Source.File] = Set(Source.File(filename))
    val resultTry = tool
      .run(rootDirectory, Option(testFiles))
      .map(_.map(toCodacyPluginsApiMetricsFileMetrics))
    val result = resultTry match {
      case Failure(e) =>
        error((e.getMessage :: e.getStackTrace.toList).mkString("\n"))
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
      error(errorMessage)
    } else debug("  Test passed")

    comparison
  }
}
