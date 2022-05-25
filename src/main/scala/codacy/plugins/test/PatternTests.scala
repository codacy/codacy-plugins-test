package codacy.plugins.test

import better.files._
import codacy.plugins.test.Utils.exceptionToString
import com.codacy.analysis.core
import com.codacy.analysis.core.model._
import com.codacy.plugins.api._
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.BinaryDockerHelper

import java.io.{File => JFile}
import java.nio.file.Paths
import scala.util.{Failure, Success, Try}

object PatternTests extends ITest with CustomMatchers {

  val opt = "pattern"

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    debug(s"Running PatternsTests:")
    val testsDirectory = docsDirectory.toScala / DockerHelpers.testsDirectoryName
    val languages = findLanguages(testsDirectory.toJava)
    val dockerTool = createDockerTool(languages, dockerImage)
    val toolSpec = createToolSpec(languages, dockerImage)
    val dockerRunner = new BinaryDockerRunner[Result](dockerTool)
    val dockerToolDocumentation = new DockerToolDocumentation(dockerTool, new BinaryDockerHelper())

    val specOpt = dockerToolDocumentation.toolSpecification
    val runner =
      new ToolRunner(dockerToolDocumentation.toolSpecification, dockerToolDocumentation.toolPrefix, dockerRunner)
    val tools = languages.map(new core.tools.Tool(runner, DockerRunner.defaultRunTimeout)(toolSpec, _))

    val testFiles = new TestFilesParser(testsDirectory.toJava).getTestFiles

    val filteredTestFiles = optArgs.headOption.fold(testFiles) { fileNameToTest =>
      testFiles.filter(testFiles => testFiles.file.getName.contains(fileNameToTest))
    }

    val matchResultsAndComparisons = ParallelCollectionsUtils
      .toPar(filteredTestFiles)
      .flatMap { testFile =>
        info(s"Analysing ${testFile.file.getName()}")
        tools
          .filter(_.languageToRun.name.equalsIgnoreCase(testFile.language.toString))
          .map { tool =>
            val analysisResultTry = analyseFile(specOpt, testsDirectory, testFile, tool)
            (testFile, analysisResultTry.map(matches => (matches, beEqualTo(testFile.matches).apply(matches))))
          }
      }
      .seq

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
          error(s"Error executing tool on $filename: ${exceptionToString(e)}")
      }
    }
    matchResultsAndComparisons.forall {
      case (_, matchResultsAndComparisonsTry) =>
        matchResultsAndComparisonsTry
          .map { case (_, comparison) => comparison.matches }
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
