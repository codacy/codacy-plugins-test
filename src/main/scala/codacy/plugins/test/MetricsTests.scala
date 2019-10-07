package codacy.plugins.test

import java.io.File
import java.nio.file.Path

import codacy.utils.Printer
import com.codacy.analysis.core
import com.codacy.analysis.core.tools.MetricsTool
import com.codacy.plugins.api.Source
import com.codacy.plugins.metrics.traits

object MetricsTests extends ITest with CustomMatchers {

  val opt = "metrics"

  def run(testSources: Seq[Path], dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    Printer.green(s"Running MetricsTests:")

    val languages = findLanguages(testSources, dockerImage)
    val metricsTool = new traits.MetricsTool(languages.toList, dockerImage.name, dockerImage.version) {}
    val tools = languages.map(language => new core.tools.MetricsTool(metricsTool, language))

    testSources
      .map { sourcePath =>
        val testFiles = new TestFilesParser(sourcePath.toFile).getTestFiles

        val filteredTestFiles = optArgs.headOption.fold(testFiles) { fileNameToTest =>
          testFiles.filter(testFiles => testFiles.file.getName.contains(fileNameToTest))
        }

        filteredTestFiles
          .map { testFile =>
            tools
              .filter(_.languageToRun.name.equalsIgnoreCase(testFile.language.toString))
              .exists(analyseFile(sourcePath.toFile, testFile, _))
          }
          .forall(identity)
      }
      .forall(identity)
  }

  private def analyseFile(rootDirectory: File, testFile: PatternTestFile, tool: MetricsTool): Boolean = {

    val filename = toRelativePath(rootDirectory.getAbsolutePath, testFile.file.getAbsolutePath)

    Printer.green(
      s"- $filename should have ${testFile.matches.length} matches with patterns: " +
        testFile.enabledPatterns.map(_.name).mkString(", ")
    )

    val testFiles = Seq(testFile.file)
    val testFilesSourcePaths: Set[Source.File] = testFiles.map(f => Source.File(f.getAbsolutePath))(collection.breakOut)

    val patterns: Set[Pattern] = testFile.enabledPatterns.map(
      p =>
        core.model.Pattern(p.name, p.parameters.fold(Set.empty[core.model.Parameter])(_.map {
          case (k, v) => core.model.Parameter(k, v.toString)
        }(collection.breakOut)))
    )(collection.breakOut)

    val codacyCfg = CodacyCfg(patterns)

    val result = tool.run(better.files.File(rootDirectory.getAbsolutePath), Option(testFilesSourcePaths), codacyCfg)

    val matches: Seq[TestFileResult] = result.map(
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
