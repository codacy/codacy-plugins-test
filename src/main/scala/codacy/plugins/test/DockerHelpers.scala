package codacy.plugins.test

import java.nio.file.{Files, Path}

import codacy.plugins.docker.Pattern
import docker.{DockerImageName, ToolSpec}
import play.api.libs.json.Json

import _root_.scala.sys.process._

object DockerHelpers {

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

  def testsInDocker(dockerImageName: DockerImageName): Path = {
    val sourceDir = Files.createTempDirectory("docker-tests")

    val dockerStartedCmd = s"docker run --net=none --privileged=false --cap-drop=ALL --user=docker -d --entrypoint=/bin/bash $dockerImageName"
    val output = dockerStartedCmd.split(" ").toSeq.lineStream_!

    val containerId = output.head
    //copy files from running container
    s"docker cp $containerId:/docs/tests/ $sourceDir".split(" ").toSeq.!
    //remove container
    s"docker rm -f $containerId".split(" ").toSeq.!
    sourceDir.resolve("tests")
  }

}
