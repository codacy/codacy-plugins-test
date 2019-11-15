package codacy.plugins.test.multiple

import com.codacy.analysis.core.model.{Parameter, Pattern}
import com.codacy.plugins.api.results.Result.Level
import com.codacy.plugins.results.PluginResult

import play.api.libs.json.Json

import com.fasterxml.jackson.core.JsonParseException
import scala.xml.Elem

private[multiple] object CheckstyleFormatParser {

  def parseResultsXml(root: Elem) = {
    for {
      fileTag <- root \\ "checkstyle" \\ "file"
      fileName = fileTag \@ "name"
      errorsTag <- fileTag \\ "error"
      line = errorsTag \@ "line"
      patternId = errorsTag \@ "source"
      message = errorsTag \@ "message"
      severity = errorsTag \@ "severity"
      level = severity match {
        case "info" => Level.Info
        case "warning" => Level.Warn
        case "error" => Level.Err
        case _ => throw new Exception(s"$severity is not a valid level")
      }
    } yield PluginResult(patternId, fileName, line.toInt, message, level)
  }

  def parsePatternsXml(root: Elem): (Set[Pattern], Option[Map[String, play.api.libs.json.JsValue]]) = {
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
      patternTags <- root \ "module"
      patternId: String = patternTags \@ "name"
      parameters = (patternTags \ "property").map { node =>
        Parameter(node \@ "name", node \@ "value")
      }.toSet
    } yield Pattern(patternId, parameters)
    (patternsList.toSet, if (extraValues.isEmpty) None else Some(extraValues))
  }
}
