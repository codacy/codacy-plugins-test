package codacy.plugins

import java.nio.file.Path

import _root_.docker.DockerImageName
import codacy.plugins.docker.DockerPlugin
import codacy.plugins.test.{DockerHelpers, ITest, PatternTests, PluginsTests}
import codacy.utils.Printer
import org.apache.commons.io.FileUtils

object DockerTest {

  private val allTestKey = Seq("all")
  private val possibleTests = Seq(PluginsTests, PatternTests)
  private val possibleTestNames = allTestKey ++ possibleTests.map(_.opt)

  def main(args: Array[String]) {
    val typeOfTests = args.headOption
    val dockerImageName = args.drop(1).headOption

    typeOfTests.collect { case typeOfTest if possibleTestNames.contains(typeOfTest) =>
      dockerImageName.map { dockerName =>
        val plugin = new DockerPlugin(DockerImageName(dockerName))
        val sourcePath = DockerHelpers.testsInDocker(plugin.dockerImageName)

        val result = possibleTests
          .map(test => run(plugin, sourcePath, test, typeOfTest))
          .forall(identity)

        FileUtils.deleteDirectory(sourcePath.toFile)

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

  private def run(plugin: DockerPlugin, sourcePath: Path, test: ITest, testRequest: String): Boolean = {
    if ((allTestKey :+ test.opt).contains(testRequest)) {
      test.run(plugin, sourcePath) match {
        case true =>
          Printer.green(s"[Success] ${test.getClass.getSimpleName}")
          true
        case _ =>
          Printer.red(s"[Failure] ${test.getClass.getSimpleName}")
          false
      }
    } else {
      // this test was not selected
      true
    }
  }
}
