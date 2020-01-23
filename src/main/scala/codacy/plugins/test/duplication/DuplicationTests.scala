package codacy.plugins.test.duplication

import codacy.plugins.test._
import com.codacy.analysis.core
import better.files._
import java.io.{File => JFile}

import codacy.plugins.test.resultprinter.ResultPrinter

import scala.util.Try
import scala.xml.XML

import com.codacy.analysis.core.model.DuplicationClone
import com.codacy.plugins.api.duplication.DuplicationTool.CodacyConfiguration
import com.codacy.plugins.duplication.api.DuplicationRequest
import com.codacy.plugins.duplication.traits.DuplicationRunner
import com.codacy.plugins.duplication.{api, _}
import com.codacy.plugins.runners.BinaryDockerRunner
import scala.concurrent.duration._

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
          val res = this.runDuplicationTool(srcDir, duplicationTool, tool)
          ResultPrinter.printToolResults(res, expectedResults)
        }
      }
      .forall(identity)
  }

  private def runDuplicationTool(srcDir: File,
                                 duplicationTool: traits.DuplicationTool,
                                 tool: com.codacy.analysis.core.tools.DuplicationTool): Try[Set[DuplicationClone]] = {
    val request = DuplicationRequest(srcDir.pathAsString)

    val dockerRunner = new BinaryDockerRunner[api.DuplicationClone](duplicationTool)()
    val runner = new DuplicationRunner(duplicationTool, dockerRunner)

    for {
      duplicationClones <- runner.run(request,
                                      CodacyConfiguration(Option(tool.languageToRun), Option.empty),
                                      15.minutes,
                                      None)
    } yield {
      duplicationClones.map(
        clone => DuplicationClone(clone.cloneLines, clone.nrTokens, clone.nrLines, clone.files.to[Set])
      )(collection.breakOut): Set[DuplicationClone]
    }
  }
}
