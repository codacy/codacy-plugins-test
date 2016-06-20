package codacy.plugins

import java.nio.file.Path

import codacy.plugins.docker.DockerPlugin
import codacy.plugins.test._
import codacy.utils.Printer
import org.apache.commons.io.FileUtils
import plugins.DockerImageName

case class Sources(mainSourcePath: Path, directoryPaths: Seq[Path])

object DockerTest {

  private val possibleTests = Seq(JsonTests, PluginsTests, PatternTests)
  private val possibleTestsWithUda = SourceTests +: possibleTests

  private val possibleTestNames = config.keySet

  private lazy val config = Map(
    "all" -> possibleTests,
    "allWithUdas" -> possibleTestsWithUda
  ) ++ possibleTestsWithUda.map{ case test =>
    test.opt -> Seq(test)
  }

  def main(args: Array[String]) {
    val typeOfTests = args.headOption
    val dockerImageName = args.drop(1).headOption
    val optArgs = args.drop(2)

    typeOfTests.collect { case typeOfTest if possibleTestNames.contains(typeOfTest) =>
      dockerImageName.map { dockerName =>
        val plugin = new DockerPlugin(DockerImageName(dockerName))
        val testSources = DockerHelpers.testFoldersInDocker(plugin.dockerImageName)

        val result = possibleTestsWithUda
          .map(test => run(plugin, testSources, test, typeOfTest, dockerName, optArgs))
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

  private def run(plugin: DockerPlugin, testSources: Seq[Path], test: ITest, testRequest: String, dockerImageName: String, optArgs: Seq[String]): Boolean = {
    config.get(testRequest) match{
      case Some(ts) if ts.contains(test) =>
        test.run(plugin, testSources, dockerImageName, optArgs) match {
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
}
