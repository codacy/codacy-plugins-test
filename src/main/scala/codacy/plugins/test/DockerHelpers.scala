package codacy.plugins.test

import java.nio.file.{Files, Path}
import _root_.scala.sys.process._

object DockerHelpers {

  val dockerRunCmd = List("docker", "run", "--net=none", "--privileged=false", "--user=docker")

  def testFoldersInDocker(dockerImage: DockerImage): Either[String, Seq[Path]] = {
    val sourceDir = Files.createTempDirectory("docker-test-folders")

    val dockerStartedCmd = dockerRunCmd ++ List("-d", "--entrypoint=sh", dockerImage.toString)
    val output = dockerStartedCmd.lineStream_!

    output.headOption match {
      case Some(containerId) =>
        try {
          //copy files from running container
          List("docker", "cp", s"$containerId:/docs/directory-tests", sourceDir.toString).lineStream_!.toList

          // backwards compatibility, making sure directory tests exist so we can copy the old test dir
          List("mkdir", "-p", s"$sourceDir/directory-tests").lineStream_!.toList
          List("docker", "cp", s"$containerId:/docs/tests", s"$sourceDir/directory-tests").lineStream_!.toList
        } finally {
          // Remove container
          List("docker", "rm", "-f", containerId).lineStream_!.toList
        }
        val sourcesDir = sourceDir.resolve("directory-tests")

        val pathArr = sourcesDir.toFile.listFiles().collect {
          case dir if dir.exists() =>
            dir.toPath
        }
        Right(pathArr)

      case None =>
        Left("[Failure] Couldn't get the container id!")
    }
  }

}
