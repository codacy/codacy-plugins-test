package codacy.plugins.test

import java.io.File

import codacy.utils.{FileHelper, Printer}
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.api.results.Result
import play.api.libs.json.{JsValue, Json}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

case class PatternTestFile(file: File,
                           language: Language,
                           enabledPatterns: Seq[PatternSimple],
                           matches: Seq[TestFileResult])

case class IssueWithLine(severity: String, line: Int, patternId: String)

object IssueWithLine {
  implicit val formatter = Json.format[IssueWithLine]
}

case class PatternSimple(name: String, parameters: Option[Map[String, JsValue]])

class TestFilesParser(filesDir: File) {

  val Warning = """\s*#Warn(?:ing)?:\s*([A-Za-z0-9\_\-\.=/]+).*""".r
  val Error = """\s*#Err(?:or)?:\s*([A-Za-z0-9\_\-\.=/]+).*""".r
  val Info = """\s*#Info:\s*([A-Za-z0-9\_\-\.=/]+).*""".r
  val PatternsList = """\s*#Patterns:\s*([\s\,A-Za-z0-9\_\-\.=/]+)""".r

  val PatternWithParameters =
    """\s*#Patterns:\s*([A-Za-z0-9\,\_\-\.=/]+)[\s\:]+(.*)""".r
  val IssueWithLineRegex = """\s*#Issue:\s*(.*)""".r

  val languages = Map[Language, Seq[String]](Languages.Javascript -> Seq("//", "/*"),
                                             Languages.Scala -> Seq("/*", "//"),
                                             Languages.CSS -> Seq("/*"),
                                             Languages.LESS -> Seq("/*"),
                                             Languages.SASS -> Seq("/*"),
                                             Languages.PHP -> Seq("#", "//"),
                                             Languages.C -> Seq("/*", "//"),
                                             Languages.CPP -> Seq("/*", "//"),
                                             Languages.ObjectiveC -> Seq("/*", "//"),
                                             Languages.Python -> Seq("#"),
                                             Languages.Ruby -> Seq("#"),
                                             Languages.Kotlin -> Seq("//", "/*"),
                                             Languages.Perl -> Seq("#"),
                                             Languages.Java -> Seq("//", "/*"),
                                             Languages.CSharp -> Seq("//", "/*"),
                                             Languages.VisualBasic -> Seq("'"),
                                             Languages.Go -> Seq("//"),
                                             Languages.Elixir -> Seq("#"),
                                             Languages.Clojure -> Seq("#", ";;"),
                                             Languages.CoffeeScript -> Seq("#"),
                                             Languages.Rust -> Seq("//"),
                                             Languages.Swift -> Seq("//"),
                                             Languages.Haskell -> Seq("--"),
                                             Languages.Shell -> Seq("#"),
                                             Languages.TypeScript -> Seq("//", "/*"),
                                             Languages.XML -> Seq("<!--"),
                                             Languages.Dockerfile -> Seq("#"),
                                             Languages.PLSQL -> Seq("--", "/*"),
                                             Languages.SQL -> Seq("--", "/*"),
                                             Languages.JSON -> Seq("//", "/*"),
                                             Languages.Apex -> Seq("//", "/*"),
                                             Languages.Velocity -> Seq("/*"),
                                             Languages.JSP -> Seq("<%--"),
                                             Languages.VisualForce -> Seq("<!--"),
                                             Languages.R -> Seq("#"),
                                             Languages.Powershell -> Seq("#", "<#"),
                                             Languages.Solidity -> Seq("//", "/*"),
                                             Languages.Markdown -> Seq("<!--"),
                                             Languages.Crystal -> Seq("#"),
                                             Languages.YAML -> Seq("#"))

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
                      Printer.red(s"${file.getName}: Failing to parse Issue $value")
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

  private def getAllComments(file: File, language: Language): Seq[(Int, String)] = {
    FileHelper.read(file).getOrElse(Seq.empty).zipWithIndex.flatMap {
      case (line, lineNr) =>
        getComment(language, line).map { comment =>
          (lineNr + 1, comment)
        }
    }
  }

  //Returns the content of a line comment or None if the line is not a comment
  private def getComment(language: Language, line: String): Option[String] = {
    languages(language).collectFirst {
      case lineComment if line.trim.startsWith(lineComment) && line.trim.endsWith(lineComment.reverse) =>
        line.trim.drop(lineComment.length).dropRight(lineComment.length)
      case lineComment if line.trim.startsWith(lineComment) =>
        line.trim.drop(lineComment.length)
    }
  }

  //The match is in the next line that is not a comment
  @tailrec
  private def getNextCodeLine(currentLine: Int, comments: Seq[Int]): Int = {
    if (!comments.contains(currentLine)) {
      currentLine
    } else {
      getNextCodeLine(currentLine + 1, comments)
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
