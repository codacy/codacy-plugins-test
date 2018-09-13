package codacy.plugins

import java.nio.file.Path

import codacy.plugins.test._
import codacy.utils.Printer
import com.codacy.plugins.api.results.Tool
import org.apache.commons.io.FileUtils
import play.api.libs.json.{Format, Json}

case class Sources(mainSourcePath: Path, directoryPaths: Seq[Path])

object DockerTest {

  private lazy val config = Map(
    "all" -> possibleTests,
    "allWithUdas" -> possibleTestsWithUda
  ) ++ possibleTestsWithUda.map { test =>
    test.opt -> Seq(test)
  }
  private lazy val possibleTests = Seq(JsonTests, PluginsTests, PatternTests)
  private lazy val possibleTestsWithUda = SourceTests +: possibleTests
  private lazy val possibleTestNames = config.keySet

  def main(args: Array[String]) {
    val typeOfTests = args.headOption
    val dockerImageNameAndVersionOpt = args.drop(1).headOption
    val optArgs = args.drop(2)

    typeOfTests.collect { case typeOfTest if possibleTestNames.contains(typeOfTest) =>
      dockerImageNameAndVersionOpt.map { dockerImageNameAndVersion =>

        val testSources = DockerHelpers.testFoldersInDocker(dockerImageNameAndVersion)

        val result = possibleTestsWithUda
          .map(test => run(testSources, test, typeOfTest, dockerImageNameAndVersion, optArgs))
          .forall(identity)

        testSources.foreach(dir => FileUtils.deleteQuietly(dir.toFile))

        if (!result) {
          Printer.red("[Failure] Some tests failed!")
          System.exit(1)
        }

        Printer.green("[Success] All tests passed!")
        result
      }.orElse {
        Printer.red("[Missing] docker ref -> dockerName:dockerVersion")
        None
      }
    }.orElse {
      Printer.red(s"[Missing] test type -> [${possibleTestNames.mkString(", ")}]")
      None
    }
  }

  private def run(testSources: Seq[Path], test: ITest, testRequest: String, dockerImageNameAndVersion: String, optArgs: Seq[String]): Boolean = {

    config.get(testRequest) match {
      case Some(ts) if ts.contains(test) =>

        val spec: Option[Tool.Specification] = readJsonDoc[Tool.Specification](dockerImageNameAndVersion, "patterns.json")

        val (dockerImageName, dockerVersion) = dockerImageNameAndVersion.split(":") match {
          case Array(name, version) => (name, version)
          case Array(name) => (name, "latest")
          case _ => throw new RuntimeException("Invalid Docker Name.")
        }

        test.run(spec, testSources, DockerImage(dockerImageName, dockerVersion), optArgs) match {
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

  private def readJsonDoc[T](dockerImageName: String, name: String)(implicit docFmt: Format[T]): Option[T] = {
    DockerHelpers.readRawDoc(dockerImageName, name).flatMap(Json.parse(_).asOpt[T])
  }

}
