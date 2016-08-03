package codacy.docker.api

import play.api.libs.json._
import scala.language.implicitConversions

private[api] case class ParamValue(value:JsValue) extends AnyVal with Parameter.Value

trait JsonApi {

  def enumWrites[E <: Enumeration#Value]: Writes[E] = Writes((e: E) => Json.toJson(e.toString))

  def enumReads[E <: Enumeration](e: E): Reads[e.Value] = {
    Reads.StringReads.flatMap { case value => Reads((_: JsValue) =>
      e.values.collectFirst { case enumValue if enumValue.toString == value =>
        JsSuccess(enumValue)
      }.getOrElse(JsError(s"Invalid enumeration value $value"))
    )
    }
  }

  implicit def paramValueToJsValue(paramValue:Parameter.Value): JsValue = {
    paramValue match {
      case ParamValue(v) => v
      case _ => JsNull
    }
  }

  implicit lazy val parameterValueFormat: Format[Parameter.Value] = Format(
    implicitly[Reads[JsValue]].map( Parameter.Value ),
    Writes( paramValueToJsValue )
  )

  implicit lazy val resultLevelFormat = Format(
    enumReads(Result.Level),
    enumWrites[Result.Level]
  )
  implicit lazy val patternCategoryFormat = Format(
    enumReads(Pattern.Category),
    enumWrites[Pattern.Category]
  )

  implicit lazy val patternIdFormat = Format(Reads.StringReads.map(Pattern.Id),
    Writes((v: Pattern.Id) => Json.toJson(v.value))) //Json.format[Pattern.Id]

  implicit lazy val errorMessageFormat = Format(Reads.StringReads.map(ErrorMessage),
    Writes((v: ErrorMessage) => Json.toJson(v.value))) //Json.format[ErrorMessage]

  implicit lazy val resultMessageFormat = Format(Reads.StringReads.map(Result.Message),
    Writes((v: Result.Message) => Json.toJson(v.value))) //Json.format[Result.Message]

  implicit lazy val resultLineFormat = Format(Reads.IntReads.map(Source.Line),
    Writes((v: Source.Line) => Json.toJson(v.value))) //Json.format[Result.Line]

  implicit lazy val parameterNameFormat = Format(Reads.StringReads.map(Parameter.Name),
    Writes((v: Parameter.Name) => Json.toJson(v.value))) //Json.format[Parameter.Name]

  implicit lazy val toolNameFormat = Format(Reads.StringReads.map(Tool.Name),
    Writes((v: Tool.Name) => Json.toJson(v.value))) //Json.format[Tool.Name]

  implicit lazy val sourceFileFormat = Format(Reads.StringReads.map(Source.File),
    Writes((v: Source.File) => Json.toJson(v.path))) //Json.format[Source.File]

  implicit lazy val parameterSpecificationFormat = Json.format[Parameter.Specification]
  implicit lazy val parameterDefinitionFormat = Json.format[Parameter.Definition]
  implicit lazy val patternDefinitionFormat = Json.format[Pattern.Definition]
  implicit lazy val patternSpecificationFormat = Json.format[Pattern.Specification]
  implicit lazy val toolConfigurationFormat = Json.format[Tool.Configuration]
  implicit lazy val specificationFormat = Json.format[Tool.Specification]
  implicit lazy val configurationFormat = Json.format[Configuration]

  implicit lazy val resultWrites: Writes[Result] = Writes[Result]((_: Result) match {
    case r: Result.Issue => Json.writes[Result.Issue].writes(r)
    case e: Result.FileError => Json.writes[Result.FileError].writes(e)
  })

  implicit lazy val resultReads: Reads[Result] = {
    //check issue then error then oldResult
    issueFormat.map(identity[Result]) orElse
      errorFormat.map(identity[Result]) orElse
      oldResultReads
  }

  //old formats still out there...
  private[this] implicit lazy val errorFormat = Json.format[Result.FileError]
  private[this] implicit lazy val issueFormat = Json.format[Result.Issue]

  private[this] sealed trait ToolResult

  private[this] case class Issue(filename: String, message: String, patternId: String, line: Int) extends ToolResult

  private[this] case class FileError(filename: String, message: Option[String]) extends ToolResult

  private[this] lazy val oldResultReads: Reads[Result] = {

    lazy val IssueReadsName = Issue.getClass.getSimpleName
    lazy val issueReads = Json.reads[Issue].map(identity[ToolResult])

    lazy val ErrorReadsName = FileError.getClass.getSimpleName
    lazy val errorReads = Json.reads[FileError].map(identity[ToolResult])

    Reads { (result: JsValue) =>
      (result \ "type").validate[String].flatMap {
        case IssueReadsName => issueReads.reads(result)
        case ErrorReadsName => errorReads.reads(result)
        case tpe => JsError(s"not a valid result type $tpe")
      }
    }.orElse(issueReads).orElse(errorReads).map {
      case Issue(filename, message, patternId, line) =>
        Result.Issue(Source.File(filename), Result.Message(message), Pattern.Id(patternId), Source.Line(line))
      case FileError(filename, messageOpt) =>
        Result.FileError(Source.File(filename), messageOpt.map(ErrorMessage))
    }
  }
}

object JsonApi extends JsonApi