package com.codacy.plugins.utils

import com.codacy.plugins.api.PatternDescription
import com.codacy.plugins.runners.{DockerConstants, IDocker}

import java.nio.file.{Files, Path}
import scala.util.{Properties, Try}

class BinaryDockerHelper extends DockerHelper {

  override def readDescription(docker: IDocker): Option[Set[PatternDescription]] = {
    val cmd =
      s"${DockerConstants.dockerRunCmd} --entrypoint=cat ${docker.dockerImage} $descriptionsFile"
        .split(" ")
        .toList

    val descriptionsOpt: Option[String] = CommandRunner.exec(cmd).map(_.mkString(System.lineSeparator())).toOption
    parseDescriptions(docker, descriptionsOpt)
  }

  override def readRaw(docker: IDocker, path: Path): Option[String] =
    Try {
      // path: hack for Windows because it compiles with dev OS fileSeparator
      val shCmd =
        s"""if [ -f ${path.toString().replace('\\', '/')} ]; then
             |  cat ${path.toString().replace('\\', '/')};
             |  if [ $$? -ne 0 ]; then
             |    exit 2;
             |  fi
             |else exit 1; fi""".stripMargin
      val cmd =
        s"""${DockerConstants.dockerRunCmd} --entrypoint=sh ${docker.dockerImage} -c"""
          .split(' ')
          .filter(_ != "")
          .toList :+ shCmd
      val errorMsg = s"Failed to read docs from ${docker.dockerImage} path $path"
      CommandRunner.execSync(cmd) match {
        case Right(CommandResult(0, stdout, stderr @ _)) =>
          Some(stdout.mkString(Properties.lineSeparator))
        case Right(CommandResult(1, stdout @ _, stderr @ _)) => // file doesn't exist
          println(errorMsg)
          None
        case Right(CommandResult(code @ _, stdout, stderr)) =>
          Log.error(s"""$errorMsg
                          |stdout: ${stdout.mkString(Properties.lineSeparator)}
                          |stderr: ${stderr.mkString(Properties.lineSeparator)}""".stripMargin)
          None
        case Left(e) =>
          Log.error(errorMsg, e)
          None
      }
    }.toOption.flatten

  protected def parseDescriptions(docker: IDocker, rawDescriptions: Option[String]): Option[Set[PatternDescription]] = {
    rawDescriptions.flatMap(parseJsonDescriptions).map { descriptions =>
      withDescriptionsDirectory(docker.dockerImage) { directory =>
        descriptions.map { pattern =>
          val descPath = directory.resolve(s"${pattern.patternId}.md")
          val fileContents = FileHelper.read(descPath.toFile).map(_.mkString(Properties.lineSeparator))
          pattern.copy(explanation = fileContents)
        }
      }
    }
  }

  private def withDescriptionsDirectory[T](dockerImageName: String)(block: Path => T): T = {
    val sourceDir = Files.createTempDirectory("docker-test-folders")
    val dockerStartedCmd = s"${DockerConstants.dockerRunNonDaemonCmd} -d --entrypoint=sh $dockerImageName"

    for {
      output <- CommandRunner.exec(dockerStartedCmd.split(" ").toList).toOption
      containerId <- output.headOption
      // Copy files from running container
      _ <- CommandRunner.exec(s"docker cp $containerId:/docs/description $sourceDir".split(" ").toList).toOption
      // Remove container
      _ <- CommandRunner.exec(s"docker rm -f $containerId".split(" ").toList).toOption
    } yield ()

    val result = block(sourceDir.resolve("description"))

    CommandRunner.exec(s"rm -f ${sourceDir.toString}".split(" ").toList)

    result
  }

}
