package codacy.plugins.docker

import codacy.plugins.traits.JsonEnumeration
import play.api.libs.json.{Format, Json}

case class PluginRequest(directory: String, files: Seq[String], configuration: PluginConfiguration)

object PluginRequest {
  implicit val patternFmt: Format[Pattern] = Json.format[Pattern]
  implicit val configurationFmt: Format[PluginConfiguration] = Json.format[PluginConfiguration]
  implicit val requestFmt: Format[PluginRequest] = Json.format[PluginRequest]
}

case class Pattern(patternIdentifier: String, parameters: Map[String, String])

case class PluginConfiguration(patterns: Seq[Pattern])

object ResultLevel extends Enumeration with JsonEnumeration {
  val Err = Value("Error")
  val Warn = Value("Warning")
  val Info = Value("Info")
}

case class Result(patternIdentifier: String, filename: String, line: Int, message: String, level: ResultLevel.Value)

case class PluginResult(results: Seq[Result], failedFiles: Seq[String])

case object Language extends Enumeration with JsonEnumeration {
  val Scala, Javascript, CoffeeScript, CSS, PHP, C, CPP, ObjectiveC, Python, Ruby, Perl, Java, CSharp, VisualBasic = Value

  def getExtensions(value: Value): Seq[String] = {
    value match {
      case Scala => Seq(".scala")
      case Javascript => Seq(".js")
      case CoffeeScript => Seq(".coffee")
      case CSS => Seq(".css")
      case PHP => Seq(".php")
      case C => Seq(".c", ".h")
      case CPP => Seq(".cpp", ".hpp")
      case ObjectiveC => Seq(".m")
      case Python => Seq(".py")
      case Ruby => Seq(".rb")
      case Perl => Seq(".pl")
      case Java => Seq(".java")
      case CSharp => Seq(".cs")
      case VisualBasic => Seq(".vb")
      case _ => Seq.empty
    }
  }
}

object CategoryType extends Enumeration with JsonEnumeration {
  val Security, CodeStyle, ErrorProne, Performance, Compatibility, Documentation, UnusedCode,

  //Deprecated
  Complexity, BestPractice, Comprehensibility, Duplication = Value

  def fromString(s: String): Option[Value] =
    values.find(_.toString == s)
}
