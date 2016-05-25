import play.api.libs.json.Reads.{IntReads, StringReads}
import play.api.libs.json._

package plugins {

import codacy.plugins.docker.{CategoryType, ResultLevel}

/*value classes to make things typesafe*/
class DockerImageName(val value: String) extends AnyVal {
  override def toString = value.toString
}

object DockerImageName {
  def apply(value: String): DockerImageName = new DockerImageName(value)
}

class ParamName(val value: String) extends AnyVal {
  override def toString = value.toString
}

class PatternId(val value: String) extends AnyVal {
  override def toString = value.toString
}

class ToolName(val value: String) extends AnyVal {
  override def toString = value.toString
}

class SourcePath(val value: String) extends AnyVal {
  override def toString = value.toString
}

class ResultMessage(val value: String) extends AnyVal {
  override def toString = value.toString
}

class ResultLine(val value: Int) extends AnyVal {
  override def toString = value.toString
}

final class ErrorMessage(val value: String) extends AnyVal {
  override def toString = value.toString
}

/*objects for the value classes so we don't have to do new... all the time - looks like a case class now*/
object ParamName {
  def apply(value: String): ParamName = new ParamName(value)
}

object PatternId {
  def apply(value: String): PatternId = new PatternId(value)
}

object ToolName {
  def apply(value: String): ToolName = new ToolName(value)
}

object SourcePath {
  def apply(value: String): SourcePath = new SourcePath(value)
}

object ResultMessage {
  def apply(value: String): ResultMessage = new ResultMessage(value)
}

object ResultLine {
  def apply(value: Int): ResultLine = new ResultLine(value)
}

object ErrorMessage {
  def apply(value: String): ErrorMessage = new ErrorMessage(value)
}

case class Param(name: ParamName, value: JsValue)

case class PatternWithParam(patternId: PatternId, parameters: Option[Seq[Param]])

case class ToolConfig(name: ToolName, patterns: Seq[PatternWithParam])

case class FullConfig(tools: Set[ToolConfig], files: Option[Set[SourcePath]])

sealed trait ToolResult

final case class Issue(filename: SourcePath, message: ResultMessage, patternId: PatternId, line: ResultLine) extends ToolResult

final case class FileError(filename: SourcePath, message: Option[ErrorMessage]) extends ToolResult

case class ParameterSpec(name: ParamName, default: JsValue)

case class PatternSpec(patternId: PatternId, category: CategoryType.Value, level: ResultLevel.Value, parameters: Option[Set[ParameterSpec]])

case class ToolSpec(name: ToolName, patterns: Set[PatternSpec])

/*
 * Description
 */

// TODO: timeToFix needs to be added to jshint patterns
case class PatternDescription(patternId: PatternId, title: String, description: Option[String],
                              parameters: Option[Set[ParameterDescription]],
                              private val timeToFix: Option[Int],
                              private val explanationOpt: Option[String]) {
  val fixTime = timeToFix.getOrElse(5)
  val explanation = explanationOpt.getOrElse("")
}

case class ParameterDescription(name: ParamName, description: String)

}

package object plugins {

  import scala.language.implicitConversions

  //implicits for the value classes still didn't implement macros for this...
  implicit def toValue0(obj: ParamName): String = obj.value

  implicit def toValue1(obj: PatternId): String = obj.value

  implicit def toValue2(obj: ToolName): String = obj.value

  implicit def toValue3(obj: SourcePath): String = obj.value

  implicit def toValue4(obj: ResultMessage): String = obj.value

  implicit def toValue5(obj: ResultLine): Int = obj.value

  implicit def toValue7(obj: DockerImageName): String = obj.value

  private[this] implicit val fmt0 = Format(StringReads.map(ParamName.apply), Writes((obj: ParamName) => Json.toJson(obj.value)))
  private[this] implicit val fmt1 = Format(StringReads.map(PatternId.apply), Writes((obj: PatternId) => Json.toJson(obj.value)))
  private[this] implicit val fmt2 = Format(StringReads.map(ToolName.apply), Writes((obj: ToolName) => Json.toJson(obj.value)))
  private[this] implicit val fmt3 = Format(StringReads.map(SourcePath.apply), Writes((obj: SourcePath) => Json.toJson(obj.value)))
  private[this] implicit val fmt4 = Format(StringReads.map(ResultMessage.apply), Writes((obj: ResultMessage) => Json.toJson(obj.value)))
  private[this] implicit val fmt5 = Format(IntReads.map(ResultLine.apply), Writes((obj: ResultLine) => Json.toJson(obj.value)))
  private[this] implicit val fmt6 = Format(StringReads.map(ErrorMessage.apply), Writes((obj: ErrorMessage) => Json.toJson(obj.value)))

  implicit lazy val readsToolResult: Reads[ToolResult] = {

    lazy val IssueReadsName = Issue.getClass.getSimpleName
    lazy val issueReads = Json.reads[Issue]

    lazy val ErrorReadsName = FileError.getClass.getSimpleName
    lazy val errorReads = Json.reads[FileError]

    Reads[ToolResult] { (result: JsValue) =>
      (result \ "type").validate[String].flatMap {
        case IssueReadsName => issueReads.reads(result)
        case ErrorReadsName => errorReads.reads(result)
        case tpe => JsError(s"not a valid result type $tpe")
      }.orElse(issueReads.reads(result))
    }

  }

  implicit val writesToolConfig: Writes[FullConfig] = {
    implicit val w2 = Json.writes[Param]
    implicit val w1 = Json.writes[PatternWithParam]
    implicit val w0 = Json.writes[ToolConfig]
    Json.writes[FullConfig]
  }

  implicit val readsToolSpec: Format[ToolSpec] = {
    implicit val paramFmt = Json.format[ParameterSpec]
    implicit val patFmt = Json.format[PatternSpec]
    Json.format[ToolSpec]
  }

  implicit val readsToolDesc: Format[PatternDescription] = {
    implicit val paramFmt = Json.format[ParameterDescription]
    Json.format[PatternDescription]
  }

}
