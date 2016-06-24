package codacy.plugins.test

import java.nio.file.{Files, Path}

import codacy.plugins.docker.Pattern
import plugins._

import _root_.scala.sys.process._
import scala.util.Try

object DockerHelpers {

  val dockerRunCmd = List("docker", "run", "--net=none", "--privileged=false", "--user=docker")

  def toPatterns(toolSpec: ToolSpec): Seq[Pattern] = toolSpec.patterns.map { case patternSpec =>
    val parameters = patternSpec.parameters.map(_.map { param =>
      (param.name.value, param.default)
    }.toMap)

    Pattern(patternSpec.patternId.value, parameters)
  }.toSeq

  def toPatterns(patterns: Seq[PatternSimple]): Seq[Pattern] = patterns.map {
    case pattern =>
      Pattern(pattern.name, pattern.parameters)
  }

  def testFoldersInDocker(dockerImageName: DockerImageName): Seq[Path] = {
    val sourceDir = Files.createTempDirectory("docker-test-folders")

    val dockerStartedCmd = dockerRunCmd ++ List("-d", "--entrypoint=bash", dockerImageName.value)
    val output = dockerStartedCmd.lineStream_!

    val containerId = output.head
    //copy files from running container
    List("docker", "cp", s"$containerId:/docs/directory-tests", sourceDir.toString).lineStream_!.toList

    // backwards compatibility, making sure directory tests exist so we can copy the old test dir
    List("mkdir", "-p", s"$sourceDir/directory-tests").lineStream_!.toList
    List("docker", "cp", s"$containerId:/docs/tests", s"$sourceDir/directory-tests").lineStream_!.toList

    // Remove container
    List("docker", "rm", "-f", containerId).lineStream_!.toList
    val sourcesDir = sourceDir.resolve("directory-tests")

    sourcesDir.toFile.listFiles().collect {
      case dir if dir.exists() =>
        dir.toPath
    }
  }

  def readRawDoc(dockerImageName: String, name: String): Option[String] = {
    val cmd = dockerRunCmd ++ List("--entrypoint=cat", dockerImageName, s"/docs/$name")
    Try(cmd.lineStream.toList).map { case rawConfigString =>
      rawConfigString.mkString(System.lineSeparator())
    }.toOption
  }

  def withDocsDirectory[T](dockerImageName: String)(block: Path => T): T = {
    val sourceDir = Files.createTempDirectory("docker-docs-folders")
    val dockerStartedCmd = dockerRunCmd ++ List("-d", "--entrypoint=bash", dockerImageName)

    for {
      output <- Try(dockerStartedCmd.lineStream_!.toList).toOption
      containerId <- output.headOption
      // Copy files from running container
      _ <- Try(List("docker", "cp", s"$containerId:/docs", sourceDir.toString).lineStream_!.toList).toOption
      // Remove container
      _ <- Try(List("docker", "rm", "-f", containerId).lineStream_!.toList).toOption
    } yield ()

    val result = block(sourceDir.resolve("docs"))

    Try(List("rm", "-rf", sourceDir.toString).lineStream_!.toList)

    result
  }

}
