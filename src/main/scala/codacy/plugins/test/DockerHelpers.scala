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

  def usingDocsDirectoryInDockerImage[T](dockerImage: String)(f: JFile => T): T = {
    val dockerStartedCmd =
      List("docker", "run", "--net=none", "--privileged=false", "--user=docker", "-d", "--entrypoint=sh", dockerImage)
    val output = dockerStartedCmd.lineStream_!.headOption
    val directory = File.newTemporaryDirectory(parent = Some(File.root / "tmp"))
    try {
      output match {
        case Some(containerId) =>
          //copy files from running container
          assert(List("docker", "cp", s"$containerId:/docs", directory.pathAsString) ! processLogger == 0)
          f((directory / "docs").toJava)
        case None =>
          throw new Exception("Couldn't get the container id!")
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
