package codacy.plugins.test

import java.nio.file.Path

import codacy.utils.{CollectionHelper, Printer}
import com.codacy.plugins.api.PatternDescription
import com.codacy.plugins.api.results.Pattern
import com.codacy.plugins.results.traits.DockerToolDocumentation

object JsonTests extends ITest {

  val opt = "json"

  def run(testSources: Seq[Path], dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    Printer.green("Running JsonTests:")

    val ignoreDescriptions: Boolean =
      sys.props.get("codacy.tests.ignore.descriptions").isDefined || optArgs.contains("--ignore-descriptions")

    val dockerToolDocumentation: DockerToolDocumentation = readDockerToolDocumentation(testSources, dockerImage)

    dockerToolDocumentation.spec.fold(Printer.red("Could not read /docs/patterns.json successfully")) {
      _ => Printer.green("Read /docs/patterns.json successfully")
    }

    dockerToolDocumentation.descriptions.fold(Printer.red("Could not read /docs/description.json successfully")) {
      descriptions =>
        Printer.green("Read /docs/description/description.json successfully")
        descriptions.foreach { patternDescription =>
          patternDescription.explanation.fold(Printer.red(s"Could not read /docs/description/${patternDescription.patternId}.md")) {
            _ => Printer.green(s"Read /docs/description/${patternDescription.patternId}.md successfully")
          }
        }
    }

    (dockerToolDocumentation.spec, dockerToolDocumentation.descriptions) match {
      case (Some(tool), Some(descriptions)) =>
        val diffResult = new CollectionHelper[Pattern.Specification, PatternDescription, String](tool.patterns.toSeq, descriptions.toSeq)({
          pattern =>
            val parameters = pattern.parameters.getOrElse(Seq.empty).map(_.name.value).toSeq.sorted
            generateUniquePatternSignature(pattern.patternId.value, parameters)
        }, {
          description =>
            val parameters = description.parameters.getOrElse(Seq.empty).map(_.name.value).toSeq.sorted
            generateUniquePatternSignature(description.patternId.value, parameters)
        }).fastDiff

        val duplicateDescriptions = descriptions.groupBy(_.patternId).filter { case (_, v) => v.size > 1 }
        if (duplicateDescriptions.nonEmpty) {
          Printer.red(
            s"""
               |Some patterns were duplicated in /docs/description/description.json
               |
               |  * ${duplicateDescriptions.map { case (patternId, _) => patternId }.mkString(",")}
              """.stripMargin)
        }

        if (diffResult.newObjects.nonEmpty) {
          Printer.red(
            s"""
               |Some patterns were only found in /docs/patterns.json
               |Confirm that all the patterns and parameters present in /docs/patterns.json are also present in /docs/description/description.json
               |
               |  * ${diffResult.newObjects.map(_.patternId).mkString(",")}
              """.stripMargin)
        }

        if (diffResult.deletedObjects.nonEmpty) {
          Printer.red(
            s"""
               |Some patterns were only found in /docs/description/description.json
               |Confirm that all the patterns and parameters present in /docs/description/description.json are also present in /docs/patterns.json
               |
               |  * ${diffResult.deletedObjects.map(_.patternId).mkString(",")}
              """.stripMargin)
        }

        val titlesAboveLimit = descriptions.filter(_.title.length > 255)
        if (titlesAboveLimit.nonEmpty) {
          Printer.red(
            s"""
               |Some titles are too big in /docs/description/description.json
               |The max size of a title is 255 characters
               |
               | * ${titlesAboveLimit.map(_.patternId).mkString(", ")}
              """.stripMargin)
        }

        val descriptionsAboveLimit = descriptions.filter(_.description.getOrElse("").length > 500)
        if (descriptionsAboveLimit.nonEmpty) {
          Printer.red(
            s"""
               |Some descriptions are too big in /docs/description/description.json
               |The max size of a description is 500 characters
               |
               | * ${descriptionsAboveLimit.map(_.patternId).mkString(", ")}
              """.stripMargin)
        }

        ignoreDescriptions ||
          (diffResult.newObjects.isEmpty && diffResult.deletedObjects.isEmpty)

      case _ => ignoreDescriptions
    }
  }

  private def readDockerToolDocumentation(testSources: Seq[Path], dockerImage: DockerImage) = {
    val languages = findLanguages(testSources, dockerImage)
    val dockerTool = createDockerTool(languages, dockerImage)
    new DockerToolDocumentation(dockerTool)
  }

  private def generateUniquePatternSignature(patternName: String, parameters: Seq[String]) = {
    s"""$patternName:${parameters.mkString(",")}"""
  }
}
