package codacy.plugins.test

import java.io.{File => JFile}

import scala.sys.process._
import scala.util.Try

import better.files.File

object DockerHelpers {
  val testsDirectoryName = "tests"
  val multipleTestsDirectoryName = "multiple-tests"
  val duplicationTestsDirectoryName = "duplication-tests"

  private val processLogger = ProcessLogger((_: String) => ())

  val dockerRunCmd = List("docker", "run", "--net=none", "--privileged=false", "--user=docker")

  def usingDocsDirectoryInDockerImage(
    dockerImage: DockerImage
  )(f: JFile => Either[String, Unit]): Either[String, Unit] = {
    val dockerStartedCmd = dockerRunCmd ++ List("-d", "--entrypoint=sh", dockerImage.toString)
    val output = dockerStartedCmd.lineStream_!.headOption
    val directory = File.newTemporaryDirectory()
    try {
      output match {
        case Some(containerId) =>
          //copy files from running container
          List("docker", "cp", s"$containerId:/docs", directory.pathAsString) ! processLogger
          f((directory / "docs").toJava)
        case None =>
          Left("[Failure] Couldn't get the container id!")
      }
    } finally {
      for (containerId <- output) {
        // Remove container
        Try(List("docker", "rm", "-f", containerId) ! processLogger)
        directory.delete(swallowIOExceptions = true)
      }
    }
  }

}
