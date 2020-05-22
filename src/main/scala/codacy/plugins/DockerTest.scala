package codacy.plugins

import java.io.{File => JFile}
import java.nio.file.Path

import codacy.plugins.test._
import codacy.plugins.test.duplication.DuplicationTests
import codacy.plugins.test.multiple.MultipleTests
import wvlet.log.{LogFormatter, LogLevel, LogSupport, Logger}

case class Sources(mainSourcePath: Path, directoryPaths: Seq[Path])

object DockerTest extends LogSupport {
  Logger.setDefaultFormatter(LogFormatter.SimpleLogFormatter)
  Logger.setDefaultLogLevel(LogLevel.DEBUG)

  private lazy val config = Map("all" -> Seq(JsonTests, PluginsTests, PatternTests)) ++
    possibleTests.map { test =>
      test.opt -> Seq(test)
    }
  private lazy val possibleTests =
    Seq(JsonTests, PluginsTests, PatternTests, MultipleTests, MetricsTests, DuplicationTests)
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

            DockerHelpers.usingDocsDirectoryInDockerImage(dockerImage.toString) { docsDirectory =>
              val allTestsPassed = possibleTests
                .map(test => run(docsDirectory, test, typeOfTest, dockerImage, optArgs))
                .forall(identity)
              if (allTestsPassed) Right(()) else Left("[Failure] Some tests failed!")
            }
            debug("[Success] All tests passed!")
        }
      case wrongTypeOfTest =>
        throw new Exception(
          s"Wrong test type -> $wrongTypeOfTest should be one of [${possibleTestNames.mkString(", ")}]"
        )
    }
  }

  private def run(docsDirectory: JFile,
                  test: ITest,
                  testRequest: String,
                  dockerImage: DockerImage,
                  optArgs: Seq[String]): Boolean = {

    config.get(testRequest) match {
      case Some(ts) if ts.contains(test) =>
        val isSuccess = test.run(docsDirectory, dockerImage, optArgs)
        if (isSuccess)
          debug(s"[Success] ${test.getClass.getSimpleName}")
        else
          error(s"[Failure] ${test.getClass.getSimpleName}")
        isSuccess
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
