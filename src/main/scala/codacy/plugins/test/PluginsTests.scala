package codacy.plugins.test

import java.nio.file.{Path, Paths}

import codacy.plugins.docker
import codacy.plugins.docker.DockerPlugin
import codacy.utils.{FileHelper, Printer}
import com.codacy.analysis.core
import com.codacy.analysis.core.model.{CodacyCfg, Issue, Pattern}
import com.codacy.analysis.core.tools.Tool
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.results.traits.DockerTool

object PluginsTests extends ITest {

  val opt = "plugin"

  def run(plugin: DockerPlugin, testSources: Seq[Path], dockerImageNameAndVersion: String, optArgs: Seq[String]): Boolean = {
    Printer.green("Running PluginsTests:")
    plugin.spec.forall { spec =>
      Printer.green(s"  + ${spec.name} should find results for all patterns")

      val (dockerImageName, dockerVersion) = dockerImageNameAndVersion.split(":") match {
        case Array(name, version) => (name, version)
        case Array(name) => (name, "latest")
        case _ => throw new RuntimeException("Invalid Docker Name.")
      }

      val patterns: Seq[docker.Pattern] = DockerHelpers.toPatterns(spec)
      val modelPatterns: Set[Pattern] = patterns.map(p => core.model.Pattern(p.patternIdentifier,
        p.parameters.fold(Set.empty[core.model.Parameter])(_.map {
          case (name, value) => core.model.Parameter(name, value.toString)
        }(collection.breakOut))))(collection.breakOut)
      val codacyCfg = CodacyCfg(modelPatterns)

      val resultsUUIDS = testSources.flatMap { sourcePath =>
        val testFiles = new TestFilesParser(sourcePath.toFile).getTestFiles

        val languages: Set[Language] = testFiles.flatMap(testFile => Languages.fromName(testFile.language.toString))(collection.breakOut)

        val dockerTool = new DockerTool(dockerName = dockerImageName, true, languages,
          dockerImageName, dockerImageName, dockerImageName, "", "", "", needsCompilation = false,
          needsPatternsToRun = false, hasUIConfiguration = true) {
          override lazy val toolVersion: Option[String] = Some(dockerVersion)
          override val dockerTag: String = dockerVersion
          override val dockerImageName: String = dockerName
        }

        val tools: Set[Tool] = languages.map {
          language => new Tool(dockerTool, language)
        }(collection.breakOut)

        val files = FileHelper.listFiles(sourcePath.toFile)
        val fileAbsolutePaths = files.map(_.getAbsolutePath)

        val filteredResults: Set[Issue] = tools.flatMap {
          tool =>
            val results = tool.run(better.files.File(sourcePath.toAbsolutePath), fileAbsolutePaths.map(f => Paths.get(f))(collection.breakOut), codacyCfg)
            filterResults(None, sourcePath, files, patterns, results)
        }(collection.breakOut)

        filteredResults.map(_.patternId.value)
      }

      val missingPatterns = patterns.map(_.patternIdentifier).diff(resultsUUIDS)

      if (missingPatterns.nonEmpty) {
        Printer.red(
          s"""
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
