package codacy.plugins.test

import java.nio.file.{Files, Path}

import codacy.plugins.docker.Pattern
import plugins._

import _root_.scala.sys.process._
import scala.util.Try

object DockerHelpers {

  val dockerRunCmd = "docker run --net=none --privileged=false --user=docker"

  def toPatterns(toolSpec: ToolSpec): Seq[Pattern] = toolSpec.patterns.map { case patternSpec =>
    val parameters = patternSpec.parameters.map(_.map { param =>
      (param.name.value, param.default)
    }.toMap).getOrElse(Map.empty)

    Pattern(patternSpec.patternId.value, parameters)
  }.toSeq

  def toPatterns(patterns: Seq[PatternSimple]): Seq[Pattern] = patterns.map {
    case pattern =>
      val parameters = pattern.parameters.map { case (name, value) =>
        (name, value)
      }

      Pattern(pattern.name, parameters)
  }

  def testFoldersInDocker(dockerImageName: DockerImageName): Seq[Path] = {
    val sourceDir = Files.createTempDirectory("docker-test-folders")

    val dockerStartedCmd = s"$dockerRunCmd -d --entrypoint=/bin/bash $dockerImageName"
    val output = dockerStartedCmd.split(" ").toSeq.lineStream_!

    val containerId = output.head
    //copy files from running container
    s"docker cp $containerId:/docs/directory-tests $sourceDir".split(" ").toSeq.!

    // backwards compatibility, making sure directory tests exist so we can copy the old test dir
    s"mkdir -p $sourceDir/directory-tests".split(" ").toSeq.!
    s"docker cp $containerId:/docs/tests $sourceDir/directory-tests".split(" ").toSeq.!

    val sourcesDir = sourceDir.resolve("directory-tests")

    sourcesDir.toFile.listFiles().collect {
      case dir if dir.exists() =>
        dir.toPath
    }
  }

  def readRawDoc(dockerImageName: String, name: String): Option[String] = {
    val cmd = s"$dockerRunCmd -t --entrypoint=cat $dockerImageName /docs/$name".split(" ").toSeq
    Try(cmd.lineStream.toList).map { case rawConfigString =>
      rawConfigString.mkString(System.lineSeparator())
    }.toOption
  }

}
