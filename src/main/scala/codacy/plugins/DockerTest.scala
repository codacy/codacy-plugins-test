package codacy.plugins

import java.nio.file.Path

import codacy.plugins.test._
import codacy.plugins.test.multiple.MultipleTests
import org.apache.commons.io.FileUtils
import better.files.File
import wvlet.log.LogSupport

case class Sources(mainSourcePath: Path, directoryPaths: Seq[Path])

object DockerTest extends LogSupport {

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

            def runTests(testSources: Seq[Path]): Either[String, Unit] = {
              val allTestsPassed = try {
                possibleTests
                  .map(test => run(testSources, test, typeOfTest, dockerImage, optArgs))
                  .forall(identity)
              } finally deleteTestSources(testSources)
              if (allTestsPassed) Right(()) else Left("[Failure] Some tests failed!")
            }
            val tempDirectory = File.newTemporaryDirectory("docker-test-folders")
            val testRunResult = try {
              for {
                testSources <- DockerHelpers.testFoldersInDocker(dockerImage, tempDirectory.path)
                res <- runTests(testSources)
              } yield res
            } finally tempDirectory.delete(swallowIOExceptions = true)

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

  private def deleteTestSources(testSources: Seq[Path]): Unit = {
    testSources.foreach(dir => FileUtils.deleteQuietly(dir.toFile))
  }

  private def run(testSources: Seq[Path],
                  test: ITest,
                  testRequest: String,
                  dockerImage: DockerImage,
                  optArgs: Seq[String]): Boolean = {

    config.get(testRequest) match {
      case Some(ts) if ts.contains(test) =>
        test.run(testSources, dockerImage, optArgs) match {
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
