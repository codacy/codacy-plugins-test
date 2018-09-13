package codacy.plugins.test

import java.io.File
import java.nio.file.Path

import codacy.plugins.docker.{DockerPlugin, Pattern}
import codacy.utils.Printer
import com.codacy.analysis.core.model.{FileError, Issue, ToolResult}

import scala.util.{Failure, Success, Try}

trait ITest {
  val opt: String

  def run(plugin: DockerPlugin, testSources: Seq[Path], dockerImageName: String, optArgs: Seq[String]): Boolean

  protected def filterResults(sourcePath: Path, files: Seq[File], patterns: Seq[Pattern], toolResults: Try[Set[ToolResult]]): Set[Issue] = {
    toolResults match {
      case Failure(e) =>
        Printer.red(e.getStackTrace.mkString("\n"))
        Set.empty
      case Success(results) =>
        val receivedResultsTotal = results.size

        if (results.nonEmpty) {
          Printer.green(s"$receivedResultsTotal results received.")
        } else {
          Printer.red("No results received!")
        }

        (filterFileErrors _).andThen(issues =>
          filterResultsFromFiles(issues, files, sourcePath).intersect(filterResultsFromPatterns(issues, patterns))
        )(results)
    }
  }

  private def filterResultsFromPatterns(issuesResults: Set[Issue], patterns: Seq[Pattern]) = {
    val requestedPatternIds = patterns.map(_.patternIdentifier)
    val (filteredPatternResults, otherPatternsResults) = issuesResults.partition { result =>
      requestedPatternIds.contains(result.patternId.value)
    }

    if (otherPatternsResults.nonEmpty) {
      Printer.red(s"Some results returned were not requested by the test and were discarded!")
      Printer.white(
        s"""
           |Extra results returned:
           |* ${otherPatternsResults.map(_.patternId.value).mkString(", ")}
           |
           |Check the results returned:
           |  * The tool should only return results requested in the configuration
           |  * The results patternIds should match the names listed in the tools /docs/patterns.json
         """.stripMargin)
    }
    filteredPatternResults
  }

  private def filterResultsFromFiles(issuesResults: Set[Issue], files: Seq[File], sourcePath: Path) = {
    val relativeFiles = files.map(file => sourcePath.relativize(file.getAbsoluteFile.toPath).toString)
    val (filteredFileResults, otherFilesResults) = issuesResults.partition { result =>
      relativeFiles.contains(result.filename.toString)
    }

    if (otherFilesResults.nonEmpty) {
      Printer.red(s"Some results are not in the files requested and were discarded!")
      Printer.white(
        s"""
           |Extra files:
           |  * ${otherFilesResults.map(_.filename).mkString(", ")}
           |
           |Check the paths returned:
           |  * The tool should only return results for the files requested
           |  * The files should be relative to /src (ex: /src/dir/file.js -> dir/file.js)
         """.stripMargin)
    }
    filteredFileResults
  }

  private def filterFileErrors(results: Set[ToolResult]) = {
    val (issuesResults: Set[Issue], fileErrorsResults: Set[FileError]) = results.foldLeft(Set.empty[Issue], Set.empty[FileError]) {
      case ((issues, fileErrors), res) =>
        res match {
          case issue: Issue => (issues + issue, fileErrors)
          case fileError: FileError => (issues, fileErrors + fileError)
        }
    }

    if (fileErrorsResults.nonEmpty) {
      Printer.red(s"Some files were not analysed because the tool failed analysing them!")
      Printer.white(fileErrorsResults.map(fe => s"* File: ${fe.filename}, Error: ${fe.message}").mkString("\n"))
    }
    issuesResults
  }
}
