package codacy.plugins.test

import java.nio.file.Path

import codacy.utils.Printer
import com.codacy.analysis.core.model.{
  CodacyCfg,
  Configuration,
  FileCfg,
  FileError,
  FullLocation,
  Issue,
  LineLocation,
  Parameter,
  Pattern
}
import com.codacy.analysis.core.tools.Tool
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.api.results.Result.Level
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.BinaryDockerHelper
import com.codacy.plugins.results.PluginResult

import better.files._

import play.api.libs.json.Json

import scala.util.{Failure, Success, Try}
import com.fasterxml.jackson.core.JsonParseException
import scala.xml.XML

object MultipleTests extends ITest with CustomMatchers {

  val opt = "multiple"

  def run(testDirectories: Seq[Path], dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    Printer.green(s"Running MultipleTests:")
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
        val expectedResults = parseResultsXml(resultFile).toSet
        Printer.green(s"${testDir.name} should have ${expectedResults.size} results")
        val configuration = {
          val patternsPath = testDir / "patterns.xml"
          if(patternsPath.exists) {
            val (patterns, extraValues) = parsePatternsXml(patternsPath)
            if(patterns.isEmpty) FileCfg(Some(srcDir.pathAsString), extraValues)
            else CodacyCfg(patterns, Some(srcDir.pathAsString), extraValues)
          }
          else FileCfg(Some(srcDir.pathAsString), None)
        }
        tools.exists { tool =>
          val res = runTool(tool, srcDir, configuration)
          res match {
            case Failure(e) =>
              Printer.red("Got failure in the analysis:")
              e.printStackTrace()
              false
            case Success(results) =>
              if (results.sameElements(expectedResults)) {
                Printer.green(s"Got ${results.size} results.")
                true
              } else {
                Printer.red("Tool results don't match expected results:")
                Printer.red("Extra: ")
                pprint.pprintln(results.diff(expectedResults), height = Int.MaxValue)
                Printer.red("Missing:")
                pprint.pprintln(expectedResults.diff(results), height = Int.MaxValue)
                false
              }
          }
        }
      }
    }
  }

  private def parseResultsXml(file: File) = {
    for {
      fileTag <- XML.loadFile(file.toJava) \\ "checkstyle" \\ "file"
      fileName = fileTag \@ "name"
      errorsTag <- fileTag \\ "error"
      line = errorsTag \@ "line"
      patternId = errorsTag \@ "source"
      message = errorsTag \@ "message"
      severity = errorsTag \@ "severity"
      level = severity match {
        case "info" => Level.Info
        case "warning" => Level.Warn
        case "error" => Level.Err
        case _ => throw new Exception(s"$severity is not a valid level")
      }
    } yield PluginResult(patternId, fileName, line.toInt, message, level)
  }

  private def parsePatternsXml(file: File): (Set[Pattern], Option[Map[String, play.api.libs.json.JsValue]]) = {
    val rootModule = XML.loadFile(file.toJava)
    val extraValues = (rootModule \ "property").map { node =>
      val v = node \@ "value"
      val value = try {
        Json.parse(v)
      } catch {
        case _: JsonParseException => // support non quoted strings
          Json.parse(s""""$v"""")
      }
      (node \@ "name", value)
    }.toMap
    val patternsList = for {
      patternTags <- rootModule \ "module"
      patternId: String = patternTags \@ "name"
      parameters = (patternTags \ "property").map { node =>
        Parameter(node \@ "name", node \@ "value")
      }.toSet
    } yield Pattern(patternId, parameters)
    (patternsList.toSet, if(extraValues.isEmpty) None else Some(extraValues))
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
