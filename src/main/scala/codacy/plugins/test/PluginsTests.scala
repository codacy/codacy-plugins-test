package codacy.plugins.test

import java.nio.file.{Path, Paths}

import codacy.utils.{FileHelper, Printer}
import com.codacy.analysis.core
import com.codacy.analysis.core.model.{CodacyCfg, Issue, Pattern}
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.traits.BinaryDockerRunner
import com.codacy.plugins.utils.BinaryDockerHelper

object PluginsTests extends ITest {

  val opt = "plugin"

  def run(testSources: Seq[Path], dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    Printer.green("Running PluginsTests:")

    val languages = findLanguages(testSources, dockerImage)
    val dockerTool = createDockerTool(languages, dockerImage)
    val dockerToolDocumentation = new DockerToolDocumentation(dockerTool, new BinaryDockerHelper(useCachedDocs = false))
    val specOpt = dockerToolDocumentation.spec
    val dockerRunner = new BinaryDockerRunner[Result](dockerTool)
    val runner = new ToolRunner(dockerTool, dockerToolDocumentation, dockerRunner)

    specOpt.forall { spec =>
      Printer.green(s"  + ${spec.name} should find results for all patterns")

      val patterns: Set[Pattern] = spec.patterns.map(
        p =>
          core.model.Pattern(p.patternId.value, p.parameters.fold(Set.empty[core.model.Parameter])(_.map {
            parameterSpec =>
              core.model.Parameter(parameterSpec.name.toString(), parameterSpec.default.toString)
          }(collection.breakOut)))
      )(collection.breakOut)
      val codacyCfg = CodacyCfg(patterns)

      val tools = languages.map(new core.tools.Tool(runner, dockerRunner.defaultRunTimeout)(dockerTool, _))

      val resultsUUIDS: Set[String] = testSources.flatMap { sourcePath =>
        val files = FileHelper.listFiles(sourcePath.toFile)
        val fileAbsolutePaths: Set[Path] = files.map(file => Paths.get(file.getAbsolutePath))(collection.breakOut)

        val filteredResults: Set[Issue] = tools.flatMap { tool =>
          val results = tool.run(better.files.File(sourcePath.toAbsolutePath), fileAbsolutePaths, codacyCfg)
          filterResults(None, sourcePath, files, patterns.to[Seq], results)
        }(collection.breakOut)

        filteredResults.map(_.patternId.value)
      }(collection.breakOut)

      val missingPatterns = patterns.map(_.id).diff(resultsUUIDS)

      if (missingPatterns.nonEmpty) {
        Printer.red(s"""
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
