package codacy.plugins.test

import java.io.File

import codacy.utils.{FileHelper, Printer}
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.api.metrics.FileMetrics
import play.api.libs.json.{JsValue, Json}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import Utils._

// object ComplexityWithLine {
//   implicit val formatter = Json.format[ComplexityWithLine]
// }

class MetricsTestFilesParser(filesDir: File) {
  private case class MetricsHeaderData(complexity: Option[Int],
                                       loc: Option[Int],
                                       cloc: Option[Int],
                                       nrMethods: Option[Int],
                                       nrClasses: Option[Int])
  private implicit val formatter = Json.format[MetricsHeaderData]
  

  val MetricsHeader = """\s*#Metrics:\s*(.*)""".r
  val LineComplexity = """\s*#LineComplexity:\s*(.*)""".r

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

          comments.headOption
            .map { case (_, comment) => comment }
            .map {
              //pattern has no parameters
              case MetricsHeader(json) =>
                val metricsHeaderData = MetricsHeaderData(complexity, loc, cloc, nrMethods, nrClasses) <- Json
                
                FileMetrics(file.getAbsolutePath,
                            metricsHeaderData.complexity,
                            metricsHeaderData.loc,
                            metricsHeaderData.cloc,
                            metricsHeaderData.nrMethods,
                            metricsHeaderData.nrClasses,
                            ???)
              case _ => throw new Exception(s"File $file has no metrics header.")
            }
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
    languageComments(language).collectFirst {
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
