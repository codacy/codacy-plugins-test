package codacy.plugins.test

import scala.sys.process._
import java.nio.file.Path

object DockerHelpers {
  val testsDirectoryName = "tests"
  val multipleTestsDirectoryName = "multiple-tests"

  private val processLogger = ProcessLogger((line: String) => ())

  val dockerRunCmd = List("docker", "run", "--net=none", "--privileged=false", "--user=docker")

  def testFoldersInDocker(dockerImage: DockerImage, sourceDir: Path): Either[String, Seq[Path]] = {
    val dockerStartedCmd = dockerRunCmd ++ List("-d", "--entrypoint=sh", dockerImage.toString)
    val output = dockerStartedCmd.lineStream_!.headOption
    try {
      output match {
        case Some(containerId) =>
          //copy files from running container
          List("docker", "cp", s"$containerId:/docs/directory-tests", sourceDir.toString) ! processLogger

          // backwards compatibility, making sure directory tests exist so we can copy the old test dir
          List("mkdir", "-p", s"$sourceDir/directory-tests") ! processLogger
          List("docker", "cp", s"$containerId:/docs/$testsDirectoryName", s"$sourceDir/directory-tests") ! processLogger
          List("docker", "cp", s"$containerId:/docs/$multipleTestsDirectoryName", s"$sourceDir/directory-tests") ! processLogger

          val sourcesDir = sourceDir.resolve("directory-tests")

          val pathArr = sourcesDir.toFile.listFiles().collect {
            case dir if dir.exists() =>
              dir.toPath
          }
          Right(pathArr)
        case None =>
          Left("[Failure] Couldn't get the container id!")
      }
    } finally {
      for (containerId <- output) {
        // Remove container
        List("docker", "rm", "-f", containerId) ! processLogger
      }
    }
  }

}
