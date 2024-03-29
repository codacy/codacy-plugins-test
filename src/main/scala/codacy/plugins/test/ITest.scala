package codacy.plugins.test

import better.files._
import com.codacy.analysis.core.model.{FileError, Issue, ParameterSpec, Pattern, PatternSpec, ToolResult, ToolSpec}
import com.codacy.analysis.core.tools.FullToolSpec
import com.codacy.plugins.api
import com.codacy.plugins.api._
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.results.traits.DockerToolDocumentation
import wvlet.log.LogSupport

import java.io.{File => JFile}
import java.nio.file.Path

final case class DockerImage(name: String, version: String) {
  override def toString: String = {
    s"$name:$version"
  }
}

trait ITest extends LogSupport {
  val opt: String

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean

  protected def findLanguages(testsDirectory: JFile): Set[Language] = {
    val languagesFromProperties =
      sys.props.get("codacy.tests.languages").map(_.split(",").flatMap(Languages.fromName).to[Set])

    lazy val languagesFromFiles: Set[Language] = (for {
      testFile <- new TestFilesParser(testsDirectory).getTestFiles
      language <- Languages.fromName(testFile.language.toString)
    } yield language)(collection.breakOut)

    languagesFromProperties.getOrElse(languagesFromFiles)
  }

  protected def createFullToolSpec(toolSpec: api.results.Tool.Specification,
                                   dockerToolDocumentation: DockerToolDocumentation,
                                   languages: Set[Language],
                                   dockerImage: DockerImage): FullToolSpec = {
    val patternDescriptions = dockerToolDocumentation.patternDescriptions.getOrElse(Set.empty)
    val patterns = toolSpec.patterns
      .flatMap { pattern =>
        patternDescriptions
          .find(_.patternId == pattern.patternId)
          .map { patternDescription =>
            merge(pattern, patternDescription)
          }
      }(collection.breakOut)

    FullToolSpec(toToolSpec(toolSpec, languages, dockerImage), patterns)
  }

  private def toToolSpec(toolSpec: api.results.Tool.Specification,
                         languages: Set[Language],
                         dockerImage: DockerImage): ToolSpec = {
    ToolSpec(toolSpec.name.value,
             dockerImage.toString,
             isDefault = true,
             dockerImage.version,
             languages,
             toolSpec.name.value,
             toolSpec.name.value,
             Option.empty,
             Option.empty,
             prefix = "",
             needsCompilation = false,
             hasConfigFile = true,
             Set.empty,
             standalone = false,
             hasUIConfiguration = true)
  }

  private def merge(p: results.Pattern.Specification, pd: PatternDescription): PatternSpec = {
    val parameters = p.parameters.map { pp =>
      val description =
        pd.parameters.getOrElse(Set.empty).find(_.name.value == pp.name.value).map(_.description)
      ParameterSpec(pp.name.value, pp.default.toString(), description)
    }(collection.breakOut)

    PatternSpec(p.patternId.toString(),
                p.level.toString(),
                p.category.toString(),
                p.subcategory.map(_.toString()),
                pd.title,
                pd.description,
                pd.explanation,
                p.enabled,
                pd.timeToFix,
                parameters,
                p.languages)
  }

  protected def filterResults(spec: Option[results.Tool.Specification],
                              sourcePath: Path,
                              files: Seq[JFile],
                              patterns: Seq[Pattern],
                              toolResults: Set[ToolResult]): Set[Issue] = {
    val filtered = filterFileErrors(toolResults)
    val filteredFromSpec = filterResultsFromSpecPatterns(filtered, spec)

    val filteredFromFiles = filterResultsFromFiles(filteredFromSpec, files, sourcePath)
    val filteredFromPatterns = filterResultsFromPatterns(filteredFromSpec, patterns)
    filteredFromFiles.intersect(filteredFromPatterns)
  }

  private def filterResultsFromSpecPatterns(issuesResults: Set[Issue], specOpt: Option[results.Tool.Specification]) = {
    specOpt.fold(issuesResults) { spec =>
      val specPatternIds: Set[results.Pattern.Id] = spec.patterns.map(_.patternId)
      issuesResults.filter(issue => specPatternIds.contains(issue.patternId))
    }
  }

  private def filterResultsFromPatterns(issuesResults: Set[Issue], patterns: Seq[Pattern]) = {
    val (filteredPatternResults, otherPatternsResults) = issuesResults.partition { result =>
      patterns.map(_.id).contains(result.patternId.value)
    }

    if (otherPatternsResults.nonEmpty) {
      error(s"Some results returned were not requested by the test and were discarded!")
      info(s"""
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

  private def filterResultsFromFiles(issuesResults: Set[Issue], files: Seq[JFile], sourcePath: Path) = {
    val relativeFiles = files.map(file => sourcePath.relativize(file.getAbsoluteFile.toPath).toString)
    val (filteredFileResults, otherFilesResults) = issuesResults.partition { result =>
      relativeFiles.contains(result.filename.toString)
    }

    if (otherFilesResults.nonEmpty) {
      error(s"Some results are not in the files requested and were discarded!")
      info(s"""
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
    val (issuesResults: Set[Issue], fileErrorsResults: Set[FileError]) =
      results.foldLeft((Set.empty[Issue], Set.empty[FileError])) {
        case ((issues, fileErrors), res) =>
          res match {
            case issue: Issue => (issues + issue, fileErrors)
            case fileError: FileError => (issues, fileErrors + fileError)
          }
      }

    if (fileErrorsResults.nonEmpty) {
      error(s"Some files were not analysed because the tool failed analysing them!")
      info(fileErrorsResults.map(fe => s"* File: ${fe.filename}, Error: ${fe.message}").mkString("\n"))
    }
    issuesResults
  }

  protected def multipleDirectories(testsDirectory: File, optArgs: Seq[String]) = {
    val selectedTest = optArgs.sliding(2).collectFirst {
      case Seq("--only", multipleTestDir) =>
        multipleTestDir
    }

    selectedTest match {
      case Some(dirName) => Seq(testsDirectory / dirName)
      case None => testsDirectory.list.filter(_.isDirectory).toSeq
    }
  }
}
