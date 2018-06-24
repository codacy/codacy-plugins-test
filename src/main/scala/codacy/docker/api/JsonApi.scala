package codacy.docker.api

import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.api.results.{Parameter, Pattern, Result, Tool}
import com.codacy.plugins.api.{ErrorMessage, Options, Source}
import play.api.libs.json._

import scala.language.implicitConversions

final private[api] case class ParamValue(value: JsValue) extends AnyVal with Parameter.Value
final private[api] case class OptionValue(value: JsValue) extends AnyVal with Options.Value

trait JsonApi {

  def enumWrites[E <: Enumeration#Value]: Writes[E] = Writes((e: E) => Json.toJson(e.toString))

  def enumReads[E <: Enumeration](e: E): Reads[e.Value] = {
    Reads.StringReads.flatMap { value =>
      Reads(
        (_: JsValue) =>
          e.values
            .collectFirst {
              case enumValue if enumValue.toString == value =>
                JsSuccess(enumValue)
            }
            .getOrElse[JsResult[e.Value]](JsError(s"Invalid enumeration value $value"))
      )
    }
  }

  implicit def paramValueToJsValue(paramValue: Parameter.Value): JsValue = {
    paramValue match {
      case ParamValue(v) => v
      case _ => JsNull
    }
  }

  implicit lazy val parameterValueFormat: Format[Parameter.Value] = {
    Format[Parameter.Value](implicitly[Reads[JsValue]].map(Parameter.Value), Writes(paramValueToJsValue))
  }

  implicit lazy val languageFormat: Format[Language] = Format(
    Reads(
      json =>
        Languages
          .fromName(json.as[String])
          .fold[JsResult[Language]](JsError(s"Invalid language ${json.as[String]}"))(l => JsSuccess(l))
    ),
    Writes((v: Language) => Json.toJson(v.name))
  )

  implicit lazy val configurationOptionsKeyFormat: Format[Options.Key] =
    Json.format[Options.Key]
  implicit lazy val configurationOptionsFormat: Format[Map[Options.Key, Options.Value]] =
    Format[Map[Options.Key, Options.Value]](
      Reads { json: JsValue =>
        JsSuccess(json.asOpt[Map[String, JsValue]].fold(Map.empty[Options.Key, Options.Value]) {
          _.map {
            case (k, v) =>
              Options.Key(k) -> Options.Value(v)
          }
        })
      },
      Writes(
        m =>
          JsObject(m.collect {
            case (k, v: OptionValue) => k.value -> v.value
          })
      )
    )

  implicit lazy val resultLevelFormat: Format[Result.Level.Value] =
    Format(enumReads(Result.Level), enumWrites[Result.Level])
  implicit lazy val patternCategoryFormat: Format[Pattern.Category.Value] =
    Format(enumReads(Pattern.Category), enumWrites[Pattern.Category])

  implicit lazy val patternIdFormat: Format[Pattern.Id] =
    Format(Reads.StringReads.map(Pattern.Id), Writes((v: Pattern.Id) => Json.toJson(v.value)))

  implicit lazy val errorMessageFormat: Format[ErrorMessage] =
    Format(Reads.StringReads.map(ErrorMessage), Writes((v: ErrorMessage) => Json.toJson(v.value)))

  implicit lazy val resultMessageFormat: Format[Result.Message] =
    Format(Reads.StringReads.map(Result.Message), Writes((v: Result.Message) => Json.toJson(v.value)))

  implicit lazy val resultLineFormat: Format[Source.Line] =
    Format(Reads.IntReads.map(Source.Line), Writes((v: Source.Line) => Json.toJson(v.value)))

  implicit lazy val parameterNameFormat: Format[Parameter.Name] =
    Format(Reads.StringReads.map(Parameter.Name), Writes((v: Parameter.Name) => Json.toJson(v.value)))

  implicit lazy val toolVersionFormat: Format[Tool.Version] =
    Format(Reads.StringReads.map(Tool.Version), Writes((v: Tool.Version) => Json.toJson(v.value)))

  implicit lazy val toolNameFormat: Format[Tool.Name] =
    Format(Reads.StringReads.map(Tool.Name), Writes((v: Tool.Name) => Json.toJson(v.value)))

  implicit lazy val sourceFileFormat: Format[Source.File] =
    Format(Reads.StringReads.map(Source.File), Writes((v: Source.File) => Json.toJson(v.path)))

  implicit lazy val patternTitleFormat: Format[Pattern.Title] =
    Format(Reads.StringReads.map(Pattern.Title), Writes((v: Pattern.Title) => Json.toJson(v.value)))

  implicit lazy val patternDescriptionTextFormat: Format[Pattern.DescriptionText] =
    Format(Reads.StringReads.map(Pattern.DescriptionText), Writes((v: Pattern.DescriptionText) => Json.toJson(v.value)))

  implicit lazy val patternTimeToFixFormat: Format[Pattern.TimeToFix] =
    Format(Reads.IntReads.map(Pattern.TimeToFix), Writes((v: Pattern.TimeToFix) => Json.toJson(v.value)))

  implicit lazy val parameterDescriptionTextFormat: Format[Parameter.DescriptionText] =
    Format(Reads.StringReads.map(Parameter.DescriptionText),
           Writes((v: Parameter.DescriptionText) => Json.toJson(v.value)))

  implicit lazy val parameterSpecificationFormat: Format[Parameter.Specification] =
    Json.format[Parameter.Specification]
  implicit lazy val parameterDefinitionFormat: Format[Parameter.Definition] = Json.format[Parameter.Definition]
  implicit lazy val resultLocationFormat: Format[Result.Location] = Json.format[Result.Location]
  implicit lazy val resultLinesFormat: Format[Result.Lines] = Json.format[Result.Lines]
  implicit lazy val resultPositionFormat: Format[Result.Position] = Json.format[Result.Position]
  implicit lazy val resultPositionsFormat: Format[Result.Positions] = Json.format[Result.Positions]
  implicit lazy val patternDefinitionFormat: Format[Pattern.Definition] = Json.format[Pattern.Definition]
  implicit lazy val parameterDescriptionFormat: Format[Parameter.Description] = Json.format[Parameter.Description]
  implicit lazy val patternDescriptionFormat: Format[Pattern.Description] = Json.format[Pattern.Description]
  implicit lazy val patternSpecificationFormat: Format[Pattern.Specification] = Json.format[Pattern.Specification]
  implicit lazy val toolConfigurationFormat: Format[Tool.Configuration] = Json.format[Tool.Configuration]
  implicit lazy val specificationFormat: Format[Tool.Specification] = Json.format[Tool.Specification]
  implicit lazy val toolCodacyconfigurationFormat: Format[Tool.CodacyConfiguration] =
    Json.format[Tool.CodacyConfiguration]

  implicit lazy val resultWrites: Writes[Result] = Writes[Result]((_: Result) match {
    case r: Result.Issue => Json.writes[Result.Issue].writes(r)
    case e: Result.FileError => Json.writes[Result.FileError].writes(e)
    case e: Result.ExtendedIssue => Json.writes[Result.ExtendedIssue].writes(e)
  })

  implicit lazy val resultReads: Reads[Result] = {
    //check issue then error then oldResult
    issueFormat.map(identity[Result]).orElse(errorFormat.map(identity[Result])).orElse(oldResultReads)
  }

  //old formats still out there...
  implicit private[this] lazy val errorFormat: Format[Result.FileError] = Json.format[Result.FileError]
  implicit private[this] lazy val issueFormat: Format[Result.Issue] = Json.format[Result.Issue]

  sealed private[this] trait ToolResult

  private[this] case class Issue(filename: String, message: String, patternId: String, line: Int) extends ToolResult

  private[this] case class FileError(filename: String, message: Option[String]) extends ToolResult

  private[this] lazy val oldResultReads: Reads[Result] = {

    lazy val IssueReadsName = Issue.getClass.getSimpleName
    lazy val issueReads = Json.reads[Issue].map(identity[ToolResult])

    lazy val ErrorReadsName = FileError.getClass.getSimpleName
    lazy val errorReads = Json.reads[FileError].map(identity[ToolResult])

    Reads { result: JsValue =>
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
