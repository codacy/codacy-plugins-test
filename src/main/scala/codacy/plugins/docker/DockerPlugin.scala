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
    import pluginRequest._
    runOnFiles(directory, files, configuration, files.length)
  }

  protected def runOnFiles(rootDirectory: String, files: Seq[String], configuration: PluginConfiguration, maxFileNum: Int): PluginResult = {
    Seq(s"chmod", "a+rwx", rootDirectory).lineStream_!.toList
    MaybeTool.map(_.apply(rootDirectory, files, configuration, maxFileNum)).map { case allResults =>
      PluginResult(allResults.toList, Seq.empty)
    }.getOrElse(PluginResult(Seq.empty, files))
  }

  def configurationFor(patterns: Seq[Pattern]): Option[ToolConfig] = MaybeTool.map(_.configFor(patterns))

  private[this] def dockerCmdForSourcePath(sourcePath: Path) =
    s"${DockerHelpers.dockerRunCmd} -t -v $sourcePath:/src:ro $dockerImageName".split(" ").toList

  private[this] lazy val MaybeTool: Option[ToolImpl] = spec.map(ToolImpl.apply)

  private[this] def whitelist(rootDir: Path, files: Seq[String]): Set[Path] = files.flatMap { case file =>
    Try(Paths.get(file)).map { file =>
      rootDir.relativize(file)
    }.toOption
  }.toSet

  private[this] case class ToolImpl(config: ToolSpec) {

    import config.{name => toolName}

    def apply(rootDirectory: String, files: Seq[String], configuration: PluginConfiguration, maxFileNum: Int): Stream[Result] = {
      Try(Paths.get(rootDirectory)).map { case rootPath =>
        val fileList = whitelist(rootPath, files)

        val results = sourceDirWithConfigCreated(rootPath, configuration.patterns, fileList).flatMap { case sourceDir =>
          val cmd = dockerCmdForSourcePath(sourceDir)
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
      name = toolName,
      patterns = patterns.map { case pattern =>
        PatternWithParam(
          patternId = PatternId(pattern.patternIdentifier),
          parameters = Option(pattern.parameters).filterNot(_.isEmpty).map(_.map { case (key, value) =>
            Param(
              name = ParamName(key),
              value = value
            )
          }.toSeq
          ))
      }
    )

    private[this] def sourceDirWithConfigCreated(sourceDir: Path, patterns: Seq[Pattern], paths: Set[Path]): Try[Path] = {
      val thisToolsConfig = configFor(patterns)
      val fileList = paths.map { path => SourcePath(path.toString) }

      val fullConfig = FullConfig(Set(thisToolsConfig), Option(fileList).filter(_.nonEmpty))

      val config = Json.stringify(Json.toJson(fullConfig))
      val path = sourceDir.resolve(Paths.get(".codacy.json"))
      Try(Files.write(path, config.getBytes)).map(_ => sourceDir)
    }
  }

  private def readJsonDoc[T](name: String)(implicit docFmt: Format[T]): Option[T] = {
    DockerHelpers.readRawDoc(dockerImageName, name).flatMap(Json.parse(_).asOpt[T])
  }

}
