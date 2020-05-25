package codacy.plugins.test.runner

import java.nio.file.{Path, Paths}

import better.files._
import codacy.plugins.test.DockerHelpers
import com.codacy.analysis.core.model._
import com.codacy.plugins.api
import com.codacy.plugins.api.PatternFormatImplicits._
import com.codacy.plugins.api.results.Tool
import play.api.libs.json.{JsArray, JsError, JsResult, JsSuccess, JsValue, Json, Reads}

import sys.process._

object ToolRunner {
  implicit private val resultReads = new Reads[ToolResult] {

    def reads(json: JsValue): JsResult[ToolResult] = {
      val patternIdOpt = (json \ "patternId").asOpt[String]
      val lineOpt = (json \ "line").asOpt[Int]
      val message = (json \ "message").as[String]
      val filename = (json \ "filename").as[String]

      (patternIdOpt, lineOpt) match {
        case (None, None) => JsSuccess(FileError(Paths.get(filename), message))
        case (Some(patternId), Some(line)) =>
          JsSuccess(
            Issue(api.results.Pattern.Id(patternId),
                  Paths.get(filename),
                  message = Issue.Message(message),
                  api.results.Result.Level.Info,
                  None,
                  LineLocation(line))
          )
        case _ => JsError(s"Wrong result format: ${Json.prettyPrint(json)}")
      }
    }
  }

  def run(dockerImage: String, srcDir: File, files: Set[Path], configuration: Configuration): Seq[ToolResult] =
    DockerHelpers.usingDocsDirectoryInDockerImage(dockerImage) { docsDirectory =>
      (for {
        codacyrcFileFolder <- File.temporaryDirectory(parent = Some(File.root / "tmp"))
        codacyrcFile = codacyrcFileFolder / ".codacyrc"
        patternsJsonString = (docsDirectory.toScala / "patterns.json").contentAsString
        patternsJson = Json.parse(patternsJsonString)
        toolName = (patternsJson \ "name").get.as[String]
        codacyrcString = codacyrcContent(toolName, files, configuration)
        _ = codacyrcFile.write(codacyrcString)
        resultStrings = Seq("docker",
                            "run",
                            "-v",
                            s"${codacyrcFile.pathAsString}:/.codacyrc",
                            "-v",
                            s"$srcDir:/src",
                            "--net=none",
                            "--privileged=false",
                            "--user=docker",
                            dockerImage).lineStream_!.filter(_.nonEmpty)
        toolResults = resultStrings.map { r =>
          val jsResult = Json.fromJson[ToolResult](Json.parse(r))
          jsResult match {
            case JsSuccess(value, _) => value
            case JsError(errors) => throw new Exception(errors.toString())
          }
        }

        patterns = (patternsJson \ "patterns")
          .as[JsArray]
          .value
          .map(p => ((p \ "patternId").as[String], (p \ "level").as[api.results.Result.Level]))
          .toMap

        toolResultsWithCorrectLevel = toolResults.map {
          case i: Issue =>
            i.copy(level = patterns(i.patternId.value))
          case other => other
        }

      } yield toolResultsWithCorrectLevel.toList).get()
    }

  private def codacyrcContent(toolName: String, files: Set[Path], config: Configuration) = {
    def createCodacyConfiguration(toolName: String,
                                  files: Set[Path],
                                  config: Configuration): Tool.CodacyConfiguration = {
      val patternsOpt = config match {
        case CodacyCfg(patterns, _, _) => Some(patterns)
        case FileCfg(_, _) => None
      }
      val patternsDefinitionsOpt = patternsOpt.map { patterns =>
        patterns.map { pattern =>
          val params = pattern.parameters.map { param =>
            api.results.Parameter.Definition(api.results.Parameter.Name(param.name),
                                             api.results.Parameter.Value(param.value))
          }
          api.results.Pattern.Definition(api.results.Pattern.Id(pattern.id),
                                         if (params.nonEmpty) Some(params) else None)
        }.toList
      }
      val toolConfiguration = Tool.Configuration(Tool.Name(toolName), patterns = patternsDefinitionsOpt)
      val optionsOpt = config.extraValues.map(_.map { case (k, v) => (api.Options.Key(k), api.Options.Value(v)) })
      val sourceFiles = files.map(f => api.Source.File(f.toString()))
      Tool.CodacyConfiguration(Set(toolConfiguration),
                               files = if (sourceFiles.isEmpty) None else Some(sourceFiles),
                               options = optionsOpt)
    }

    val codacyConfiguration = createCodacyConfiguration(toolName, files, config)
    Json.prettyPrint(Json.toJson(codacyConfiguration))
  }
}
