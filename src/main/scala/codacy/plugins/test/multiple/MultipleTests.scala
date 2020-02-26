package codacy.plugins.test.multiple

import java.io.{File => JFile}

import better.files._
import codacy.plugins.test._
import codacy.plugins.test.resultprinter.ResultPrinter
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.tools.Tool
import com.codacy.plugins.api.languages.Languages
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.PluginResult
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.BinaryDockerHelper

import scala.util.{Failure, Properties, Success, Try}
import scala.xml.XML

object MultipleTests extends ITest {

  val opt = "multiple"

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    debug(s"Running MultipleTests:")
    val multipleTestsDirectory = docsDirectory.toScala / DockerHelpers.multipleTestsDirectoryName
    ParallelCollectionUtil
      .toPar(multipleTestsDirectory.list.toList)
      .map { testDirectory =>
        val srcDir = testDirectory / "src"
        // on multiple tests, the language is not validated but required. We used Scala.
        val dockerTool = createDockerTool(Set(Languages.Scala), dockerImage)
        val toolDocumentation = new DockerToolDocumentation(dockerTool, new BinaryDockerHelper(useCachedDocs = false))
        val dockerRunner = new BinaryDockerRunner[Result](dockerTool)()
        val runner = new ToolRunner(dockerTool, toolDocumentation, dockerRunner)
        val tools = dockerTool.languages.map(new Tool(runner, DockerRunner.defaultRunTimeout)(dockerTool, _))
        val resultFile = testDirectory / "results.xml"
        val resultFileXML = XML.loadFile(resultFile.toJava)
        val expectedResults = CheckstyleFormatParser.parseResultsXml(resultFileXML).toSet
        val (configuration, excludedFilesRegex) = createConfiguration(testDirectory, srcDir)
        val results = tools.map(runTool(_, srcDir, configuration, excludedFilesRegex))
        (testDirectory.name, results, expectedResults)
      }
      .seq
      .map {
        case (directoryName, results, expectedResults) =>
          debug(s"${directoryName} should have ${expectedResults.size} results")
          results.exists(ResultPrinter.printToolResults(_, expectedResults))
      }
      .forall(identity)
  }

  private def createConfiguration(testDirectory: File, srcDir: File): (Configuration, Option[String]) = {
    val patternsPath = testDirectory / "patterns.xml"
    if (patternsPath.exists) {
      val patternsFileXML = XML.loadFile(patternsPath.toJava)
      val (patterns, extraValues, excludedFilesRegex) = CheckstyleFormatParser.parsePatternsXml(patternsFileXML)
      val configuration =
        if (patterns.isEmpty) FileCfg(Some(srcDir.pathAsString), extraValues)
        else CodacyCfg(patterns, Some(srcDir.pathAsString), extraValues)
      (configuration, excludedFilesRegex)
    } else (FileCfg(Some(srcDir.pathAsString), None), None)
  }

  private def convertResults(resultSet: Set[ToolResult]): Set[Either[String, PluginResult]] = resultSet.map {
    case Issue(patternId, filename, message, level, category, location: Location) =>
      val line = location match {
        case FullLocation(line, column) => line
        case LineLocation(line) => line
      }
      Right(PluginResult(patternId.value, filename.getFileName().toString(), line, message.text, level))
    case FileError(file, error) => Left(s"  error $error in file $file")
  }

  private def runTool(tool: Tool,
                      multipleTestsDirectory: File,
                      configuration: Configuration,
                      excludedFilesRegex: Option[String]): Try[Set[PluginResult]] = {
    val optRegex = excludedFilesRegex.map(_.r)

    def toExclude(file: File) = optRegex.exists(_.findFirstIn(file.name).nonEmpty)

    val filesToTest = for {
      file <- multipleTestsDirectory.listRecursively
      if file.isRegularFile && !toExclude(file)
    } yield file.path
    val toolRunResult: Try[Set[ToolResult]] = tool.run(multipleTestsDirectory, filesToTest.toSet, configuration)

    toolRunResult.flatMap { resultSet =>
      val resultEithers = convertResults(resultSet)
      val failures = resultEithers.collect { case Left(error) => error }
      if (failures.isEmpty)
        Success(resultEithers.collect { case Right(value) => value })
      else {
        val errorsString =
          failures.mkString(Properties.lineSeparator)
        Failure(new Exception(s"Got errors in ${failures.size} files:${Properties.lineSeparator}$errorsString"))
      }
    }
  }
}
