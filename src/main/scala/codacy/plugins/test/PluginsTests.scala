package codacy.plugins.test

import java.io.{File => JFile}
import java.nio.file.{Path, Paths}

import scala.util.{Failure, Success, Try}

import better.files._
import codacy.plugins.test.Utils.exceptionToString
import codacy.utils.FileHelper
import com.codacy.analysis.core
import com.codacy.analysis.core.model.{CodacyCfg, Issue, Pattern}
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.{DockerToolDocumentation, ToolRunner}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.BinaryDockerHelper

object PluginsTests extends ITest {

  val opt = "plugin"

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    debug("Running PluginsTests:")
    val testsDirectory = docsDirectory.toScala / DockerHelpers.testsDirectoryName

    val languages = findLanguages(testsDirectory.toJava, dockerImage)
    val dockerTool = createDockerTool(languages, dockerImage)

    val dockerRunner = new BinaryDockerRunner[Result](dockerTool)
    val dockerToolDocumentation = new DockerToolDocumentation(dockerTool, new BinaryDockerHelper())

    val specOpt = dockerToolDocumentation.toolSpecification
    val runner =
      new ToolRunner(dockerToolDocumentation.toolSpecification, dockerToolDocumentation.toolPrefix, dockerRunner)

    specOpt.forall { spec =>
      debug(s"  + ${spec.name} should find results for all patterns")

      val patterns: Set[Pattern] = spec.patterns.map(
        p =>
          core.model.Pattern(p.patternId.value, p.parameters.map { parameterSpec =>
            core.model.Parameter(parameterSpec.name.toString(), parameterSpec.default.toString)
          }(collection.breakOut))
      )(collection.breakOut)
      val codacyCfg = CodacyCfg(patterns)

      val tools = languages.map(new core.tools.Tool(runner, DockerRunner.defaultRunTimeout)(dockerTool, _))

      val resultsUUIDSTry: Try[Set[String]] = {
        val files = FileHelper.listFiles(testsDirectory.toJava)
        val fileAbsolutePaths: Set[Path] = files.map(file => Paths.get(file.getAbsolutePath))(collection.breakOut)

        val filteredResults: Try[Set[Issue]] = {
          val setOfTryOfSets = tools.map { tool =>
            val resultsTry = tool.run(better.files.File(testsDirectory.pathAsString), fileAbsolutePaths, codacyCfg)
            resultsTry.map(results => filterResults(None, testsDirectory.path, files, patterns.to[Seq], results))
          }
          setOfTryOfSets.fold(Success(Set.empty[Issue])) { (acc, resultsTry) =>
            acc.flatMap(set => resultsTry.map(set.union(_)))
          }
        }

        filteredResults.map(_.map(_.patternId.value))
      }

      val missingPatternsTry = resultsUUIDSTry.map(resultsUUIDS => patterns.map(_.id).diff(resultsUUIDS))
      missingPatternsTry match {
        case Success(missingPatterns) =>
          if (missingPatterns.nonEmpty) {
            error(s"""
                       |Some patterns are not tested on plugin ${spec.name}
                       |-> Missing patterns:
                       |${missingPatterns.mkString(", ")}
              """.stripMargin)
            false
          } else {
            debug("All the patterns have occurrences in the test files.")
            true
          }
        case Failure(e) =>
          error(s"Error happened launching the tool: ${exceptionToString(e)}")
          false
      }
    }
  }
}
