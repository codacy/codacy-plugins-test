package codacy.plugins

import java.nio.file.Path

import codacy.plugins.test._
import codacy.utils.Printer
import org.apache.commons.io.FileUtils

case class Sources(mainSourcePath: Path, directoryPaths: Seq[Path])

object DockerTest {

  private lazy val config = Map("all" -> possibleTests) ++ possibleTests.map { test =>
    test.opt -> Seq(test)
  }
  private lazy val possibleTests = Seq(JsonTests, PluginsTests, PatternTests)
  private lazy val possibleTestNames = config.keySet

  def main(args: Array[String]): Unit = {
    val typeOfTests = args.headOption
    val dockerImageNameAndVersionOpt = args.drop(1).headOption
    val optArgs = args.drop(2)

    typeOfTests.fold(Printer.red(s"[Missing] test type -> [${possibleTestNames.mkString(", ")}]")) {
      case typeOfTest if possibleTestNames.contains(typeOfTest) =>
        dockerImageNameAndVersionOpt.fold(Printer.red("[Missing] docker ref -> dockerName:dockerVersion")) {
          dockerImageNameAndVersion =>
            val dockerImage = parseDockerImage(dockerImageNameAndVersion)

            def runTests(testSources: Seq[Path]): Either[String, Unit] = {
              val allTestsPassed = possibleTests
                .map(test => run(testSources, test, typeOfTest, dockerImage, optArgs))
                .forall(identity)
              deleteTestSources(testSources)
              if (allTestsPassed) Right(()) else Left("[Failure] Some tests failed!")
            }

            val testRunResult = for {
              testSources <- DockerHelpers.testFoldersInDocker(dockerImage)
              res <- runTests(testSources)
            } yield res

            testRunResult match {
              case Left(err) =>
                Printer.red(err)
                System.exit(1)
              case Right(()) =>
                Printer.green("[Success] All tests passed!")
            }
        }
      case _ =>
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
            Printer.green(s"[Success] ${test.getClass.getSimpleName}")
            true
          case _ =>
            Printer.red(s"[Failure] ${test.getClass.getSimpleName}")
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
