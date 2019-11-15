package codacy.plugins.test.multiple

import codacy.plugins.test._
import java.nio.file.Path

import com.codacy.analysis.core.model.{
  CodacyCfg,
  Configuration,
  FileCfg,
  FileError,
  FullLocation,
  Issue,
  LineLocation
}
import com.codacy.analysis.core.tools.Tool
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.BinaryDockerHelper
import com.codacy.plugins.results.PluginResult

import better.files._

import scala.util.{Failure, Success, Try}
import scala.xml.XML

object MultipleTests extends ITest with CustomMatchers {

  val opt = "multiple"

  def run(testDirectories: Seq[Path], dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    debug(s"Running MultipleTests:")
    val testSources = testDirectories.filter(_.getFileName.toString == DockerHelpers.multipleTestsDirectoryName)
    val languages = findLanguages(testSources, dockerImage)
    val dockerTool = createDockerTool(languages, dockerImage)
    val toolDocumentation = new DockerToolDocumentation(dockerTool, new BinaryDockerHelper(useCachedDocs = false))
    val dockerRunner = new BinaryDockerRunner[Result](dockerTool)()
    val runner = new ToolRunner(dockerTool, toolDocumentation, dockerRunner)
    val tools = languages.map(new Tool(runner, DockerRunner.defaultRunTimeout)(dockerTool, _))
    testSources.forall { multipleTestsDir =>
      val testsDirectories = File(multipleTestsDir).list
      testsDirectories.forall { testDir =>
        val srcDir = testDir / "src"
        val resultFile = testDir / "results.xml"
        val resultFileXML = XML.loadFile(resultFile.toJava)
        val expectedResults = CheckstyleFormatParser.parseResultsXml(resultFileXML).toSet
        debug(s"${testDir.name} should have ${expectedResults.size} results")
        val configuration = {
          val patternsPath = testDir / "patterns.xml"
          if (patternsPath.exists) {
            val patternsFileXML = XML.loadFile(patternsPath.toJava)
            val (patterns, extraValues) = CheckstyleFormatParser.parsePatternsXml(patternsFileXML)
            if (patterns.isEmpty) FileCfg(Some(srcDir.pathAsString), extraValues)
            else CodacyCfg(patterns, Some(srcDir.pathAsString), extraValues)
          } else FileCfg(Some(srcDir.pathAsString), None)
        }
        tools.exists { tool =>
          val res = runTool(tool, srcDir, configuration)
          res match {
            case Failure(e) =>
              info("Got failure in the analysis:")
              e.printStackTrace()
              false
            case Success(results) =>
              if (results.sameElements(expectedResults)) {
                debug(s"Got ${results.size} results.")
                true
              } else {
                error("Tool results don't match expected results:")
                error("Extra: ")
                info(pprint.apply(results.diff(expectedResults), height = Int.MaxValue))
                error("Missing:")
                info(pprint.apply(expectedResults.diff(results), height = Int.MaxValue))
                false
              }
          }
        }
      }
    }
  }

  private def runTool(tool: Tool, testDir: File, configuration: Configuration): Try[Set[PluginResult]] = {
    val builder = Set.newBuilder[PluginResult]
    for {
      set <- tool.run(testDir, testDir.list.filter(_.isRegularFile).map(_.path).toSet, configuration)
      toolResult <- set
    } toolResult match {
      case Issue(patternId, filename, message, level, category, location) =>
        val line = location match {
          case fl: FullLocation => fl.line
          case l: LineLocation => l.line
        }
        builder += PluginResult(patternId.value, filename.getFileName().toString(), line, message.text, level)
      case FileError(file, error) => return Failure(new Exception(s"Got error $error on file $file"))
    }
    Success(builder.result())
  }
}
