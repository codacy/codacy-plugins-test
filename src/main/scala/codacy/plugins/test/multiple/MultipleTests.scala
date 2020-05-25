package codacy.plugins.test.multiple

import java.io.{File => JFile}

import scala.util.Try
import scala.xml.XML

import better.files._
import codacy.plugins.test._
import codacy.plugins.test.resultprinter.ResultPrinter
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.tools.Tool
import com.codacy.plugins.api.languages.Languages
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.BinaryDockerHelper

object MultipleTests extends ITest {

  val opt = "multiple"

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    debug(s"Running MultipleTests:")

    val selectedTest = optArgs.sliding(2).collectFirst {
      case Seq("--only", multipleTestDir) =>
        multipleTestDir
    }
    val multipleTestsDirectory = docsDirectory.toScala / DockerHelpers.multipleTestsDirectoryName

    val directories = selectedTest match {
      case Some(dirName) => Seq(multipleTestsDirectory / dirName)
      case None => multipleTestsDirectory.list.toSeq
    }

    ParallelCollectionsUtils
      .toPar(directories)
      .map { testDirectory =>
        val srcDir = testDirectory / "src"
        // on multiple tests, the language is not validated but required. We used Scala.
        val dockerTool = createDockerTool(Set(Languages.Scala), dockerImage)
        val toolDocumentation = new DockerToolDocumentation(dockerTool, new BinaryDockerHelper(useCachedDocs = false))
        val dockerRunner = new BinaryDockerRunner[Result](dockerTool)
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

  private def runTool(tool: Tool,
                      multipleTestsDirectory: File,
                      configuration: Configuration,
                      excludedFilesRegex: Option[String]): Try[Set[ToolResult]] = {
    val optRegex = excludedFilesRegex.map(_.r)

    def toExclude(file: File) = optRegex.exists(_.findFirstIn(file.name).nonEmpty)

    val filesToTest = for {
      file <- multipleTestsDirectory.listRecursively
      if file.isRegularFile && !toExclude(file)
    } yield file.path

    tool
      .run(multipleTestsDirectory, filesToTest.toSet, configuration)
      .map(_.map {
        case fileError: FileError => fileError.copy(filename = multipleTestsDirectory.relativize(fileError.filename))
        case issue: Issue => issue.copy(filename = multipleTestsDirectory.relativize(issue.filename))
      })
  }
}
