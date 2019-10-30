package codacy.plugins.test

import java.nio.file.Path

import codacy.utils.Printer
import com.codacy.analysis.core.model.{CodacyCfg, Configuration, FileCfg, FileError, FullLocation, Issue, LineLocation, Parameter, Pattern}
import com.codacy.analysis.core.tools.Tool
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.api.results.Result.Level
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.BinaryDockerHelper
import com.codacy.plugins.results.PluginResult

import better.files._

import play.api.libs.json.Json
import java.io.StringReader
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}

import scala.util.{Failure, Success, Try}
import com.fasterxml.jackson.core.JsonParseException

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
      val testsDirectories = File(multipleTestsDir).list.toList
      testsDirectories.forall { testDir =>
        val testFiles = testDir.list(_.isRegularFile).toList
        val resultFile = testDir / "results"
        val expectedResults = parseResultsCsv(resultFile).toSet
        Printer.green(s"${testDir.name} should have ${expectedResults.size} results")
        val configuration = {
          val extraValues = testFiles.find(_.name == "extraValues").map(parseExtraValuesCsv)
          testFiles.find(_.name == "patterns") match {
            case Some(file) => CodacyCfg(parsePatternsCsv(file), Some(testDir.pathAsString), extraValues)
            case None => FileCfg(Some(testDir.pathAsString), extraValues)
          }
        }
        tools.exists { tool =>
          val res = runTool(tool, testDir, configuration)
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

  private def parseResultsCsv(file: File) = {
    CSVReader.open(file.toJava).all().map {
      case List(patternIdentifier, filename, lineString, message, levelString) =>
        val level = levelString match {
          case "Err" => Level.Err
          case "Warn" => Level.Warn
          case "Info" => Level.Info
          case _ => throw new Exception(s"$levelString is not a valid level")
        }
        PluginResult(patternIdentifier, filename, lineString.toInt, message, level)

      case l => throw new Exception(s"Line $l has wrong number or comma separated value")
    }
  }

  private def parsePatternsCsv(file: File): Set[Pattern] = {
    val colonFormat = new DefaultCSVFormat {
      override val delimiter: Char = ':'
    }

    CSVReader
      .open(file.toJava)
      .all()
      .map {
        case patternId :: Nil => Pattern(patternId, Set.empty)
        case patternId :: parametersList =>
          val parameters = {
            parametersList.map { parameterString =>
              CSVReader.open(new StringReader(parameterString))(colonFormat).all() match {
                case List(List(key, value)) => Parameter(key, value)
                case _ => throw new Exception(s"parameters in $parametersList should be in format key:value")
              }
            }.toSet
          }
          Pattern(patternId, parameters)
        case l => throw new Exception(s"Line $l has wrong number or comma separated value")
      }
      .toSet
  }

  private def parseExtraValuesCsv(file: File): Map[String, play.api.libs.json.JsValue] = {
    val list = CSVReader.open(file.toJava).all().map {
      case List(key, v) =>
        val value = try {
          Json.parse(v)
        } catch {
          case _: JsonParseException => // support non quoted strings
            Json.parse(s""""$v"""")
        }
        (key, value)

      case l => throw new Exception(s"Line $l has wrong number or comma separated value")
    }
    list.toMap
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
