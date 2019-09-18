package codacy.plugins.test

import scala.sys.process._
import better.files._
import java.nio.file.Path

object DockerHelpers {

  val dockerRunCmd = List("docker", "run", "--net=none", "--privileged=false", "--user=docker")

  def testFoldersInDocker(dockerImage: DockerImage): Either[String, Seq[Path]] = {
    val sourceDirFile = File.newTemporaryDirectory("docker-test-folders")
    val sourceDir = sourceDirFile.path
    val dockerStartedCmd = dockerRunCmd ++ List("-d", "--entrypoint=sh", dockerImage.toString)
    val output = dockerStartedCmd.lineStream_!.headOption
    try {
      output match {
        case Some(containerId) =>
          //copy files from running container
          List("docker", "cp", s"$containerId:/docs/directory-tests", sourceDir.toString).!

          // backwards compatibility, making sure directory tests exist so we can copy the old test dir
          List("mkdir", "-p", s"$sourceDir/directory-tests").!
          List("docker", "cp", s"$containerId:/docs/tests", s"$sourceDir/directory-tests").!

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
        List("docker", "rm", "-f", containerId).!
      }
      sourceDirFile.delete(swallowIOExceptions = true)
    }
  }

}
