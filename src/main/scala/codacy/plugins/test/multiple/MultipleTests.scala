package codacy.plugins.test.multiple

import better.files._
import codacy.plugins.test._
import codacy.plugins.test.implicits.OrderingInstances._
import codacy.plugins.test.resultprinter.ResultPrinter
import com.codacy.analysis.core.model._
import com.codacy.plugins.api.Options
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.results.{PatternRequest, PluginConfiguration, PluginRequest}
import com.codacy.plugins.runners.BinaryDockerRunner
import com.codacy.plugins.utils.BinaryDockerHelper

import java.io.{File => JFile}
import java.nio.file.Paths
import scala.util.Try
import scala.xml.XML
import com.codacy.plugins.runners.IDocker

object MultipleTests extends ITest {

  val opt = "multiple"

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    debug(s"Running MultipleTests:")

    val directories = multipleDirectories(docsDirectory.toScala / "multiple-tests", optArgs)

    ParallelCollectionsUtils
      .toPar(directories)
      .map { testDirectory =>
        val srcDir = testDirectory / "src"
        // on multiple tests, the language is not validated but required. We used Scala.
        val dockerTool = new IDocker(dockerImage.toString()) {}
        val dockerRunner = new BinaryDockerRunner[Result](dockerTool)
        val dockerToolDocumentation = new DockerToolDocumentation(dockerTool, new BinaryDockerHelper())

        val runner =
          new ToolRunner(dockerToolDocumentation.toolSpecification, dockerToolDocumentation.toolPrefix, dockerRunner)
        val resultFile = testDirectory / "results.xml"
        val resultFileXML = XML.loadFile(resultFile.toJava)
        val expectedResults = CheckstyleFormatParser.parseResultsXml(resultFileXML)
        val (configuration, excludedFilesRegex) = createConfiguration(testDirectory)
        val results = runTool(runner, srcDir, configuration, excludedFilesRegex)
        (testDirectory.name, results, expectedResults)
      }
      .seq
      .map {
        case (directoryName, results, expectedResults) =>
          debug(s"${directoryName} should have ${expectedResults.size} results")
          ResultPrinter.printToolResults(results, expectedResults)
      }
      .forall(identity)
  }

  private def createConfiguration(testDirectory: File): (PluginConfiguration, Option[String]) = {
    val patternsPath = testDirectory / "patterns.xml"
    if (patternsPath.notExists) (PluginConfiguration(None, None), None)
    else {
      val patternsFileXML = XML.loadFile(patternsPath.toJava)
      val (patterns, extraValues, excludedFilesRegex) = CheckstyleFormatParser.parsePatternsXml(patternsFileXML)
      val configuration =
        PluginConfiguration(
          patterns =
            if (patterns.nonEmpty)
              Some(patterns.map(p => PatternRequest(p.id, p.parameters.map(p => p.name -> p.value).toMap)).toList)
            else None,
          options = extraValues.map(_.map { case (k, v) => Options.Key(k) -> Options.Value(v) })
        )
      (configuration, excludedFilesRegex)
    }
  }

  private def runTool(runner: ToolRunner,
                      multipleTestsDirectory: File,
                      configuration: PluginConfiguration,
                      excludedFilesRegex: Option[String]): Try[Seq[ToolResult]] = {
    val optRegex = excludedFilesRegex.map(_.r)

    def toExclude(file: File) = optRegex.exists(_.findFirstIn(file.name).nonEmpty)

    val filesToTest = for {
      file <- multipleTestsDirectory.listRecursively
      if file.isRegularFile && !toExclude(file)
    } yield file.pathAsString

    val pluginResultsTry =
      runner.run(PluginRequest(multipleTestsDirectory.pathAsString, filesToTest.toList, configuration))

    pluginResultsTry.map { pluginResults =>
      val fileErrors = pluginResults.fileErrors.map(
        fileError =>
          FileError(filename = Paths.get(fileError.filename),
                    message = fileError.message
                      .getOrElse(throw new Exception(s"Message not defined for fileError on ${fileError.filename}")))
      )
      val results = pluginResults.results.map(
        result =>
          Issue(patternId = com.codacy.plugins.api.results.Pattern.Id(result.patternIdentifier),
                filename = Paths.get(result.filename),
                message = Issue.Message(result.message),
                level = result.level,
                category = result.category,
                location = LineLocation(result.line),
        )
      )

      results ++ fileErrors
    }
  }
}
