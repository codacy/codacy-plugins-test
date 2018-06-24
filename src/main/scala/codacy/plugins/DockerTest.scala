package codacy.plugins

import java.nio.file.Path

import better.files.File
import codacy.plugins.docker.DockerPlugin
import codacy.plugins.test._
import codacy.utils.Printer

final case class Sources(mainSourcePath: Path, directoryPaths: Seq[Path])

object DockerTest {

  private lazy val config = Map("all" -> possibleTests, "allWithUdas" -> possibleTestsWithUda) ++ possibleTestsWithUda
    .map(test => test.opt -> Seq(test))
  private lazy val possibleTests = Seq(JsonTests, PluginsTests, PatternTests)
  private lazy val possibleTestsWithUda = SourceTests +: possibleTests
  private lazy val possibleTestNames = config.keySet

  def main(args: Array[String]): Unit = {
    val typeOfTests = args.headOption
    val dockerImageName = args.drop(1).headOption
    val optArgs = args.drop(2)

    typeOfTests
      .collect {
        case typeOfTest if possibleTestNames.contains(typeOfTest) =>
          dockerImageName
            .map { dockerName =>
              val plugin = new DockerPlugin(dockerName)
              val testSources = DockerHelpers.testFoldersInDocker(plugin.dockerImageName)

              val result = possibleTestsWithUda
                .map(test => run(plugin, testSources, test, typeOfTest, dockerName, optArgs))
                .forall(identity)

              testSources.foreach(dir => File(dir).delete(swallowIOExceptions = true))

              if (!result) {
                Printer.red("[Failure] Some tests failed!")
                System.exit(1)
              }

              Printer.green("[Success] All tests passed!")
              result
            }
            .orElse {
              Printer.red("[Missing] docker ref -> dockerName:dockerVersion")
              None
            }
      }
      .orElse {
        Printer.red(s"[Missing] test type -> [${possibleTestNames.mkString(", ")}]")
        None
      }

    ()
  }

  private def run(plugin: DockerPlugin,
                  testSources: Seq[Path],
                  test: ITest,
                  testRequest: String,
                  dockerImageName: String,
                  optArgs: Seq[String]): Boolean = {
    config.get(testRequest) match {
      case Some(ts) if ts.contains(test) =>
        if (test.run(plugin, testSources, dockerImageName, optArgs)) {
          Printer.green(s"[Success] ${test.getClass.getSimpleName}")
          true
        } else {
          Printer.red(s"[Failure] ${test.getClass.getSimpleName}")
          false
        }
      case _ =>
        // this test was not selected
        true
    }
  }
}
