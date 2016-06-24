package codacy.plugins.docker

import java.nio.file.{Files, Path, Paths}

import codacy.plugins.test.DockerHelpers
import codacy.plugins.traits.IResultsPlugin
import play.api.libs.json.{Format, Json}
import plugins._

import _root_.scala.sys.process._
import _root_.scala.util.Try

class DockerPlugin(val dockerImageName: DockerImageName) extends IResultsPlugin {

  lazy val spec: Option[ToolSpec] = readJsonDoc[ToolSpec]("patterns.json")

  override def run(pluginRequest: PluginRequest): PluginResult = {
    runOnFiles(pluginRequest.directory, pluginRequest.files, pluginRequest.configuration, pluginRequest.files.length)
  }

  protected def runOnFiles(rootDirectory: String, files: Seq[String], configuration: PluginConfiguration, maxFileNum: Int): PluginResult = {
    List("chmod", "a+rwx", rootDirectory).lineStream_!.toList
    MaybeTool.map(_.apply(rootDirectory, files, configuration, maxFileNum)).map { case allResults =>
      PluginResult(allResults.toList, Seq.empty)
    }.getOrElse(PluginResult(Seq.empty, files))
  }

  def configurationFor(patterns: Seq[Pattern]): Option[ToolConfig] = MaybeTool.map(_.configFor(patterns))

  private[this] def dockerCmdForSourcePath(sourcePath: Path, configPath: Path) = {
    val rmOpts = sys.props.get("codacy.tests.noremove").map(_ => List.empty).getOrElse(List("--rm=true"))

    DockerHelpers.dockerRunCmd ++ List("-v", s"${sourcePath.toFile.getCanonicalPath}:/src:rw",
      "-v", s"${configPath.toFile.getCanonicalPath}:/src/.codacy.json:ro") ++
      rmOpts ++
      List(dockerImageName.value)
  }

  private[this] lazy val MaybeTool: Option[ToolImpl] = spec.map(ToolImpl.apply)

  private[this] def whitelist(rootDir: Path, files: Seq[String]): Set[Path] = files.flatMap { case file =>
    Try(Paths.get(file)).map { file =>
      rootDir.relativize(file)
    }.toOption
  }.toSet

  private[this] case class ToolImpl(config: ToolSpec) {
    def apply(rootDirectory: String, files: Seq[String], configuration: PluginConfiguration, maxFileNum: Int): Stream[Result] = {
      Try(Paths.get(rootDirectory)).map { case rootPath =>
        val fileList = whitelist(rootPath, files)

        val results = sourceDirWithConfigCreated(configuration.patterns, fileList).flatMap { configPath =>
          val cmd = dockerCmdForSourcePath(rootPath, configPath)
          Try(cmd.lineStream_!).map(_.flatMap { case line =>
            Try(Json.parse(line)).toOption.flatMap(_.asOpt[ToolResult])
          })
        }.getOrElse(Stream.empty[ToolResult])

        results.collect { case toolResult: Issue =>
          config.patterns.collectFirst { case patternDef if patternDef.patternId == toolResult.patternId =>
            Result(patternDef.patternId, toolResult.filename, toolResult.line, toolResult.message, patternDef.level)
          }
        }.flatten
      }.toOption.getOrElse(Stream.empty)
    }

    def configFor(patterns: Seq[Pattern]): ToolConfig = ToolConfig(
      name = config.name,
      patterns = patterns.map { case pattern =>
        PatternWithParam(
          patternId = PatternId(pattern.patternIdentifier),
          parameters = pattern.parameters.map(_.map { case (key, value) =>
            Param(
              name = ParamName(key),
              value = value
            )
          }.toSeq
          ))
      }
    )

    private[this] def sourceDirWithConfigCreated(patterns: Seq[Pattern], paths: Set[Path]): Try[Path] = {
      val thisToolsConfig = configFor(patterns)
      val fileList = paths.map { path => SourcePath(path.toString) }

      val fullConfig = FullConfig(Set(thisToolsConfig), Option(fileList).filter(_.nonEmpty))

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
