package codacy.plugins.test.duplication

import codacy.plugins.test._

import com.codacy.plugins.duplication.traits
import com.codacy.analysis.core

import better.files._
import java.io.{File => JFile}

import scala.util.Try
import scala.xml.XML
import com.codacy.plugins.api.duplication.DuplicationClone

object DuplicationTests extends ITest {

  val opt = "duplication"

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    debug(s"Running DuplicationTests:")
    val testsDirectory = docsDirectory.toScala / DockerHelpers.duplicationTestsDirectoryName
    testsDirectory.list.toList
      .map { testDirectory =>
        val srcDir = testDirectory / "src"
        val languages = findLanguages(srcDir.toJava, dockerImage)
        val duplicationTool = new traits.DuplicationTool(languages.toList, dockerImage.name, dockerImage.version) {}
        val duplicationTools = languages.map(l => new core.tools.DuplicationTool(duplicationTool, l))
        val resultFile = testDirectory / "results.xml"
        val resultFileXML = XML.loadFile(resultFile.toJava)
        val expectedResults = CheckstyleFormatParser.parseResultsXml(resultFileXML).toSet
        debug(s"${testDirectory.name} should have ${expectedResults.size} results")
        duplicationTools.exists { tool =>
          val res = runTool(tool, srcDir)
          ResultPrinter.printToolResults(res, expectedResults)
        }
      }
      .forall(identity)
  }

  private def runTool(tool: core.tools.DuplicationTool, multipleTestsDirectory: File): Try[Set[DuplicationClone]] = {
    val filesToTest = for {
      file <- multipleTestsDirectory.listRecursively
      if file.isRegularFile
    } yield file.path
    tool
      .run(multipleTestsDirectory, filesToTest.toSet)
      .map(_.map(Utils.toCodacyPluginsApiDuplicationDuplicationClone))
  }
}
