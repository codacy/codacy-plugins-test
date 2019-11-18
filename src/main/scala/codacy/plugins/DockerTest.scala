package codacy.plugins

import java.nio.file.Path

import codacy.plugins.test._
import codacy.plugins.test.multiple.MultipleTests
import better.files.File
import wvlet.log.{LogFormatter, LogLevel, LogSupport, Logger}

case class Sources(mainSourcePath: Path, directoryPaths: Seq[Path])

object DockerTest extends LogSupport {
  Logger.setDefaultFormatter(LogFormatter.SimpleLogFormatter)
  Logger.setDefaultLogLevel(LogLevel.ALL)

  private lazy val config = Map("all" -> Seq(JsonTests, PluginsTests, PatternTests)) ++
    possibleTests.map { test =>
      test.opt -> Seq(test)
    }
  private lazy val possibleTests = Seq(JsonTests, PluginsTests, PatternTests, MultipleTests, MetricsTests)
  private lazy val possibleTestNames = config.keySet

  def main(args: Array[String]): Unit = {
    val typeOfTests = args.headOption
    val dockerImageNameAndVersionOpt = args.drop(1).headOption
    val optArgs = args.drop(2)

    typeOfTests.fold(error(s"[Missing] test type -> [${possibleTestNames.mkString(", ")}]")) {
      case typeOfTest if possibleTestNames.contains(typeOfTest) =>
        dockerImageNameAndVersionOpt.fold(error("[Missing] docker ref -> dockerName:dockerVersion")) {
          dockerImageNameAndVersion =>
            val dockerImage = parseDockerImage(dockerImageNameAndVersion)

            def runTests(docsDirectory: File): Either[String, Unit] = {
              val allTestsPassed = possibleTests
                .map(test => run(docsDirectory, test, typeOfTest, dockerImage, optArgs))
                .forall(identity)
              if (allTestsPassed) Right(()) else Left("[Failure] Some tests failed!")
            }
            val testRunResult = DockerHelpers.usingDocsDirectoryInDockerImage(dockerImage) { docsDirectory =>
              runTests(docsDirectory)
            }
            testRunResult match {
              case Left(err) =>
                error(err)
                System.exit(1)
              case Right(()) =>
                debug("[Success] All tests passed!")
            }
        }
      case wrongTypeOfTest =>
        error(s"Wrong test type -> $wrongTypeOfTest should be one of [${possibleTestNames.mkString(", ")}]")
    }
  }

  private def run(docsDirectory: File,
                  test: ITest,
                  testRequest: String,
                  dockerImage: DockerImage,
                  optArgs: Seq[String]): Boolean = {

    config.get(testRequest) match {
      case Some(ts) if ts.contains(test) =>
        test.run(docsDirectory, dockerImage, optArgs) match {
          case true =>
            debug(s"[Success] ${test.getClass.getSimpleName}")
            true
          case _ =>
            error(s"[Failure] ${test.getClass.getSimpleName}")
            false
        }
      case _ =>
        // this test was not selected
        true
    }
  }

  private def parseDockerImage(dockerImageNameAndVersion: String): DockerImage = {
    val (dockerImageName, dockerVersion) = dockerImageNameAndVersion.split(":") match {
      case Array(name, version) => (name, version)
      case Array(name) => (name, "latest")
      case _ => throw new RuntimeException("Invalid Docker Name.")
    }
    DockerImage(dockerImageName, dockerVersion)
  }
}
