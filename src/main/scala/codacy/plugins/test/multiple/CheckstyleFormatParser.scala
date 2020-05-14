package codacy.plugins.test.multiple

import com.codacy.analysis.core.model.{Parameter, Pattern}
import com.codacy.plugins.api.results.Result.Level

import play.api.libs.json.Json

import com.fasterxml.jackson.core.JsonParseException
import scala.xml.Elem
import com.codacy.analysis.core.model.{FileError, Issue, ToolResult}
import java.nio.file.Paths
import com.codacy.analysis.core.model.LineLocation
import com.codacy.plugins.api

private[multiple] object CheckstyleFormatParser {

  def parseResultsXml(root: Elem): Seq[ToolResult] = {
    for {
      fileTag <- root \\ "checkstyle" \ "file"
      filePath = Paths.get(fileTag \@ "name")
      errorsTag <- fileTag \ "error"
      message = errorsTag \@ "message"
      lineAttr = errorsTag \ "@line"
      patternIdAttr = errorsTag \ "@source"
      severityAttr = errorsTag \ "@severity"
      result = if (patternIdAttr.isEmpty && severityAttr.isEmpty) {
        FileError(filePath, message)
      } else if (patternIdAttr.nonEmpty && severityAttr.nonEmpty) {
        val line = lineAttr.text.toInt
        val severity = severityAttr.text
        val patternId = patternIdAttr.text
        val level = severity match {
          case "info" => Level.Info
          case "warning" => Level.Warn
          case "error" => Level.Err
          case _ => throw new Exception(s"""$severity is not a valid level. Use one of ["info", "warning", "error"]""")
        }
        Issue(api.results.Pattern.Id(patternId), filePath, Issue.Message(message), level, None, LineLocation(line))
      } else {
        throw new Exception("""Errors should be either results or file errors:
                              |Example result:
                              |  <error source="pattern_id" line="1" message="Message from the tool." severity="info" />
                              |Example file error:
                              |  <error message="Error message" />""".stripMargin)
      }
    } yield result
  }

  def parsePatternsXml(root: Elem): (Set[Pattern], Option[Map[String, play.api.libs.json.JsValue]], Option[String]) = {
    val extraValues = (root \ "property").map { node =>
      val v = node \@ "value"
      val value = try {
        Json.parse(v)
      } catch {
        case _: JsonParseException => // support non quoted strings
          Json.parse(s""""$v"""")
      }
      (node \@ "name", value)
    }.toMap
    val patternsList = for {
      rootChildren <- root \ "module"
      if rootChildren \@ "name" != "BeforeExecutionExclusionFileFilter"
      patternId: String = rootChildren \@ "name"
      parameters = (rootChildren \ "property").map { node =>
        Parameter(node \@ "name", node \@ "value")
      }.toSet
    } yield Pattern(patternId, parameters)

    val excludedFilesRegex: Option[String] = {
      val rootChildren = root \ "module"
      val fileFilterOption = rootChildren.find(_ \@ "name" == "BeforeExecutionExclusionFileFilter")
      fileFilterOption.map { fileFilter =>
        val property = fileFilter \ "property"
        if (property \@ "name" == "fileNamePattern")
          property \@ "value"
        else
          throw new Exception(
            """"BeforeExecutionExclusionFileFilter" module should have a "property" tag with name="fileNamePattern"
              |Example:
              |<module name="BeforeExecutionExclusionFileFilter">
              |  <property name="fileNamePattern" value="module\-info\.java$"/>
              |</module>""".stripMargin
          )
      }
    }
    (patternsList.toSet, if (extraValues.isEmpty) None else Some(extraValues), excludedFilesRegex)
  }
}
