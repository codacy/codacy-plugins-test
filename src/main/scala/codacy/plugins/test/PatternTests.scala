package codacy.plugins.test

import java.io.File
import java.nio.file.Path

import codacy.plugins.docker.{DockerPlugin, Pattern, PluginConfiguration, PluginRequest}
import codacy.plugins.traits.IResultsPlugin
import codacy.utils.{Printer, FileHelper}
import org.apache.commons.io.FileUtils

case class TestPattern(toolName: String, plugin: IResultsPlugin, patternId: String)

object PatternTests extends ITest with CustomMatchers {

  val opt = "pattern"

  def run(plugin: DockerPlugin, sourcePath: Path): Boolean = {
    Printer.green(s"Running PatternsTests:")
    val testFiles = new TestFilesParser(sourcePath.toFile).getTestFiles
    testFiles.map { testFile =>
      analyseFile(sourcePath.toFile, testFile, plugin)
    }.forall(identity)
  }

  private def analyseFile(rootDirectory: File, testFile: PatternTestFile, plugin: DockerPlugin): Boolean = {

    val testFilePath = testFile.file.getAbsolutePath
    val filename = toRelativePath(rootDirectory.getAbsolutePath, testFilePath)

    Printer.green(s"- $filename should have ${testFile.matches.length} matches with patterns: " +
      testFile.enabledPatterns.map(_.name).mkString(", "))

    val matches = FileHelper.withRandomDirectory { testDirectory =>
      FileUtils.copyFileToDirectory(testFile.file, testDirectory, true)

      val configuration = DockerHelpers.toPatterns(testFile.enabledPatterns)

      testFile.enabledPatterns.map { pattern =>
        Pattern(pattern.name, pattern.parameters)
      }

      val testFiles = Seq(new java.io.File(testDirectory, testFile.file.getName)).map(_.getAbsolutePath)

      val pluginResult = plugin.run(PluginRequest(testDirectory.getAbsolutePath, testFiles, PluginConfiguration(configuration)))
      val resultsUUIDS = pluginResult.results.map(_.patternIdentifier).distinct

      if (resultsUUIDS.isEmpty) Printer.red(s"  + failed in file $filename, no results found!")
      else Printer.green(s"  + found ${pluginResult.results.length} results")

      pluginResult.results.map(r => TestFileResult(r.patternIdentifier, r.line, r.level))
    }

    val comparison = beEqualTo(testFile.matches).apply(matches)

    Printer.green(s"  + ${matches.length} matches found in lines: ${matches.map(_.line).sorted.mkString(", ")}")

    if (!comparison.matches) Printer.red(comparison.rawFailureMessage)
    else Printer.green(comparison.rawNegatedFailureMessage)

    comparison.matches
  }

  private def toRelativePath(rootPath: String, absolutePath: String) = {
    absolutePath.stripPrefix(rootPath).stripPrefix(File.separator)
  }

}
