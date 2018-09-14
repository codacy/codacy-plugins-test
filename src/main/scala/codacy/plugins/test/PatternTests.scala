package codacy.plugins.test

import java.io.File
import java.nio.file.{Path, Paths}

import codacy.utils.Printer
import com.codacy.analysis.core
import com.codacy.analysis.core.model.{CodacyCfg, FullLocation, LineLocation, Pattern}
import com.codacy.analysis.core.tools.Tool
import com.codacy.plugins.api._

import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
import scala.util.Try


object PatternTests extends ITest with CustomMatchers {

  val opt = "pattern"

  def run(specOpt: Option[results.Tool.Specification], testSources: Seq[Path],
          dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    Printer.green(s"Running PatternsTests:")
    testSources.map { sourcePath =>
      val testFiles = new TestFilesParser(sourcePath.toFile).getTestFiles

      val filteredTestFiles = optArgs.headOption.fold(testFiles) {
        fileNameToTest => testFiles.filter(testFiles => testFiles.file.getName.contains(fileNameToTest))
      }

      val tools = DockerHelpers.findTools(testFiles, dockerImage)

      val testFilesPar = sys.props.get("codacy.tests.threads").flatMap(nrt => Try(nrt.toInt).toOption)
        .map { nrThreads =>
          val filesPar = filteredTestFiles.par
          filesPar.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(nrThreads))
          filesPar
        }.getOrElse(filteredTestFiles)

      testFilesPar.map { testFile =>
        tools.exists(analyseFile(specOpt, sourcePath.toFile, testFile, _))
      }.forall(identity)
    }.forall(identity)
  }

  private def analyseFile(spec: Option[results.Tool.Specification], rootDirectory: File, testFile: PatternTestFile, tool: Tool): Boolean = {

    val testFilePath = testFile.file.getAbsolutePath
    val filename = toRelativePath(rootDirectory.getAbsolutePath, testFilePath)

    Printer.green(s"- $filename should have ${testFile.matches.length} matches with patterns: " +
      testFile.enabledPatterns.map(_.name).mkString(", "))


    val testFiles = Seq(testFile.file)
    val testFilesAbsolutePaths = testFiles.map(f => Paths.get(f.getAbsolutePath))

    val configuration = DockerHelpers.toPatterns(testFile.enabledPatterns)

    val patterns: Set[Pattern] = configuration.map(p => core.model.Pattern(p.patternIdentifier,
      p.parameters.fold(Set.empty[core.model.Parameter])(_.map {
        case (k, v) => core.model.Parameter(k, v.toString)
      }(collection.breakOut))))(collection.breakOut)

    val codacyCfg = CodacyCfg(patterns)

    val result = tool.run(better.files.File(rootDirectory.getAbsolutePath), testFilesAbsolutePaths.to[Set], codacyCfg)

    val filteredResults = filterResults(spec, rootDirectory.toPath, testFiles, configuration, result)

    val matches = filteredResults.map(r => TestFileResult(r.patternId.value,
      r.location match {
        case fl: FullLocation => fl.line
        case l: LineLocation => l.line
      }, r.level))

    val comparison = beEqualTo(testFile.matches).apply(matches.to[Seq])

    Printer.green(s"  + ${matches.size} matches found in lines: ${matches.map(_.line).to[Seq].sorted.mkString(", ")}")

    if (!comparison.matches) Printer.red(comparison.rawFailureMessage)
    else Printer.green(comparison.rawNegatedFailureMessage)

    comparison.matches
  }

  private def toRelativePath(rootPath: String, absolutePath: String) = {
    absolutePath.stripPrefix(rootPath).stripPrefix(File.separator)
  }

}
