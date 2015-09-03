package codacy.plugins

import java.nio.file.Path

import codacy.plugins.docker.DockerPlugin
import codacy.plugins.test._
import codacy.utils.Printer
import org.apache.commons.io.FileUtils
import plugins.DockerImageName

case class Sources(mainSourcePath: Path, directoryPaths: Seq[Path])

object DockerTest {

  private val allTestKey = Seq("all")
  private val possibleTests = Seq(JsonTests, PluginsTests, PatternTests)
  private val possibleTestNames = allTestKey ++ possibleTests.map(_.opt)

  def main(args: Array[String]) {
    val typeOfTests = args.headOption
    val dockerImageName = args.drop(1).headOption

    typeOfTests.collect { case typeOfTest if possibleTestNames.contains(typeOfTest) =>
      dockerImageName.map { dockerName =>
        val plugin = new DockerPlugin(DockerImageName(dockerName))
        val mainSourcePath = DockerHelpers.testsInDocker(plugin.dockerImageName)
        val directorySourcePaths = DockerHelpers.testFoldersInDocker(plugin.dockerImageName).toFile.listFiles().toSeq.map(_.toPath)
        val testSources = Seq(mainSourcePath) ++ directorySourcePaths

        val result = possibleTests
          .map(test => run(plugin, testSources, test, typeOfTest, dockerName))
          .forall(identity)

        testSources.foreach(dir => FileUtils.deleteDirectory(dir.toFile))

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

  private def run(plugin: DockerPlugin, testSources: Seq[Path], test: ITest, testRequest: String, dockerImageName: String): Boolean = {
    if ((allTestKey :+ test.opt).contains(testRequest)) {
      test.run(plugin, testSources, dockerImageName) match {
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
