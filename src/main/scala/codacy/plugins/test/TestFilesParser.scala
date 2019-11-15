package codacy.plugins.test

import java.io.File

import codacy.utils.{FileHelper}
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.api.results.Result
import com.codacy.analysis.core
import play.api.libs.json.{JsValue, Json}

import scala.util.{Failure, Success, Try}

import Utils._
import wvlet.log.LogSupport

case class PatternTestFile(file: File,
                           language: Language,
                           enabledPatterns: Seq[PatternSimple],
                           matches: Seq[TestFileResult])

case class IssueWithLine(severity: String, line: Int, patternId: String)

object IssueWithLine {
  implicit val formatter = Json.format[IssueWithLine]
}

case class PatternSimple(name: String, parameters: Option[Map[String, JsValue]]) {

  def toCoreModelPattern: core.model.Pattern = {
    val newParameters: Set[core.model.Parameter] = parameters match {
      case None => Set.empty
      case Some(map) =>
        map.map {
          case (k, v) => core.model.Parameter(k, v.toString)
        }(collection.breakOut)
    }
    core.model.Pattern(name, newParameters)
  }
}

class TestFilesParser(filesDir: File) extends LogSupport {

  val Warning = """\s*#Warn(?:ing)?:\s*([A-Za-z0-9\_\-\.=/]+).*""".r
  val Error = """\s*#Err(?:or)?:\s*([A-Za-z0-9\_\-\.=/]+).*""".r
  val Info = """\s*#Info:\s*([A-Za-z0-9\_\-\.=/]+).*""".r
  val PatternsList = """\s*#Patterns:\s*([\s\,A-Za-z0-9\_\-\.=/]+)""".r

  val PatternWithParameters =
    """\s*#Patterns:\s*([A-Za-z0-9\,\_\-\.=/]+)[\s\:]+(.*)""".r
  val IssueWithLineRegex = """\s*#Issue:\s*(.*)""".r

  def getTestFiles: Seq[PatternTestFile] = {
    FileHelper
      .listFiles(filesDir)
      .map { file =>
        (file, Languages.forPath(file.getAbsolutePath))
      }
      .collect { case (file, Some(language)) => (file, language) }
      .map {
        case (file, language) =>
          val comments = getAllComments(file, language)
          val commentLines = comments.map {
            case (commentFile, _) => commentFile
          }

          val matches = comments.flatMap {
            case (line, comment) =>
              val nextLine = getNextCodeLine(line, commentLines)

              comment.trim match {
                case IssueWithLineRegex(value) =>
                  Try {
                    for {
                      IssueWithLine(severityStr, line, patternId) <- Json
                        .parse(value)
                        .asOpt[IssueWithLine]
                      severity <- Result.Level.values
                        .find(_.toString.startsWith(severityStr))
                    } yield TestFileResult(patternId, line, severity)
                  } match {
                    case Success(result) => result
                    case Failure(_) =>
                      error(s"${file.getName}: Failing to parse Issue $value")
                      System.exit(2)
                      None
                  }
                case Warning(value) =>
                  Some(TestFileResult(value, nextLine, Result.Level.Warn))
                case Error(value) =>
                  Some(TestFileResult(value, nextLine, Result.Level.Err))
                case Info(value) =>
                  Some(TestFileResult(value, nextLine, Result.Level.Info))
                case _ => None
              }
          }

          //we probably need to convert this into a smarter regex
          val enabledPatterns = comments
            .map { case (_, comment) => comment }
            .flatMap {
              //pattern has no parameters
              case PatternsList(value) =>
                value.split(",").map { pattern =>
                  PatternSimple(pattern.trim, None)
                }

              case PatternWithParameters(patternIdString, parameters) =>
                val patternId = patternIdString.trim
                val params =
                  Try(cleanParameterTypes(Json.parse(parameters))).toOption
                    .flatMap(_.asOpt[Map[String, JsValue]])
                Seq(PatternSimple(patternId, params))

              case _ => Seq.empty
            }

          PatternTestFile(file, language, enabledPatterns, matches)
      }
  }

  private def cleanParameterTypes(json: JsValue): JsValue = {
    val jsonString = json.toString()
    val fixedString = jsonString
      .replaceAll(""""(true|false)"""", "$1")
      .replaceAll(""""([0-9]+)"""", "$1")
    Json.parse(fixedString)
  }
}
