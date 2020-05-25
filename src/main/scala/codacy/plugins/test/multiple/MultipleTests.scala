package codacy.plugins.test.multiple

import java.io.{File => JFile}

import scala.util.Try
import scala.xml.XML

import better.files._
import codacy.plugins.test._
import codacy.plugins.test.implicits.OrderingInstances._
import codacy.plugins.test.resultprinter.ResultPrinter
import codacy.plugins.test.runner.ToolRunner
import com.codacy.analysis.core.model._

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
        val resultFile = testDirectory / "results.xml"
        val resultFileXML = XML.loadFile(resultFile.toJava)
        val expectedResults = CheckstyleFormatParser.parseResultsXml(resultFileXML)
        val (configuration, excludedFilesRegex) = createConfiguration(testDirectory, srcDir)
        val result = runTool(dockerImage.toString(), srcDir, configuration, excludedFilesRegex)
        (testDirectory.name, result, expectedResults)
      }
      .seq
      .map {
        case (directoryName, result, expectedResults) =>
          debug(s"${directoryName} should have ${expectedResults.size} results")
          ResultPrinter.printToolResults(result, expectedResults)
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

  private def runTool(dockerImage: String,
                      multipleTestsDirectory: File,
                      configuration: Configuration,
                      excludedFilesRegex: Option[String]): Try[Seq[ToolResult]] = {
    val optRegex = excludedFilesRegex.map(_.r)

    def toExclude(file: File) = optRegex.exists(_.findFirstIn(file.name).nonEmpty)

    val filesToTest = for {
      file <- multipleTestsDirectory.listRecursively
      if file.isRegularFile && !toExclude(file)
    } yield file.path

    Try {
      ToolRunner.run(dockerImage = dockerImage,
                     srcDir = multipleTestsDirectory,
                     files = filesToTest.toSet.map((f: java.nio.file.Path) => multipleTestsDirectory.path.relativize(f)),
                     configuration = configuration)
    }
  }
}
