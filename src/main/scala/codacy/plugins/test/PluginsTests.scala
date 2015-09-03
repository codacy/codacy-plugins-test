package codacy.plugins.test

import java.nio.file.{Paths, Path}

import codacy.plugins.docker.{DockerPlugin, PluginConfiguration, PluginRequest}
import codacy.utils.{Printer, FileHelper}

object PluginsTests extends ITest {

  val opt = "plugin"

  def run(plugin: DockerPlugin, sourcePath: Path): Boolean = {
    Printer.green("Running PluginsTests:")
    plugin.spec.forall { spec =>
      Printer.green(s"  + ${spec.name} should find results for all patterns")

      val files = FileHelper.listFiles(sourcePath.toFile)
      val fileAbsolutePaths = files.map(_.getAbsolutePath)

      val patterns = DockerHelpers.toPatterns(spec)

      val filteredResults = {
        val pluginResult = plugin.run(PluginRequest(sourcePath.toAbsolutePath.toString, fileAbsolutePaths, PluginConfiguration(patterns)))
        filterResults(sourcePath, files, patterns, pluginResult.results)
      }

      val resultsUUIDS = filteredResults.map(_.patternIdentifier).distinct

      val missingPatterns = patterns.map(_.patternIdentifier).diff(resultsUUIDS)

      if (missingPatterns.nonEmpty) {
        Printer.red(
          s"""
             |Some patterns are not tested on plugin ${spec.name}
             |-> Missing patterns:
             |${missingPatterns.mkString(", ")}
           """.stripMargin)
        false
      } else {
        Printer.green("All the patterns have occurrences in the test files.")
        true
      }
    }
  }

}
