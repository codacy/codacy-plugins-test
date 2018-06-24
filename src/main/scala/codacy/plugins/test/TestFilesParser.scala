package codacy.plugins.test

import java.io.File

import codacy.plugins.docker.Language
import codacy.utils.{FileHelper, Printer}
import com.codacy.plugins.api.results.Result
import play.api.libs.json.{JsValue, Json, OFormat}

import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

final case class PatternTestFile(file: File,
                                 language: Language.Value,
                                 enabledPatterns: Seq[PatternSimple],
                                 matches: Seq[TestFileResult])

final case class IssueWithLine(severity: String, line: Int, patternId: String)

object IssueWithLine {
  implicit val formatter: OFormat[IssueWithLine] = Json.format[IssueWithLine]
}

final case class PatternSimple(name: String, parameters: Option[Map[String, JsValue]])

class TestFilesParser(filesDir: File) {

  val Warning: Regex = """\s*#Warn(?:ing)?:\s*([A-Za-z0-9\_\-\.=/]+).*""".r
  val Error: Regex = """\s*#Err(?:or)?:\s*([A-Za-z0-9\_\-\.=/]+).*""".r
  val Info: Regex = """\s*#Info:\s*([A-Za-z0-9\_\-\.=/]+).*""".r
  val PatternsList: Regex = """\s*#Patterns:\s*([\s\,A-Za-z0-9\_\-\.=/]+)""".r
  val PatternWithParameters: Regex = """\s*#Patterns:\s*([A-Za-z0-9\,\_\-\.=/]+)[\s\:]+(.*)""".r
  val IssueWithLineRegex: Regex = """\s*#Issue:\s*(.*)""".r

  val languages: Map[Language.Value, Seq[String]] = Map[Language.Value, Seq[String]](
    Language.Javascript -> Seq("//", "/*"),
    Language.Scala -> Seq("/*", "//"),
    Language.CSS -> Seq("/*"),
    Language.PHP -> Seq("#", "//"),
    Language.C -> Seq("/*", "//"),
    Language.CPP -> Seq("/*", "//"),
    Language.ObjectiveC -> Seq("/*", "//"),
    Language.Python -> Seq("#"),
    Language.Ruby -> Seq("#"),
    Language.Kotlin -> Seq("//", "/*"),
    Language.Perl -> Seq("#"),
    Language.Java -> Seq("//", "/*"),
    Language.CSharp -> Seq("//", "/*"),
    Language.VisualBasic -> Seq("'"),
    Language.Go -> Seq("//"),
    Language.Elixir -> Seq("#"),
    Language.Clojure -> Seq("#", ";;"),
    Language.CoffeeScript -> Seq("#"),
    Language.Rust -> Seq("//"),
    Language.Swift -> Seq("//"),
    Language.Haskell -> Seq("--"),
    Language.React -> Seq("//", "/*"),
    Language.Shell -> Seq("#"),
    Language.TypeScript -> Seq("//", "/*"),
    Language.Jade -> Seq("//", "//-"),
    Language.Stylus -> Seq("//"),
    Language.XML -> Seq("<!--"),
    Language.Dockerfile -> Seq("#"),
    Language.PLSQL -> Seq("--", "/*"),
    Language.JSON -> Seq("//", "/*"),
    Language.Apex -> Seq("//", "/*"),
    Language.Velocity -> Seq("/*"),
    Language.JSP -> Seq("<%--"),
    Language.Visualforce -> Seq("<!--"),
    Language.R -> Seq("#")
  )

  def getTestFiles: Seq[PatternTestFile] = {

    val validExtensions = Language.values.flatMap(Language.getExtensions).toArray
    val files = FileHelper.listFiles(filesDir).filter(f => validExtensions.contains(s".${f.getName.split('.').last}"))

    files
      .map { file =>
        val extension = "." + file.getName.split('.').last

        val languageMap = Language.values.toList.map(lang => (lang, Language.getExtensions(lang)))

        languageMap.collect {
          case (language, extensions) if extensions.contains(extension) =>
            val comments = getAllComments(file, language)
            val commentLines = comments.map { case (commentFile, _) => commentFile }

            val matches = comments.flatMap {
              case (line, comment) =>
                val nextLine = getNextCodeLine(line, commentLines)

                comment.trim match {
                  case IssueWithLineRegex(value) =>
                    Try {
                      for {
                        IssueWithLine(severityStr, line, patternId) <- Json.parse(value).asOpt[IssueWithLine]
                        severity <- Result.Level.values.find(_.toString.startsWith(severityStr))
                      } yield TestFileResult(patternId, line, severity)
                    } match {
                      case Success(result) => result
                      case Failure(_) =>
                        Printer.red(s"${file.getName}: Failing to parse Issue $value")
                        System.exit(2)
                        None
                    }
                  case Warning(value) => Some(TestFileResult(value, nextLine, Result.Level.Warn))
                  case Error(value) => Some(TestFileResult(value, nextLine, Result.Level.Err))
                  case Info(value) => Some(TestFileResult(value, nextLine, Result.Level.Info))
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
                  val params = Try(cleanParameterTypes(Json.parse(parameters))).toOption
                    .flatMap(_.asOpt[Map[String, JsValue]])
                  Seq(PatternSimple(patternId, params))

                case _ => Seq.empty
              }

            PatternTestFile(file, language, enabledPatterns, matches)
        }.toSeq
      }
      .toSeq
      .flatten
  }

  private def getAllComments(file: File, language: Language.Value): Seq[(Int, String)] = {
    FileHelper.read(file).getOrElse(Seq.empty).zipWithIndex.flatMap {
      case (line, lineNr) =>
        getComment(language, line).map { comment =>
          (lineNr + 1, comment)
        }
    }
  }

  //Returns the content of a line comment or None if the line is not a comment
  private def getComment(language: Language.Value, line: String): Option[String] = {
    languages(language).foreach { lineComment =>
      if (line.trim.startsWith(lineComment)) {
        if (line.trim.endsWith(lineComment.reverse)) {
          return Some(line.trim.drop(lineComment.length).dropRight(lineComment.length))
        }

        return Some(line.trim.drop(lineComment.length))
      }
    }
    None
  }

  //The match is in the next line that is not a comment
  private def getNextCodeLine(currentLine: Int, comments: Seq[Int]): Int = {
    if (comments.contains(currentLine)) {
      return getNextCodeLine(currentLine + 1, comments)
    }
    currentLine
  }

  private def cleanParameterTypes(json: JsValue): JsValue = {
    val jsonString = json.toString()
    val fixedString = jsonString.replaceAll(""""(true|false)"""", "$1").replaceAll(""""([0-9]+)"""", "$1")
    Json.parse(fixedString)
  }
}
