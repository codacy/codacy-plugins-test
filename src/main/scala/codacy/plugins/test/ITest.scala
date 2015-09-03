package codacy.plugins.test

import java.io.File
import java.nio.file.Path

import codacy.plugins.docker.{DockerPlugin, Result}
import codacy.utils.Printer
import codacy.plugins.docker.Pattern

trait ITest {
  val opt: String

  def run(plugin: DockerPlugin, sourcePath: Path): Boolean

  protected def filterResults(sourcePath: Path, files: Seq[File], patterns: Seq[Pattern], results: Seq[Result]): Seq[Result] = {
    val receivedResultsTotal = results.length

    if (results.nonEmpty) {
      Printer.green(s"$receivedResultsTotal results received.")
    } else {
      Printer.red("No results received!")
    }

    val relativeFiles = files.map(file => sourcePath.relativize(file.getAbsoluteFile.toPath).toString)
    val (filteredFileResults, otherFilesResults) = results.partition { result =>
      relativeFiles.contains(result.filename)
    }

    if (otherFilesResults.nonEmpty) {
      Printer.red(s"Some results are not in the files requested and were discarded!")
      Printer.white(
        """
          |Check the paths returned:
          |  * The tool should only return results for the files requested
          |  * The files should be relative to /src (ex: /src/dir/file.js -> dir/file.js)
        """.stripMargin)
    }

    val requestedPatternIds = patterns.map(_.patternIdentifier)
    val (filteredPatternResults, otherPatternsResults) = results.partition { result =>
      requestedPatternIds.contains(result.patternIdentifier)
    }

    if (otherPatternsResults.nonEmpty) {
      Printer.red(s"Some results returned were not requested by the test and were discarded!")
      Printer.white(
        s"""
           |Extra results returned:
           | * ${otherPatternsResults.map(_.patternIdentifier).mkString(", ")}
           |
           |Check the results returned:
           | * The tool should only return results requested in the configuration
           | * The results patternIds should match the names listed in the tools /docs/patterns.json
        """.stripMargin)
    }

    filteredFileResults.intersect(filteredPatternResults)
  }
}
