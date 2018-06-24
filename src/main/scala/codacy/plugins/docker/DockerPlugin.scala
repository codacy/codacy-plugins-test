package codacy.plugins.docker

import java.nio.file.{Files, Path, Paths}

import codacy.docker.api._
import codacy.plugins.test.DockerHelpers
import codacy.plugins.traits.IResultsPlugin
import com.codacy.plugins.api.Source
import com.codacy.plugins.api.results.{Parameter, Pattern, Result, Tool}
import play.api.libs.json._

import _root_.scala.sys.process._
import _root_.scala.util.Try

class DockerPlugin(val dockerImageName: String) extends IResultsPlugin {

  lazy val spec: Option[Tool.Specification] = readJsonDoc[Tool.Specification]("patterns.json")

  override def run(pluginRequest: PluginRequest): PluginResults = {
    runOnFiles(pluginRequest.directory, pluginRequest.files, pluginRequest.configuration)
  }

  protected def runOnFiles(rootDirectory: String,
                           files: Seq[String],
                           configuration: PluginConfiguration): PluginResults = {
    List("chmod", "a+rwx", rootDirectory).lineStream_!.toList
    MaybeTool
      .map(_.apply(rootDirectory, files, configuration))
      .map(allResults => PluginResults(allResults.toList, Seq.empty))
      .getOrElse(PluginResults(Seq.empty, files))
  }

  def configurationFor(patterns: Seq[PluginPattern]): Option[Tool.Configuration] = MaybeTool.map(_.configFor(patterns))

  private[this] def dockerCmdForSourcePath(sourcePath: Path, configPath: Path) = {
    val rmOpts = sys.props.get("codacy.tests.noremove").map(_ => List.empty).getOrElse(List("--rm=true"))

    DockerHelpers.dockerRunCmd ++ List("-v",
                                       s"${sourcePath.toFile.getCanonicalPath}:/src:rw",
                                       "-v",
                                       s"${configPath.toFile.getCanonicalPath}:/src/.codacy.json:ro",
                                       "-v",
                                       s"${configPath.toFile.getCanonicalPath}:/.codacyrc:ro") ++
      rmOpts ++
      List(dockerImageName)
  }

  private[this] lazy val MaybeTool: Option[ToolImpl] = spec.map(new ToolImpl(_))

  private[this] def whitelist(rootDir: Path, files: Seq[String]): Set[Path] =
    files.flatMap { file =>
      Try(Paths.get(file)).map { file =>
        rootDir.relativize(file)
      }.toOption
    }.toSet

  final private[this] class ToolImpl(config: Tool.Specification) {

    def apply(rootDirectory: String, files: Seq[String], configuration: PluginConfiguration): Stream[PluginResult] = {
      Try(Paths.get(rootDirectory))
        .map { rootPath =>
          val fileList = whitelist(rootPath, files)

          val results = sourceDirWithConfigCreated(configuration.patterns, fileList)
            .flatMap { configPath =>
              val cmd = dockerCmdForSourcePath(rootPath, configPath)
              Try(cmd.lineStream_!).map(_.flatMap(line => Try(Json.parse(line)).toOption.flatMap(_.asOpt[Result])))
            }
            .getOrElse(Stream.empty[Result])

          results.collect {
            case toolResult: Result.Issue =>
              config.patterns.collectFirst {
                case patternDef if patternDef.patternId == toolResult.patternId =>
                  PluginResult(patternDef.patternId.value,
                               toolResult.file.path,
                               toolResult.line.value,
                               toolResult.message.value,
                               patternDef.level)
              }
          }.flatten
        }
        .toOption
        .getOrElse(Stream.empty)
    }

    def configFor(patterns: Seq[PluginPattern]): Tool.Configuration =
      Tool.Configuration(name = config.name,
                         patterns = Some(patterns.map {
                           case PluginPattern(id, paramsOpt) =>
                             Pattern.Definition(Pattern.Id(id), paramsOpt.map(_.map {
                               case (key, value) =>
                                 Parameter.Definition(Parameter.Name(key), Parameter.Value(value))

                             }.toSet))
                         }.toList))

    private[this] def sourceDirWithConfigCreated(patterns: Seq[PluginPattern], paths: Set[Path]): Try[Path] = {
      val thisToolsConfig = configFor(patterns)
      val fileList = paths.map { path =>
        Source.File(path.toString)
      }

      val fullConfig = Tool.CodacyConfiguration(Set(thisToolsConfig), Option(fileList).filter(_.nonEmpty), Option.empty)

      val tmpFile = Files.createTempFile("codacy-config", ".json")
      val config = Json.stringify(Json.toJson(fullConfig))
      val filePathRes = Try(Files.write(tmpFile, config.getBytes))

      List("chmod", "a+rwx", tmpFile.toFile.getCanonicalPath).lineStream_!.toList

      filePathRes
    }
  }

  private def readJsonDoc[T](name: String)(implicit docFmt: Format[T]): Option[T] = {
    DockerHelpers.readRawDoc(dockerImageName, name).flatMap(Json.parse(_).asOpt[T])
  }

}
