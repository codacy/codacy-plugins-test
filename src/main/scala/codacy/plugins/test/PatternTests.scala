package codacy.plugins.test

import java.io.File
import java.nio.file.{Path, Paths}
// import java.util.concurrent.ForkJoinPool

import codacy.utils.Printer
import com.codacy.analysis.core
import com.codacy.analysis.core.model.{CodacyCfg, FullLocation, LineLocation, Pattern}
import com.codacy.analysis.core.tools.Tool
import com.codacy.plugins.api._
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.BinaryDockerHelper

// import scala.collection.parallel.ForkJoinTaskSupport
// import scala.util.Try

object PatternTests extends ITest with CustomMatchers {

  val opt = "pattern"

  def run(testDirectories: Seq[Path], dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    Printer.green(s"Running PatternsTests:")
    val testSources = testDirectories.filter(_.getFileName.toString == DockerHelpers.testsDirectoryName)
    val languages = findLanguages(testSources, dockerImage)
    val dockerTool = createDockerTool(languages, dockerImage)
    val toolDocumentation = new DockerToolDocumentation(dockerTool, new BinaryDockerHelper(useCachedDocs = false))
    val dockerRunner = new BinaryDockerRunner[Result](dockerTool)()
    val runner = new ToolRunner(dockerTool, toolDocumentation, dockerRunner)
    val tools = languages.map(new core.tools.Tool(runner, DockerRunner.defaultRunTimeout)(dockerTool, _))
    testSources
      .forall { sourcePath =>
        val testFiles = new TestFilesParser(sourcePath.toFile).getTestFiles

        val filteredTestFiles = optArgs.headOption.fold(testFiles) { fileNameToTest =>
          testFiles.filter(testFiles => testFiles.file.getName.contains(fileNameToTest))
        }

        filteredTestFiles.par
          .forall { testFile =>
            tools
              .filter(_.languageToRun.name.equalsIgnoreCase(testFile.language.toString))
              .exists(analyseFile(toolDocumentation.spec, sourcePath.toFile, testFile, _))
          }
      }
  }

  private def analyseFile(spec: Option[results.Tool.Specification],
                          rootDirectory: File,
                          testFile: PatternTestFile,
                          tool: Tool): Boolean = {

    val filename = toRelativePath(rootDirectory.getAbsolutePath, testFile.file.getAbsolutePath)

    Printer.green(
      s"- $filename should have ${testFile.matches.length} matches with patterns: " +
        testFile.enabledPatterns.map(_.name).mkString(", ")
    )

    val testFiles = Seq(testFile.file)
    val testFilesAbsolutePaths = testFiles.map(f => Paths.get(f.getAbsolutePath))

    val patterns: Set[Pattern] = testFile.enabledPatterns.map(
      p =>
        core.model.Pattern(p.name, p.parameters.fold(Set.empty[core.model.Parameter])(_.map {
          case (k, v) => core.model.Parameter(k, v.toString)
        }(collection.breakOut)))
    )(collection.breakOut)

    val codacyCfg = CodacyCfg(patterns)

    val result = tool.run(better.files.File(rootDirectory.getAbsolutePath), testFilesAbsolutePaths.to[Set], codacyCfg)
    println(result)
    val filteredResults = filterResults(spec, rootDirectory.toPath, testFiles, patterns.to[Seq], result)

    val matches: Seq[TestFileResult] = filteredResults.map(
      r =>
        TestFileResult(r.patternId.value, r.location match {
          case fl: FullLocation => fl.line
          case l: LineLocation => l.line
        }, r.level)
    )(collection.breakOut)

    val comparison = beEqualTo(testFile.matches).apply(matches)

    Printer.green(s"  + ${matches.size} matches found in lines: ${matches.map(_.line).to[Seq].sorted.mkString(", ")}")

    if (!comparison.matches) Printer.red(comparison.rawFailureMessage)
    else Printer.green(comparison.rawNegatedFailureMessage)

    comparison.matches
  }

  private def toRelativePath(rootPath: String, absolutePath: String) = {
    absolutePath.stripPrefix(rootPath).stripPrefix(File.separator)
  }
}
