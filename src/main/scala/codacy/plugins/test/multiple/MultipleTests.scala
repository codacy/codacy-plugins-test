package codacy.plugins.test.multiple

import codacy.plugins.test._

import com.codacy.analysis.core.model.{CodacyCfg, Configuration, FileCfg, FileError, FullLocation, Issue, LineLocation}
import com.codacy.analysis.core.tools.Tool
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.BinaryDockerHelper
import com.codacy.plugins.results.PluginResult

import better.files._
import java.io.{ File => JFile }

import scala.util.{Failure, Success, Try}
import scala.xml.XML
import com.codacy.analysis.core.model.ToolResult
import com.codacy.analysis.core.model.Location
import scala.util.Properties

object MultipleTests extends ITest {

  val opt = "multiple"

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    debug(s"Running MultipleTests:")
    val multipleTestsDirectory = docsDirectory.toScala / DockerHelpers.multipleTestsDirectoryName
    val languages = findLanguages(multipleTestsDirectory.toJava, dockerImage)
    val dockerTool = createDockerTool(languages, dockerImage)
    val toolDocumentation = new DockerToolDocumentation(dockerTool, new BinaryDockerHelper(useCachedDocs = false))
    val dockerRunner = new BinaryDockerRunner[Result](dockerTool)()
    val runner = new ToolRunner(dockerTool, toolDocumentation, dockerRunner)
    val tools = languages.map(new Tool(runner, DockerRunner.defaultRunTimeout)(dockerTool, _))
    multipleTestsDirectory.list.forall { testDirectory =>
      val srcDir = testDirectory / "src"
      val resultFile = testDirectory / "results.xml"
      val resultFileXML = XML.loadFile(resultFile.toJava)
      val expectedResults = CheckstyleFormatParser.parseResultsXml(resultFileXML).toSet
      debug(s"${testDirectory.name} should have ${expectedResults.size} results")
      val configuration = createConfiguration(testDirectory, srcDir)
      tools.exists { tool =>
        val res = runTool(tool, srcDir, configuration)
        ResultPrinter.printToolResults(res, expectedResults)
      }
    }
  }

  private def createConfiguration(testDirectory: File, srcDir: File) = {
    val patternsPath = testDirectory / "patterns.xml"
    if (patternsPath.exists) {
      val patternsFileXML = XML.loadFile(patternsPath.toJava)
      val (patterns, extraValues) = CheckstyleFormatParser.parsePatternsXml(patternsFileXML)
      if (patterns.isEmpty) FileCfg(Some(srcDir.pathAsString), extraValues)
      else CodacyCfg(patterns, Some(srcDir.pathAsString), extraValues)
    } else FileCfg(Some(srcDir.pathAsString), None)
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
                      configuration: Configuration): Try[Set[PluginResult]] = {
    val toolRunResult: Try[Set[ToolResult]] = tool.run(
      multipleTestsDirectory,
      multipleTestsDirectory.list.filter(_.isRegularFile).map(_.path).toSet,
      configuration
    )

    toolRunResult.flatMap { resultSet =>
      val resultEithers = convertResults(resultSet)
      val failures = resultEithers.collect { case Left(error) => error }
      if (failures.isEmpty) {
        Success(resultEithers.collect { case Right(value) => value })
      } else {
        val errorsString =
          failures.mkString(Properties.lineSeparator)
        Failure(new Exception(s"Got errors in ${failures.size} files:${Properties.lineSeparator}$errorsString"))
      }
    }
  }
}
