package codacy.plugins.test

import better.files._

import com.codacy.analysis.core
import com.codacy.analysis.core.model.{CodacyCfg, FullLocation, Issue, LineLocation, Pattern}
import com.codacy.plugins.api._
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.BinaryDockerHelper
import java.nio.file.Paths
import java.io.{File => JFile}
import scala.util.{Failure, Success, Try}

object PatternTests extends ITest with CustomMatchers {

  val opt = "pattern"

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    debug(s"Running PatternsTests:")
    val testsDirectory = docsDirectory.toScala / DockerHelpers.testsDirectoryName
    val languages = findLanguages(testsDirectory.toJava, dockerImage)
    val dockerTool = createDockerTool(languages, dockerImage)
    val toolDocumentation = new DockerToolDocumentation(dockerTool, new BinaryDockerHelper(useCachedDocs = false))
    val dockerRunner = new BinaryDockerRunner[Result](dockerTool)()
    val runner = new ToolRunner(dockerTool, toolDocumentation, dockerRunner)
    val tools = languages.map(new core.tools.Tool(runner, DockerRunner.defaultRunTimeout)(dockerTool, _))

    val testFiles = new TestFilesParser(testsDirectory.toJava).getTestFiles

    val filteredTestFiles = optArgs.headOption.fold(testFiles) { fileNameToTest =>
      testFiles.filter(testFiles => testFiles.file.getName.contains(fileNameToTest))
    }

    val matchResultsAndComparisons = filteredTestFiles.par.flatMap { testFile =>
      tools
        .filter(_.languageToRun.name.equalsIgnoreCase(testFile.language.toString))
        .map { tool =>
          val analysisResultTry = analyseFile(toolDocumentation.spec, testsDirectory, testFile, tool)
          (testFile, analysisResultTry.map(matches => (matches, beEqualTo(testFile.matches).apply(matches))))
        }
    }.seq

    for ((testFile, matchResultsAndComparisonsTry) <- matchResultsAndComparisons) {
      val filename = testsDirectory.path.relativize(testFile.file.toPath())
      matchResultsAndComparisonsTry match {
        case Success((matches, comparison)) =>
          debug(s"""- $filename should have ${testFile.matches.length} matches with patterns: ${testFile.enabledPatterns
                     .map(_.name)
                     .mkString(", ")}
               |  + ${matches.size} matches found in lines: ${matches
                     .map(_.line)
                     .to[Seq]
                     .sorted
                     .mkString(", ")}""".stripMargin)
          if (!comparison.matches) error(comparison.rawFailureMessage)
          else debug(comparison.rawNegatedFailureMessage)
        case Failure(e) =>
          error(s"Error executing tool on $filename: ${e.getStackTraceString}")
      }
    }
    matchResultsAndComparisons.forall {
      case (testFile, matchResultsAndComparisonsTry) =>
        matchResultsAndComparisonsTry
          .map { case (matches, comparison) => comparison.matches }
          .getOrElse(false)
    }
  }

  private def analyseFile(spec: Option[results.Tool.Specification],
                          rootDirectory: File,
                          testFile: PatternTestFile,
                          tool: core.tools.Tool): Try[Seq[TestFileResult]] = {
    val testFiles = Seq(testFile.file)
    val testFilesAbsolutePaths = testFiles.map(f => Paths.get(f.getAbsolutePath))

    val patterns: Set[Pattern] = testFile.enabledPatterns.map(
      p =>
        core.model.Pattern(p.name, p.parameters.fold(Set.empty[core.model.Parameter])(_.map {
          case (k, v) => core.model.Parameter(k, v.toString)
        }(collection.breakOut)))
    )(collection.breakOut)

    val codacyCfg = CodacyCfg(patterns)

    val resultTry = tool.run(better.files.File(rootDirectory.pathAsString), testFilesAbsolutePaths.to[Set], codacyCfg)

    val filteredResultsTry: Try[Set[Issue]] =
      resultTry.map(result => filterResults(spec, rootDirectory.path, testFiles, patterns.to[Seq], result))

    filteredResultsTry.map(
      filteredResults =>
        filteredResults.map(
          r =>
            TestFileResult(r.patternId.value, r.location match {
              case fl: FullLocation => fl.line
              case l: LineLocation => l.line
            }, r.level)
        )(collection.breakOut)
    )
  }
}
